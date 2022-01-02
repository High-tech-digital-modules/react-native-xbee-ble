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

export interface StartOptions {
  showAlert?: boolean;
  restoreIdentifierKey?: string;
  queueIdentifierKey?: string;
  forceLegacy?: boolean;
}

export interface FileProgress {
  address: string;
  chunks: number;
  bytes: number;
  fileLength: number;
  progress: number;
  speed: number;
  isDone: boolean;
}

export interface UserDataRelayData {
  sourceInterface: UserDataRelayInterface;
  id: string;
  data: number[];
}

export enum ConnectionPriority {
  balanced = 0,
  high = 1,
  low = 2,
}

export enum UserDataRelayInterface {
  serial = 0,
  ble = 1,
  micropython = 2,
}

class XbeeBleManager {
  constructor() {}

  start(options?: StartOptions | null) {
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

  requestConnectionPriority(address: string, priority: ConnectionPriority) {
    XbeeBle.requestConnectionPriority(address, priority);
  }

  sendUserDataRelay(
    address: string,
    iface: UserDataRelayInterface,
    data: number[]
  ) {
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

  sendFile(options: any) {
    return new Promise<void>((fulfill, reject) => {
      XbeeBle.sendFile(options, (error: any) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
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
