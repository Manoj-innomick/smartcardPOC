import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  Button,
  TextInput,
  Alert,
  FlatList,
  Picker,
  CheckBox,
  TouchableOpacity,
  BackHandler,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { SmartCardModule, UsbModule } = NativeModules;
const smartCardEmitter = new NativeEventEmitter(SmartCardModule);
const usbEmitter = new NativeEventEmitter(UsbModule);

type LogEntry = { time: string; text: string };

const App = () => {
  const [terminals, setTerminals] = useState<string[]>([]);
  const [selectedTerminal, setSelectedTerminal] = useState<string>('');
  const [mode, setMode] = useState<'direct' | 'exclusive'>('exclusive');
  const [t0, setT0] = useState(true);
  const [t1, setT1] = useState(true);
  const [connected, setConnected] = useState(false);
  const [isDirectMode, setIsDirectMode] = useState(false);
  const [apduCommand, setApduCommand] = useState('80 84 00 00 08');
  const [controlCommand, setControlCommand] = useState('FF 00 04 00 03 01 00 01');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [monitoring, setMonitoring] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [usbStatus, setUsbStatus] = useState('Checking USB...');

  useEffect(() => {
    if (Platform.OS !== 'android') return;

    const checkUsbHost = async () => {
      const hasSupport = await UsbModule.hasUsbHost();
      if (!hasSupport) {
        Alert.alert('Error', 'USB Host not supported on this device.');
        BackHandler.exitApp();
      } else {
        addLog('USB Host supported.');
      }
    };
    checkUsbHost();

    refreshTerminals();

    const terminalsSub = smartCardEmitter.addListener('TerminalsUpdated', refreshTerminals);
    const cardEventSub = smartCardEmitter.addListener('CardEvent', (event) => {
      if (event.type === 'inserted') {
        addLog(`Card Inserted ${event.terminal} ATR: ${event.atr}`);
      } else if (event.type === 'removed') {
        addLog(`Card Removed ${event.terminal}`);
      }
    });

    const permissionSub = usbEmitter.addListener('UsbPermissionResult', (event) => {
      setUsbStatus(`USB Permission: ${event.granted ? 'Granted' : 'Denied'} for ${event.deviceName}`);
      if (event.granted) {
        addLog('USB permission granted.');
      } else {
        addLog('USB permission denied.');
      }
    });

    const attachedSub = usbEmitter.addListener('UsbDeviceAttached', () => {
      setUsbStatus('USB Device attached');
      addLog('USB Device attached - requesting permission');
      UsbModule.checkAndRequestPermission();
    });

    const detachedSub = usbEmitter.addListener('UsbDeviceDetached', () => {
      setUsbStatus('USB Device detached');
      addLog('USB Device detached');
      setConnected(false);
      setIsDirectMode(false);
    });

    UsbModule.checkAndRequestPermission();

    return () => {
      terminalsSub.remove();
      cardEventSub.remove();
      permissionSub.remove();
      attachedSub.remove();
      detachedSub.remove();
    };
  }, []);

  const refreshTerminals = async () => {
    try {
      const names = await SmartCardModule.listTerminals();
      setTerminals(names);
      if (names.length > 0 && !selectedTerminal) {
        setSelectedTerminal(names[0]);
        addLog(`Terminal Selected: ${names[0]}`);
      }
    } catch (e) {
      addLog(`Error refreshing terminals: ${e.message}`);
    }
  };

  const handleConnect = async () => {
    setIsLoading(true);
    if (connected) {
      try {
        await SmartCardModule.disconnect();
        setConnected(false);
        setIsDirectMode(false);
        addLog('Disconnected');
      } catch (e) {
        addLog(`Disconnect error: ${e.message}`);
      }
    } else {
      if (!selectedTerminal) {
        Alert.alert('Error', 'Select a terminal');
        setIsLoading(false);
        return;
      }
      let protocol = '';
      if (mode === 'direct') {
        protocol = 'direct';
      } else {
        if (!t0 && !t1) {
          Alert.alert('Error', 'Select at least one protocol');
          setIsLoading(false);
          return;
        }
        protocol = t0 && t1 ? '*' : t0 ? 'T=0' : 'T=1';
      }
      try {
        await SmartCardModule.selectTerminal(selectedTerminal);
        await SmartCardModule.connect(protocol);
        setConnected(true);
        setIsDirectMode(protocol === 'direct');
        addLog(`Connected with protocol: ${protocol}`);
      } catch (e) {
        addLog(`Connect error: ${e.message}`);
      }
    }
    setIsLoading(false);
  };

  const handleTransmitAPDU = async () => {
    try {
      const response = await SmartCardModule.transmitAPDU(apduCommand);
      addLog(`CommandAPDU: ${apduCommand.replace(/ /g, '')}`);
      addLog(`ResponseAPDU: ${response}`);
    } catch (e) {
      setConnected(false);
      setIsDirectMode(false);
      addLog(`APDU error: ${e.message}`);
    }
  };

  const handleTransmitControl = async () => {
    try {
      const response = await SmartCardModule.transmitControl(controlCommand);
      addLog(`EscapeCommand: ${controlCommand.replace(/ /g, '')}`);
      addLog(`EscapeResponse: ${response}`);
    } catch (e) {
      setConnected(false);
      setIsDirectMode(false);
      addLog(`Control error: ${e.message}`);
    }
  };

  const handleGetFirmware = async () => {
    try {
      const version = await SmartCardModule.getFirmware();
      addLog(`Firmware: ${version}`);
    } catch (e) {
      addLog(`Firmware error: ${e.message}`);
    }
  };

  const toggleMonitoring = async () => {
    const newMonitoring = !monitoring;
    setMonitoring(newMonitoring);
    try {
      if (newMonitoring) {
        await SmartCardModule.startMonitorCard();
        addLog('Card Monitoring ON');
      } else {
        await SmartCardModule.stopMonitorCard();
        addLog('Card Monitoring OFF');
      }
    } catch (e) {
      addLog(`Monitoring error: ${e.message}`);
    }
  };

  const addLog = (text: string) => {
    const time = new Date().toLocaleTimeString();
    setLogs((prev) => [...prev, { time, text }]);
  };

  const clearLogs = () => setLogs([]);

  const filterHexInput = (text: string) => text.toUpperCase().replace(/[^0-9A-F ]/g, '');

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Smart Card Reader</Text>
      <Text style={styles.statusText}>{usbStatus}</Text>

      <View style={styles.row}>
        <Text style={styles.label}>Terminal:</Text>
        <Picker
          selectedValue={selectedTerminal}
          style={styles.picker}
          enabled={!connected}
          onValueChange={(itemValue) => setSelectedTerminal(itemValue)}>
          {terminals.map((name) => (
            <Picker.Item key={name} label={name} value={name} />
          ))}
        </Picker>
        <Button title="Refresh" onPress={refreshTerminals} disabled={connected} />
      </View>

      <View style={styles.row}>
        <Text style={styles.label}>Mode:</Text>
        <TouchableOpacity onPress={() => setMode('direct')} disabled={connected}>
          <Text>Direct {mode === 'direct' ? '(selected)' : ''}</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={() => setMode('exclusive')} disabled={connected}>
          <Text>Exclusive {mode === 'exclusive' ? '(selected)' : ''}</Text>
        </TouchableOpacity>
      </View>

      {mode === 'exclusive' && (
        <View style={styles.row}>
          <Text style={styles.label}>Protocol:</Text>
          <CheckBox value={t0} onValueChange={setT0} disabled={connected} />
          <Text>T=0</Text>
          <CheckBox value={t1} onValueChange={setT1} disabled={connected} />
          <Text>T=1</Text>
        </View>
      )}

      <Button
        title={connected ? 'Disconnect' : 'Connect'}
        onPress={handleConnect}
        disabled={terminals.length === 0 || isLoading}
      />

      <View style={styles.row}>
        <Text style={styles.label}>Transmit:</Text>
        <TextInput
          style={styles.input}
          value={apduCommand}
          onChangeText={(text) => setApduCommand(filterHexInput(text))}
          editable={connected && !isDirectMode}
        />
        <Button title="Send" onPress={handleTransmitAPDU} disabled={!connected || isDirectMode || isLoading} />
      </View>

      <View style={styles.row}>
        <Text style={styles.label}>Control:</Text>
        <TextInput
          style={styles.input}
          value={controlCommand}
          onChangeText={(text) => setControlCommand(filterHexInput(text))}
          editable={connected}
        />
        <Button title="Send" onPress={handleTransmitControl} disabled={!connected || isLoading} />
      </View>

      <View style={styles.buttonRow}>
        <Button title="Get Version" onPress={handleGetFirmware} disabled={!selectedTerminal} />
        <Button title={monitoring ? 'Stop Monitor Card' : 'Monitor Card'} onPress={toggleMonitoring} />
        <Button title="Clear" onPress={clearLogs} />
      </View>

      {isLoading && <ActivityIndicator size="large" color="#00b4d8" />}

      <FlatList
        data={logs}
        renderItem={({ item }) => <Text style={styles.logText}>[{item.time}] {item.text}</Text>}
        keyExtractor={(item, index) => index.toString()}
        style={styles.logList}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 10, backgroundColor: '#f0f0f0' },
  title: { fontSize: 24, fontWeight: 'bold', textAlign: 'center', marginBottom: 10 },
  statusText: { fontSize: 16, textAlign: 'center', marginBottom: 10 },
  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  label: { fontSize: 18, marginRight: 10 },
  picker: { flex: 1, height: 50 },
  input: { flex: 1, borderWidth: 1, borderColor: '#ccc', padding: 5 },
  buttonRow: { flexDirection: 'row', justifyContent: 'space-around', marginBottom: 10 },
  logList: { flex: 1, borderWidth: 1, borderColor: '#ccc', padding: 5 },
  logText: { fontSize: 14, marginBottom: 5 },
});

export default App;