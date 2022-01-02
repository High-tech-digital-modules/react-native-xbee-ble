# react-native-xbee-ble

This package wraps [xbee-android](https://github.com/digidotcom/xbee-android). Currently, only android is supported
because xbee does not have Objective-C or Swift implementation. Support for IOS could be done but needs to be
implemented base on e.g. [react-native-ble-manager](https://github.com/innoveit/react-native-ble-manager) and SRP
authentication and packets encryption needs to be done.

## Installation

```sh
npm install react-native-xbee-ble
```

#### Android
 - Add sources for xbee libraries
```
android/build.gradle
//..
allprojects {
    repositories {
    //..
      maven {
        url 'http://ftp1.digi.com/support/m-repo/'
    }
    //..
  }
}
```
 - there might be error also with minSdk 19 needed so also update value in **android/build.gradle**.
 - there might be error with **android:allowBackup="false"** change it to true

## Usage
Permission for android to cover all library functionality
```js
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Before everything start ble manager. Typically, use it in useEffect
```js
import XbeeBleManager from 'react-native-xbee-ble';

useEffect(async () => {
  //...
  await XbeeBleManager.start();
  //...
},[]);
```

### Start scan

```js
const startScan = () => {
  XbeeBleManager.scan()
    .then(() => console.log('Scan started'))
    .catch((err) => console.log(err));
};

// Stop scan
const stopScan = () => {
  XbeeBleManager.stopScan()
    .then(() => console.log('Scan stopped'))
    .catch((err) => console.log(err));
};
```

#### Event handler emitter
This needs to be used to get possibility to set event handlers
```js
const XbeeBleManagerModule = NativeModules.XbeeBle;
const xbeeBleManagerEmitter = new NativeEventEmitter(XbeeBleManagerModule);
```

#### Handle devices discovers
```js
// in use effect used for start manager add
useEffect(async () => {
  //...
  xbeeBleManagerEmitter.addListener(
    'BleManagerDiscoverPeripheral',
    handleDiscoverPeripheral
  );
  //...
  return () => {
    //...
    xbeeBleManagerEmitter.removeListener(
      'BleManagerDiscoverPeripheral',
      handleDiscoverPeripheral
    );
    //...
  };
}, []);

const handleDiscoverPeripheral = (peripheral: Peripheral) => {
  if (!peripheral.name) {
    peripheral.name = 'NO NAME';
  }
  // Xbee device has by default name Xbee. There is a filter by name, remove it if neccessary
  if (!peripherals.has(peripheral.id) && peripheral.name.includes('XBee')) {
    // Do something else with peripherals typically set list state
  }
};
```
### Connect/Disconnect
```js
const connect = (item: Peripheral) => () => {
    if (!item.connected) {
      // Stop scan before connect
      XbeeBleManager.stopScan().then(() =>
        XbeeBleManager.connectToDevice(item.id, '1234') // 1234 is password set by XCTU in BLE setting
          .then(() => {
            // use this to speed up communication or delete if speed is not neccesarry
            // with this setting it is able to write about 7,2 kB/s without about 2 kB/s
            XbeeBleManager.requestConnectionPriority(
              item.id,
              ConnectionPriority.high,
            );
            item.connected = true;
            // set list state value
          })
          .catch((e) => console.log(e))
      );
    } else {
      XbeeBleManager.disconnectFromDevice(item.id).then(() => {
        item.connected = false;
        // set list state value
      });
    }
  };
```

### Send user data relay
```js
// item is Peripheral, could be also id directly
XbeeBleManager.sendUserDataRelay(
  item.id,
  UserDataRelayInterface.serial, // use UserDataRelayInterface.ble for echo
  [...Buffer.from('HELLO', 'utf-8')]
)
```

### Receive user data relay
When xbee received user data
```js
// in use effect used for start manager add
useEffect(async () => {
  //...
  xbeeBleManagerEmitter.addListener(
    'XbeeReceivedUserDataRelay',
    handleDataReceived
  );
  //...
  return () => {
    //...
    xbeeBleManagerEmitter.removeListener(
      'XbeeReceivedUserDataRelay',
      handleDataReceived
    );
    //...
  };
}, []);

//...
const handleDataReceived = (data: UserDataRelayData) => {
  console.log(data);
};
//...
```

### Send file using BLE
Use [post images](https://postimages.org/) to upload some image and later used as url for this function.
After image is uploaded use direct link. This function is good when you use BLE to transfer some files to MCU using UART.

```js
// item is Peripheral, could be also id directly
XbeeBleManager.sendFile({
  address: item.id,
  url: 'https://i.postimg.cc/7ZtsYgFT/Screenshot-from-2021-12-21-15-45-06.png'
})
  .then(() => console.log('file sent'))
  .catch((err) => console.log(err))
```

#### Handle file transfer progress
```js
// in use effect used for start manager add
useEffect(async () => {
  //...
  xbeeBleManagerEmitter.addListener(
    'XbeeFileSendProgress',
    handleFileProgress
  );
  //...
  return () => {
    //...
    xbeeBleManagerEmitter.removeListener(
      'XbeeFileSendProgress',
      handleFileProgress
    );
    //...
  };
}, []);

const handleFileProgress = (progress: FileProgress) => {
  console.log(progress);
};

```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
