package com.smartcardpoc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import javax.smartcardio.*

class SmartCardModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val reactContext: ReactApplicationContext = reactContext

    override fun getName(): String {
        return "SmartCardModule"
    }

    @ReactMethod
    fun readCard(protocol: String, promise: Promise) {
        try {
            val factory = TerminalFactory.getDefault()
            val terminals = factory.terminals()
            val terminalList = terminals.list()
            if (terminalList.isEmpty()) {
                promise.reject("NO_TERMINALS", "No card terminals found")
                return
            }
            val terminal = terminalList[0]
            if (!terminal.isCardPresent) {
                promise.reject("NO_CARD", "No card present in the terminal")
                return
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

    @ReactMethod
    fun requestUsbPermission(deviceName: String, promise: Promise) {
        val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.find { it.deviceName == deviceName }
        if (device == null) {
            promise.reject("NO_DEVICE", "USB device not found")
            return
        }
        usbManager.requestPermission(device, android.app.PendingIntent.getBroadcast(reactContext, 0, android.content.Intent("com.smartcardpoc.USB_PERMISSION"), 0))
        promise.resolve("Permission requested")
    }

    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02X", it) }
    }
}