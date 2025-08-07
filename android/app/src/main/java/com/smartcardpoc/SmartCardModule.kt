package com.smartcardpoc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import javax.smartcardio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartCardModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val reactContext: ReactApplicationContext = reactContext

    override fun getName(): String {
        return "SmartCardModule"
    }

    @ReactMethod
    fun readCard(protocol: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val factory = TerminalFactory.getDefault()
                val terminals = factory.terminals()
                val terminalList = terminals.list()
                if (terminalList.isEmpty()) {
                    promise.reject("NO_TERMINALS", "No card terminals found")
                    return@launch
                }
                val terminal = terminalList[0]
                if (!terminal.isCardPresent) {
                    promise.reject("NO_CARD", "No card present in the terminal")
                    return@launch
                }
                val card = terminal.connect(protocol)
                val channel = card.basicChannel
                val selectCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x00)
                val command = CommandAPDU(selectCommand)
                val response = channel.transmit(command)
                promise.resolve(byteArrayToHex(response.bytes))
                card.disconnect(true)
            } catch (e: Exception) {
                promise.reject("CARD_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun requestUsbPermission(deviceName: String, promise: Promise) {
        val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.find { it.deviceName == deviceName }
        if (device == null) {
            promise.reject("NO_DEVICE", "USB device not found")
            return
        }
        val intent = Intent("com.smartcardpoc.USB_PERMISSION")
        val pendingIntent = PendingIntent.getBroadcast(reactContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, pendingIntent)
        promise.resolve("Permission requested")
    }

    @ReactMethod
    fun listUsbDevices(promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList
                val devices: WritableArray = Arguments.createArray()

                for (device in deviceList.values) {
                    for (i in 0 until device.interfaceCount) {
                        if (device.getInterface(i).interfaceClass == 0x0B) {
                            val deviceInfo: WritableMap = Arguments.createMap()
                            deviceInfo.putString("deviceName", device.deviceName)
                            deviceInfo.putInt("vendorId", device.vendorId)
                            deviceInfo.putInt("productId", device.productId)
                            devices.pushMap(deviceInfo)
                            break
                        }
                    }
                }

                if (devices.size() == 0) {
                    promise.reject("NO_DEVICES", "No smart card readers found")
                    return@launch
                }
                promise.resolve(devices)
            } catch (e: Exception) {
                promise.reject("LIST_ERROR", "Failed to list USB devices: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun debugModule(promise: Promise) {
        promise.resolve("SmartCardModule is loaded")
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02X", it) }
    }
}