import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, TouchableOpacity, View, ActivityIndicator, Alert } from 'react-native';
import LinearGradient from 'react-native-linear-gradient';
import { NativeModules, NativeEventEmitter } from 'react-native';

const { SmartCardModule, UsbModule } = NativeModules;
const usbEmitter = new NativeEventEmitter(UsbModule);

const App = () => {
  const [cardData, setCardData] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [usbStatus, setUsbStatus] = useState<string>('Checking USB...');

  useEffect(() => {
    // Debug modules
    console.log('SmartCardModule methods:', Object.keys(SmartCardModule));
    console.log('UsbModule methods:', Object.keys(UsbModule));

    // Initial USB check
    checkUsbAndRequestPermission();

    // Listeners for USB events
    const permissionListener = usbEmitter.addListener('UsbPermissionResult', (event) => {
      setUsbStatus(`USB Permission: ${event.granted ? 'Granted' : 'Denied'} for ${event.deviceName}`);
      if (event.granted) {
        Alert.alert('Success', 'USB permission granted! You can now read the card.');
      } else {
        setError('USB permission denied. Please plug in a card reader.');
      }
    });

    const attachedListener = usbEmitter.addListener('UsbDeviceAttached', () => {
      setUsbStatus('USB Device attached - requesting permission');
      checkUsbAndRequestPermission();
    });

    const detachedListener = usbEmitter.addListener('UsbDeviceDetached', () => {
      setUsbStatus('USB Device detached');
      setCardData('');
      setError('Card reader disconnected.');
    });

    return () => {
      permissionListener.remove();
      attachedListener.remove();
      detachedListener.remove();
    };
  }, []);

  const checkUsbAndRequestPermission = async () => {
    try {
      await UsbModule.checkAndRequestPermission();
      setUsbStatus('USB permission request sent');
    } catch (e: any) {
      setError(`USB check error: ${e.message}`);
      console.error('USB check error:', e);
    }
  };

  const readCard = async () => {
    setIsLoading(true);
    setError('');
    try {
      // Verify USB permission before reading
      await checkUsbAndRequestPermission();

      let deviceName = 'AbCircleCardTerminalUSB'; // Fallback
      let devices;
      try {
        devices = await SmartCardModule.listUsbDevices();
        if (devices.length > 0) {
          deviceName = devices[0].deviceName;
          console.log('Detected device:', deviceName);
        } else {
          throw new Error('No card reader found');
        }
      } catch (e: any) {
        throw new Error(`Listing devices error: ${e.message}`);
      }

      const response: string = await SmartCardModule.readCard('T=0');
      setCardData(`Card Data: ${response}`);
    } catch (e: any) {
      setError(e.message);
      console.error('Read card error:', e);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <LinearGradient colors={['#1e3c72', '#2a5298']} style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>Smart Card Reader</Text>
        <Text style={styles.statusText}>{usbStatus}</Text>
        <TouchableOpacity style={styles.button} onPress={readCard} disabled={isLoading}>
          <Text style={styles.buttonText}>{isLoading ? 'Reading...' : 'Read Card'}</Text>
        </TouchableOpacity>
        {isLoading && <ActivityIndicator size="large" color="#ffffff" />}
        {cardData && <Text style={styles.dataText}>{cardData}</Text>}
        {error && <Text style={styles.errorText}>{error}</Text>}
      </View>
    </LinearGradient>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
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
  title: { fontSize: 24, fontWeight: 'bold', color: '#ffffff', marginBottom: 20 },
  statusText: { color: '#ffffff', fontSize: 16, marginBottom: 10 },
  button: {
    backgroundColor: '#00b4d8',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 25,
    marginBottom: 20,
  },
  buttonText: { color: '#ffffff', fontSize: 18, fontWeight: '600' },
  dataText: { color: '#ffffff', fontSize: 16, marginTop: 10, textAlign: 'center' },
  errorText: { color: '#ff4d4d', fontSize: 16, marginTop: 10, textAlign: 'center' },
});

export default App;