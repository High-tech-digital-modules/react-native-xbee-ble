import * as React from 'react';
import { useRef } from 'react';

import {
  Button,
  FlatList,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableHighlight,
  View,
} from 'react-native';
import XbeeBleManager, {
  ConnectionPriority,
  FileProgress,
  Peripheral,
  UserDataRelayData,
  UserDataRelayInterface,
} from 'react-native-xbee-ble';
import { Buffer } from 'buffer';

const XbeeBleManagerModule = NativeModules.XbeeBle;
const xbeeBleManagerEmitter = new NativeEventEmitter(XbeeBleManagerModule);

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

  const handleFileProgress = (progress: FileProgress) => {
    console.log(progress);
  };

  const handleDataReceived = (data: UserDataRelayData) => {
    console.log(data);
  };

  React.useEffect(() => {
    XbeeBleManager.start();

    xbeeBleManagerEmitter.addListener(
      'BleManagerDiscoverPeripheral',
      handleDiscoverPeripheral
    );
    xbeeBleManagerEmitter.addListener('BleManagerStopScan', handleStopScan);
    xbeeBleManagerEmitter.addListener(
      'XbeeReceivedUserDataRelay',
      handleDataReceived
    );

    xbeeBleManagerEmitter.addListener(
      'XbeeFileSendProgress',
      handleFileProgress
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
    return () => {
      xbeeBleManagerEmitter.removeListener(
        'BleManagerDiscoverPeripheral',
        handleDiscoverPeripheral
      );
      xbeeBleManagerEmitter.removeListener(
        'BleManagerStopScan',
        handleStopScan
      );
      xbeeBleManagerEmitter.removeListener(
        'XbeeReceivedUserDataRelay',
        handleDataReceived
      );
      xbeeBleManagerEmitter.removeListener(
        'XbeeFileSendProgress',
        handleFileProgress
      );
    };
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
            XbeeBleManager.requestConnectionPriority(
              item.id,
              ConnectionPriority.high
            );
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
                XbeeBleManager.sendUserDataRelay(
                  item.id,
                  UserDataRelayInterface.serial,
                  [...Buffer.from('HELLO', 'utf-8')]
                )
              }
            />
          )}
          {item.connected && (
            <Button
              title="Send file test"
              onPress={() =>
                XbeeBleManager.sendFile({
                  address: item.id,
                  url: 'https://i.postimg.cc/7ZtsYgFT/Screenshot-from-2021-12-21-15-45-06.png'
                })
                  .then(() => console.log('file sent'))
                  .catch((err) => console.log(err))
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
