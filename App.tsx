/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import React, { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View, ActivityIndicator } from 'react-native';
import LinearGradient from 'react-native-linear-gradient';
import { NativeModules } from 'react-native';

const { SmartCardModule } = NativeModules;

const App = () => {
  const [cardData, setCardData] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');

  const readCard = async () => {
    setIsLoading(true);
    setError('');
    try {
      // Replace 'AbCircleCardTerminalUSB' with your actual USB device name if known
      await SmartCardModule.requestUsbPermission('AbCircleCardTerminalUSB');
      const response: string = await SmartCardModule.readCard('T=0');
      setCardData(`Card Data: ${response}`);
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
        {cardData && <Text style={styles.dataText}>{cardData}</Text>}
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
  dataText: {
    color: '#ffffff',
    fontSize: 16,
    marginTop: 10,
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