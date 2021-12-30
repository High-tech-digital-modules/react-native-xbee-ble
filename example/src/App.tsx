import * as React from 'react';

import {
  StyleSheet,
  View,
  Text,
  Platform,
  PermissionsAndroid,
  NativeEventEmitter,
  NativeModules,
  Button,
  FlatList,
  TouchableHighlight,
  SafeAreaView,
} from 'react-native';
import XbeeBleManager from 'react-native-xbee-ble';
import { useRef } from 'react';
const XbeeBleManagerModule = NativeModules.XbeeBle;
const xbeeBleManagerEmitter = new NativeEventEmitter(XbeeBleManagerModule);
import { Buffer } from 'buffer';
export interface Peripheral {
  id: string;
  rssi: number;
  name?: string;
  advertising: AdvertisingData;
  connected?: boolean;
}

export interface AdvertisingData {
  isConnectable?: boolean;
  localName?: string;
  manufacturerData?: any;
  serviceUUIDs?: string[];
  txPowerLevel?: number;
}

export default function App() {
  const peripherals = new Map();
  const [list, setList] = React.useState<Peripheral[]>([]);

  const currentAddress = useRef<string>('');

  const handleDiscoverPeripheral = (peripheral: Peripheral) => {
    if (!peripheral.name) {
      peripheral.name = 'NO NAME';
    }
    if (!peripherals.has(peripheral.id) && peripheral.name.includes('XBee')) {
      peripherals.set(peripheral.id, peripheral);
      setList(Array.from(peripherals.values()));
    }
  };

  const handleStopScan = () => {
    console.log('Scan is stopped');
  };

  const handleDataReceived = (data: any) => {
    console.log(data);
  };

  React.useEffect(() => {
    XbeeBleManager.start({ showAlert: false });

    xbeeBleManagerEmitter.addListener(
      'BleManagerDiscoverPeripheral',
      handleDiscoverPeripheral
    );
    xbeeBleManagerEmitter.addListener('BleManagerStopScan', handleStopScan);
    xbeeBleManagerEmitter.addListener(
      'XbeeReceivedUserDataRelay',
      handleDataReceived
    );

    if (Platform.OS === 'android' && Platform.Version >= 23) {
      PermissionsAndroid.check(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      ).then((result) => {
        if (result) {
          console.log('Permission is OK');
        } else {
          PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
          ).then((result) => {
            if (result) {
              console.log('User accept');
            } else {
              console.log('User refuse');
            }
          });
        }
      });
    }
  }, []);

  const startScan = () => {
    XbeeBleManager.scan()
      .then(() => console.log('Scan started'))
      .catch((err) => console.log(err));
  };

  const stopScan = () => {
    XbeeBleManager.stopScan()
      .then(() => console.log('Scan stopped'))
      .catch((err) => console.log(err));
  };

  const connect = (item: Peripheral) => () => {
    if (!item.connected) {
      XbeeBleManager.stopScan().then(() =>
        XbeeBleManager.connectToDevice(item.id, '1234')
          .then(() => {
            XbeeBleManager.requestConnectionPriority(item.id, 1);
            currentAddress.current = item.id;
            item.connected = true;
            peripherals.set(item.id, item);
            setList(Array.from(peripherals.values()));
          })
          .catch((e) => console.log(e))
      );
    } else {
      XbeeBleManager.disconnectFromDevice(item.id).then(() => {
        item.connected = false;
        peripherals.set(item.id, item);
        setList(Array.from(peripherals.values()));
      });
    }
  };

  const renderItem = (item: Peripheral) => {
    const color = item.connected ? 'green' : '#fff';
    return (
      <TouchableHighlight
        style={[{ backgroundColor: color }]}
        onPress={connect(item)}
      >
        <View>
          <Text
            style={{
              fontSize: 12,
              textAlign: 'center',
              color: '#333333',
              padding: 10,
            }}
          >
            {item.name}
          </Text>
          <Text
            style={{
              fontSize: 10,
              textAlign: 'center',
              color: '#333333',
              padding: 2,
            }}
          >
            RSSI: {item.rssi}
          </Text>
          <Text
            style={{
              fontSize: 8,
              textAlign: 'center',
              color: '#333333',
              padding: 2,
              paddingBottom: 20,
            }}
          >
            {item.id}
          </Text>
          {item.connected && (
            <Button
              title="Send test"
              onPress={() =>
                XbeeBleManager.sendUserDataRelay(item.id, 0, [
                  ...Buffer.from('HELLO', 'utf-8'),
                ])
              }
            />
          )}
        </View>
      </TouchableHighlight>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={list}
        renderItem={({ item }) => renderItem(item)}
        keyExtractor={(item) => item.id}
      />
      <Button title="Scan" onPress={startScan} />
      <Button title="Stop" onPress={stopScan} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
