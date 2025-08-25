package com.smartcardpoc

import android.content.Context
import com.abc.terminalfactory.UsbSmartCard
import com.abc.terminalfactory.AbCircleCardTerminalUSB
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import javax.smartcardio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList

class SmartCardModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var currentTerminal: CardTerminal? = null
    private var card: Card? = null
    private var channel: CardChannel? = null
    private var terminalsThread: Thread? = null
    private var cardMonitorThread: Thread? = null

    init {
        // Delay thread start until explicitly requested
        android.util.Log.e("SmartCardModule", "Init complete")
    }

    override fun getName(): String = "SmartCardModule"

    private fun startAutoRefreshThread() {
        if (terminalsThread?.isAlive == true) return
        terminalsThread = Thread {
            var oldTerminals: List<CardTerminal>? = null
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val terminals = UsbSmartCard.terminals()
                    val currentTerminals = terminals.list()
                    if (oldTerminals == null || !oldTerminals.equals(currentTerminals)) {
                        oldTerminals = currentTerminals
                        sendEvent("TerminalsUpdated", null)
                    }
                    Thread.sleep(500)
                } catch (e: Exception) {
                    android.util.Log.e("SmartCardModule", "AutoRefresh error: ${e.message}")
                }
            }
        }
        terminalsThread?.start()
    }

    @ReactMethod
    fun listTerminals(promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                startAutoRefreshThread()
                val terminals = UsbSmartCard.terminals().list()
                val names = Arguments.createArray()
                for (terminal in terminals) {
                    names.pushString(terminal.name)
                }
                promise.resolve(names)
            } catch (e: CardException) {
                promise.reject("LIST_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun selectTerminal(name: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val terminals = UsbSmartCard.terminals().list()
                currentTerminal = terminals.find { it.name == name }
                if (currentTerminal == null) {
                    promise.reject("NO_TERMINAL", "Terminal not found: $name")
                } else {
                    promise.resolve(true)
                }
            } catch (e: CardException) {
                promise.reject("SELECT_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun connect(protocol: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (currentTerminal == null) {
                    promise.reject("NO_TERMINAL", "No terminal selected")
                    return@launch
                }
                card = currentTerminal!!.connect(protocol)
                channel = card!!.basicChannel
                promise.resolve(Util.bytesToHexString(card!!.atr.bytes))
            } catch (e: CardException) {
                card = null
                channel = null
                promise.reject("CONNECT_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun disconnect(promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                card?.disconnect(true)
                card = null
                channel = null
                stopMonitorCardInternally()
                promise.resolve(true)
            } catch (e: CardException) {
                promise.reject("DISCONNECT_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun transmitAPDU(commandStr: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (channel == null) {
                    promise.reject("NO_CHANNEL", "Not connected")
                    return@launch
                }
                val command = CommandAPDU(Util.stringToBytes(commandStr))
                val response = channel!!.transmit(command)
                promise.resolve(Util.bytesToHexString(response.bytes))
            } catch (e: CardException) {
                disconnectInternally()
                promise.reject("APDU_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun transmitControl(commandStr: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (card == null) {
                    promise.reject("NO_CARD", "Not connected")
                    return@launch
                }
                val command = Util.stringToBytes(commandStr)
                val response = card!!.transmitControlCommand(3500, command)
                promise.resolve(Util.bytesToHexString(response))
            } catch (e: CardException) {
                disconnectInternally()
                promise.reject("CONTROL_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun getFirmware(promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (currentTerminal == null) {
                    promise.reject("NO_TERMINAL", "No terminal selected")
                    return@launch
                }
                if (currentTerminal !is AbCircleCardTerminalUSB) {
                    promise.reject("INVALID_TERMINAL", "Not an AB Circle USB terminal")
                    return@launch
                }
                val version = (currentTerminal as AbCircleCardTerminalUSB).firmwareVersion
                promise.resolve(version)
            } catch (e: Exception) {
                promise.reject("FIRMWARE_ERROR", e.message)
            }
        }
    }

    @ReactMethod
    fun startMonitorCard(promise: Promise) {
        if (cardMonitorThread?.isAlive == true) {
            promise.resolve(false)
            return
        }
        cardMonitorThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val terminals = UsbSmartCard.terminals()
                    terminals.waitForChange(1000)
                    val inserted = terminals.list(CardTerminals.State.CARD_INSERTION)
                    for (terminal in inserted) {
                        val cardTemp = terminal.connect("*")
                        val atr = Util.bytesToHexString(cardTemp.atr.bytes)
                        cardTemp.disconnect(true)
                        val params = Arguments.createMap()
                        params.putString("type", "inserted")
                        params.putString("terminal", terminal.name)
                        params.putString("atr", atr)
                        sendEvent("CardEvent", params)
                    }
                    val removed = terminals.list(CardTerminals.State.CARD_REMOVAL)
                    for (terminal in removed) {
                        val params = Arguments.createMap()
                        params.putString("type", "removed")
                        params.putString("terminal", terminal.name)
                        sendEvent("CardEvent", params)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SmartCardModule", "Monitor error: ${e.message}")
                }
            }
        }
        cardMonitorThread?.start()
        promise.resolve(true)
    }

    @ReactMethod
    fun stopMonitorCard(promise: Promise) {
        stopMonitorCardInternally()
        promise.resolve(true)
    }

    private fun stopMonitorCardInternally() {
        cardMonitorThread?.interrupt()
        cardMonitorThread = null
    }

    private fun disconnectInternally() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                card?.disconnect(true)
                card = null
                channel = null
                stopMonitorCardInternally()
            } catch (e: CardException) {
                android.util.Log.e("SmartCardModule", "Internal disconnect error: ${e.message}")
            }
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit(eventName, params)
        } catch (e: Exception) {
            android.util.Log.e("SmartCardModule", "Event emission failed: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        terminalsThread?.interrupt()
        cardMonitorThread?.interrupt()
        try {
            card?.disconnect(true)
        } catch (e: Exception) {
            android.util.Log.e("SmartCardModule", "Cleanup error: ${e.message}")
        }
        terminalsThread = null
        cardMonitorThread = null
    }
}