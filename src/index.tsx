import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-xbee-ble' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const XbeeBle = NativeModules.XbeeBle
  ? NativeModules.XbeeBle
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

class XbeeBleManager {
  constructor() {}

  start(options: {} | null) {
    return new Promise<void>((fulfill, reject) => {
      if (options == null) {
        options = {};
      }
      XbeeBle.start(options, (error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  scan() {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.scan((error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  stopScan() {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.stopScan((error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  sendUserDataRelay(address: string, iface: number, data: number[]) {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.sendUserDataRelay(address, iface, data, (error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  sendFile(address: string) {
    XbeeBle.sendFile(address);
  }

  connectToDevice(address: string, password: string) {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.connectToDevice(address, password, (error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  disconnectFromDevice(address: string) {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.disconnect(address, (error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  enableBluetooth() {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.enableBluetooth((error: null) => {
        if (error != null) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }
}

export default new XbeeBleManager();
