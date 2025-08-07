Smart Card Integration in React Native
This README provides a comprehensive guide to integrating the javax.smartcardio SDK into a React Native CLI project. The goal is to enable the app to communicate with USB smart card readers dynamically, supporting all compatible devices (e.g., CIR115, CIR125, CIR135, CIR215, CIR315, CIR415, CIR515, CIR615) using the AB Circle Smart Card I/O Android Library.

Prerequisites
Before you begin, ensure you have the following tools and dependencies installed:

Node.js: Version 16 or higher
JDK: Version 11 or higher
Android SDK: Configured via Android Studio or command line
React Native CLI: Install globally with npm install -g @react-native-community/cli
Physical Android Device: Must support USB Host mode and have a compatible smart card reader attached
JAR Files: smartcardio-x.y.z.jar and abcTerminalFactory-x.y.z.jar (replace x.y.z with the actual version from the AB Circle SDK distribution)


Step 1: Create a New React Native Project
Start by creating a new React Native project using the CLI:
npx @react-native-community/cli init SmartCardApp
cd SmartCardApp

This command initializes a project named SmartCardApp with the default structure.

Step 2: Add JAR Files to the Project
The AB Circle Smart Card I/O Android Library relies on external JAR files since javax.smartcardio is not natively available in Android's runtime.

Obtain the JAR Files:

Download smartcardio-x.y.z.jar and abcTerminalFactory-x.y.z.jar from the AB Circle SDK provider.


Place JAR Files:

Navigate to android/app/.
Create a libs folder if it doesn’t exist: mkdir libs.
Copy both JAR files into android/app/libs/.


Update build.gradle:

Open android/app/build.gradle and add the following to the dependencies block:dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    // Existing dependencies remain unchanged
}




Sync the Project:

Run the following commands to clean and sync:cd android
./gradlew clean
cd ..






Step 3: Configure Android Permissions
Modify the AndroidManifest.xml to include permissions and features for USB communication.

Open android/app/src/main/AndroidManifest.xml and update it as follows:
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.USB_PERMISSION" />
  <uses-feature android:name="android.hardware.usb.host" android:required="true" />
  <application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:icon="@mipmap/ic_launcher">
    <activity
      android:name=".MainActivity"
      android:label="@string/app_name"
      android:configChanges="keyboard|keyboardHidden|orientation|screenSize|uiMode"
      android:launchMode="singleTask">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
      <intent-filter>
        <action android:name="com.smartcardapp.USB_PERMISSION" />
      </intent-filter>
    </activity>
  </application>
</manifest>


Note: Replace com.smartcardapp with your actual package name if it differs (check android/app/src/main/java/com/ for your package).



Step 4: Create the Native Module
Create a Kotlin native module to interface with the smart card readers.

Create SmartCardModule.kt:

Navigate to android/app/src/main/java/com/smartcardapp/.

Create a file named SmartCardModule.kt with the following content:
package com.smartcardapp

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import android.app.PendingIntent
import android.content.Intent
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
        val intent = Intent("com.smartcardapp.USB_PERMISSION")
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




Create SmartCardPackage.kt:

In the same directory, create SmartCardPackage.kt:
package com.smartcardapp

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class SmartCardPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(SmartCardModule(reactContext))
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}




Update MainApplication.kt:

Open android/app/src/main/java/com/smartcardapp/MainApplication.kt and modify the getPackages() method:import com.smartcardapp.SmartCardPackage

// ...

override fun getPackages(): List<ReactPackage> {
    val packages = PackageList(this).packages
    packages.add(SmartCardPackage())
    return packages
}






Step 5: Update TypeScript Code
Modify the App.tsx file to interact with the native module.

Open SmartCardApp/App.tsx and replace its content with:
import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, TouchableOpacity, View, ActivityIndicator } from 'react-native';
import LinearGradient from 'react-native-linear-gradient';
import { NativeModules } from 'react-native';

const { SmartCardModule } = NativeModules;

interface CardData {
  raw: string;
}

interface UsbDevice {
  deviceName: string;
  vendorId: number;
  productId: number;
}

const App = () => {
  const [cardData, setCardData] = useState<CardData | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    console.log('SmartCardModule methods:', Object.keys(SmartCardModule));
    SmartCardModule.debugModule()
      .then((result: string) => console.log('Debug:', result))
      .catch((e: any) => console.error('Debug error:', e));
  }, []);

  const readCard = async () => {
    setIsLoading(true);
    setError('');
    try {
      let deviceName = 'AbCircleCardTerminalUSB';
      try {
        const devices: UsbDevice[] = await SmartCardModule.listUsbDevices();
        if (devices.length > 0) {
          deviceName = devices[0].deviceName;
          console.log('Detected device:', deviceName, devices[0].vendorId, devices[0].productId);
        } else {
          console.log('No smart card readers detected, using fallback device name');
        }
      } catch (e: any) {
        console.error('List USB devices error:', e);
      }

      await SmartCardModule.requestUsbPermission(deviceName);
      const response: string = await SmartCardModule.readCard('T=0');
      setCardData({ raw: response });
    } catch (e: any) {
      setError(`Error: ${e.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <LinearGradient colors={['#1e3c72', '#2a5298']} style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>Smart Card Reader</Text>
        <TouchableOpacity style={styles.button} onPress={readCard} disabled={isLoading}>
          <Text style={styles.buttonText}>{isLoading ? 'Reading...' : 'Read Card'}</Text>
        </TouchableOpacity>
        {isLoading && <ActivityIndicator size="large" color="#ffffff" />}
        {cardData && (
          <View style={styles.dataContainer}>
            <Text style={styles.dataText}>Card Data: {cardData.raw}</Text>
          </View>
        )}
        {error && <Text style={styles.errorText}>{error}</Text>}
      </View>
    </LinearGradient>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  card: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderRadius: 10,
    padding: 20,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.8,
    shadowRadius: 5,
    elevation: 5,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 20,
  },
  button: {
    backgroundColor: '#00b4d8',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 25,
    marginBottom: 20,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '600',
  },
  dataContainer: {
    marginTop: 10,
    alignItems: 'center',
  },
  dataText: {
    color: '#ffffff',
    fontSize: 16,
    marginVertical: 5,
    textAlign: 'center',
  },
  errorText: {
    color: '#ff4d4d',
    fontSize: 16,
    marginTop: 10,
    textAlign: 'center',
  },
});

export default App;


Install Dependencies:

Run npm install react-native-linear-gradient to add the gradient component.
Link it (if required, depending on your React Native version): npx react-native link react-native-linear-gradient.




Step 6: Configure ProGuard Rules
Ensure the native module and smart card classes are preserved in release builds.

Open android/app/proguard-rules.pro and add:
# Preserve SmartCardModule and related classes
-keep class com.smartcardapp.SmartCardModule { *; }
-keep class android.hardware.usb.** { *; }
-keep class javax.smartcardio.** { *; }
-keep class sun.security.action.** { *; }
-dontwarn sun.security.action.**




Step 7: Build and Test

Run in Debug Mode:

Connect your Android device and run:npx react-native run-android




Generate a Release APK:

Configure signing in android/gradle.properties:MYAPP_RELEASE_STORE_FILE=my-release-key.keystore
MYAPP_RELEASE_KEY_ALIAS=my-key-alias
MYAPP_RELEASE_STORE_PASSWORD=yourpassword
MYAPP_RELEASE_KEY_PASSWORD=yourpassword


Update android/app/build.gradle:android {
    ...
    signingConfigs {
        release {
            storeFile file(MYAPP_RELEASE_STORE_FILE)
            storePassword MYAPP_RELEASE_STORE_PASSWORD
            keyAlias MYAPP_RELEASE_KEY_ALIAS
            keyPassword MYAPP_RELEASE_KEY_PASSWORD
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}


Build the release APK:cd android
./gradlew assembleRelease




Install and Test:

Install the APK:adb install android/app/build/outputs/apk/release/app-release.apk


Test with a supported USB smart card reader and a smart card inserted.




Conclusion
By following these steps, you’ve successfully integrated the javax.smartcardio SDK into a React Native CLI project. The app dynamically detects and supports all compatible USB smart card readers, providing a robust solution for smart card operations on Android devices.