package com.smartcardpoc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

class UsbModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val usbManager: UsbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private lateinit var usbReceiver: BroadcastReceiver

    init {
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                if (USB_PERMISSION_ACTION == action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val params = Arguments.createMap()
                    params.putBoolean("granted", granted)
                    params.putString("deviceName", device?.deviceName ?: "unknown")
                    sendEvent("UsbPermissionResult", params)
                    Log.d(TAG, "USB Permission: ${if (granted) "Granted" else "Denied"}")
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                    requestUsbPermission(device)
                    sendEvent("UsbDeviceAttached", null)
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                    sendEvent("UsbDeviceDetached", null)
                }
            }
        }

        val filter = IntentFilter(USB_PERMISSION_ACTION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Context.RECEIVER_NOT_EXPORTED else 0
        reactContext.registerReceiver(usbReceiver, filter, flags)
    }

    override fun getName(): String = "UsbModule"

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun hasUsbHost(): Boolean {
        return reactApplicationContext.packageManager.hasSystemFeature("android.hardware.usb.host") &&
               usbManager != null
    }

    @ReactMethod
    fun checkAndRequestPermission() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            Log.d(TAG, "No USB devices found")
            return
        }
        for (device in deviceList.values) {
            if (device.vendorId == 0x31aa) {  // AB Circle VID
                requestUsbPermission(device)
            }
        }
    }

    private fun requestUsbPermission(device: UsbDevice?) {
        if (device == null) return

        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Already has permission for device: ${device.deviceName}")
            val params = Arguments.createMap()
            params.putBoolean("granted", true)
            params.putString("deviceName", device.deviceName)
            sendEvent("UsbPermissionResult", params)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                reactApplicationContext,
                0,
                Intent(USB_PERMISSION_ACTION),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
            Log.d(TAG, "Requesting USB permission for device: ${device.deviceName}")
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    override fun onCatalystInstanceDestroy() {
        reactApplicationContext.unregisterReceiver(usbReceiver)
    }

    companion object {
        private const val TAG = "UsbModule"
        private const val USB_PERMISSION_ACTION = "com.smartcardpoc.USB_PERMISSION"
    }
}