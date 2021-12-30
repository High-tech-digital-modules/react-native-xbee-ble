package com.reactnativexbeeble;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.facebook.react.bridge.Callback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.os.Build;
import android.util.Base64;
import org.json.JSONException;

import com.digi.xbee.api.android.XBeeBLEDevice;
import com.digi.xbee.api.exceptions.BluetoothAuthenticationException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.packet.XBeePacket;
import com.digi.xbee.api.packet.relay.UserDataRelayPacket;
import com.digi.xbee.api.models.XBeeLocalInterface;

import androidx.annotation.Nullable;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

import java.util.*;

@ReactModule(name = XbeeBleModule.NAME)
public class XbeeBleModule extends ReactContextBaseJavaModule {
    public static final String NAME = "XbeeBle";
    public static final String LOG_TAG = "ReactNativeBleManager";
    private static final int ENABLE_REQUEST = 539;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BleScanCallback scanCallback;
    private Context context;
    private ReactApplicationContext reactContext;
    private boolean forceLegacy;
    private Callback enableBluetoothCallback;

    private Map<String, XBeeBLEDevice> connectedXbeeDevices;

    public XbeeBleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
        this.reactContext = reactContext;
        connectedXbeeDevices = new HashMap<String, XBeeBLEDevice>();
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (bluetoothAdapter == null) {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = manager.getAdapter();
        }
        return bluetoothAdapter;
    }

    private BluetoothManager getBluetoothManager() {
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        }
        return bluetoothManager;
    }

    public void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
    }

    public void sendEvent(String eventName, String name) {
            getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, name);
        }

    @ReactMethod
    public void scan(Callback callback) {
        Log.d(LOG_TAG, "scan");
        if (getBluetoothAdapter() == null) {
            Log.d(LOG_TAG, "No bluetooth support");
            callback.invoke("No bluetooth support");
            return;
        }
        if (!getBluetoothAdapter().isEnabled()) {
            Log.d(LOG_TAG, "No bluetooth support");
            return;
        }

        Log.d(LOG_TAG, "Ok starting scan");
        getBluetoothAdapter().startLeScan(scanCallback);
        callback.invoke();
    }

    @ReactMethod
    public void stopScan(Callback callback) {
        Log.d(LOG_TAG, "Stop scan");
        if (getBluetoothAdapter() == null) {
            Log.d(LOG_TAG, "No bluetooth support");
            callback.invoke("No bluetooth support");
            return;
        }
        if (!getBluetoothAdapter().isEnabled()) {
            callback.invoke();
            return;
        }
        getBluetoothAdapter().stopLeScan(scanCallback);
        sendEvent("BleManagerStopScan", Arguments.createMap());
        callback.invoke();
    }

    @ReactMethod
    public void start(ReadableMap options, Callback callback) {
        Log.d(LOG_TAG, "start");
        if (getBluetoothAdapter() == null) {
            Log.d(LOG_TAG, "No bluetooth support");
            callback.invoke("No bluetooth support");
            return;
        }
        forceLegacy = false;
        if (options.hasKey("forceLegacy")) {
            forceLegacy = options.getBoolean("forceLegacy");
        }

        scanCallback = new BleScanCallback();

        callback.invoke();
        Log.d(LOG_TAG, "BleManager initialized");
    }

    @ReactMethod
    public void enableBluetooth(Callback callback) {
        if (getBluetoothAdapter() == null) {
            Log.d(LOG_TAG, "No bluetooth support");
            callback.invoke("No bluetooth support");
            return;
        }
        if (!getBluetoothAdapter().isEnabled()) {
            enableBluetoothCallback = callback;
            Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (getCurrentActivity() == null)
                callback.invoke("Current activity not available");
            else
                getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
        } else
            callback.invoke();
    }

    @ReactMethod
    public void connectToDevice(final String address, final String password, Callback callback) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          XBeeBLEDevice xbeeDevice = new XBeeBLEDevice(context, address, password);
          try {
            Log.d(LOG_TAG, "Connecting");
            xbeeDevice.open();
            xbeeDevice.requestConnectionPriority(1);
            connectedXbeeDevices.put(address, xbeeDevice);
            callback.invoke();
            Log.d(LOG_TAG, "Connected");
          } catch (BluetoothAuthenticationException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "Failed to connect device check password");
            callback.invoke("Failed to connect device check password");
          } catch (XBeeException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "Failed to connect device");
            callback.invoke("Failed to connect device");
          }
        }
      }).start();
    }

    @ReactMethod
    public void disconnect(String address, Callback callback) {
        Log.d(LOG_TAG, "Disconnect from: " + address);

        XBeeBLEDevice xbeeDevice = connectedXbeeDevices.get(address);
        if (xbeeDevice != null) {
            xbeeDevice.close();
            callback.invoke();
        } else
            callback.invoke("Peripheral not found");
    }

    @ReactMethod
    public void sendUserDataRelay(String address, int destInterface, ReadableArray data, Callback callback) throws XBeeException {
      if(!checkIfConnected(address)) {
        callback.invoke("Device is not connected");
        return;
      }
      byte[] decoded = new byte[data.size()];
      for (int i = 0; i < data.size(); i++) {
          decoded[i] = new Integer(data.getInt(i)).byteValue();
      }
      final XBeeBLEDevice xbeeDevice = connectedXbeeDevices.get(address);
      xbeeDevice.sendUserDataRelay(XBeeLocalInterface.get(destInterface), decoded);
      callback.invoke();
    }

    private boolean checkIfConnected(String address) {
      return connectedXbeeDevices.containsKey(address);
    }

    @ReactMethod
    private void sendFile(String address) {
        if(!checkIfConnected(address))
          return;
        final XBeeBLEDevice xbeeDevice = connectedXbeeDevices.get(address);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                int total_len = 0;
                long start2 = System.currentTimeMillis();
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < 12; i++) {
                    stringBuilder.append("0123456789012345678");
                }
                String resultString = stringBuilder.toString();
                Thread t1 = null;
                Thread t2 = null;
                Thread t[] = new Thread[] {null, null, null};
                for (int i = 0; i < 100; i++) {
                    String s = resultString + "aa" + Integer.toString(i);
                    total_len += s.length();
                    long start = System.currentTimeMillis();
                    /*device.sendUserDataRelay(XBeeLocalInterface.SERIAL,
                            (s).getBytes());*/
                    final XBeePacket xbeePacket = new UserDataRelayPacket(xbeeDevice.getNextFrameID(),
                            XBeeLocalInterface.SERIAL, (s).getBytes());
                    try {
                        xbeeDevice.sendPacketAsync(xbeePacket);
                    } catch (XBeeException e) {
                        e.printStackTrace();
                    }


                    long end = System.currentTimeMillis();
                    Log.i(LOG_TAG, Long.toString(end - start) + " [ms] " + Integer.toString(total_len) + " bytes ");
                }
                long end2 = System.currentTimeMillis();
                Log.i(LOG_TAG, "Total: " + Long.toString(end2 - start2) + " [ms] "
                        + Integer.toString(total_len) + " bytes => " + Double.toString(new Double(total_len) / new Double(end2 - start2)) + " kB/s");
                // Send the User Data Relay message.

                //Toast.makeText(RelayConsoleActivity.this, getResources().getString(R.string.send_success), Toast.LENGTH_SHORT).show();
            }
        };
        r.run();
    }

    public static WritableMap byteArrayToWritableMap(byte[] bytes) throws JSONException {
    		WritableMap object = Arguments.createMap();
    		object.putString("CDVType", "ArrayBuffer");
    		object.putString("data", bytes != null ? Base64.encodeToString(bytes, Base64.NO_WRAP) : null);
    		//object.putArray("bytes", bytes != null ? bytesToWritableArray(bytes) : null);
    		return object;
    }

    private class BleScanCallback implements BluetoothAdapter.LeScanCallback {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                   final byte[] scanRecord) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Log.i(LOG_TAG, "DiscoverPeripheral: " + device.getName());
              WritableMap map = Arguments.createMap();
              WritableMap advertising = Arguments.createMap();
              try {
              			map.putString("name", device.getName());
              			map.putString("id", device.getAddress()); // mac address
              			map.putInt("rssi", rssi);

              			String name = device.getName();
              			if (name != null)
              				advertising.putString("localName", name);

                    advertising.putMap("manufacturerData", byteArrayToWritableMap(scanRecord));

                    // No scanResult to access so we can't check if peripheral is connectable
                    advertising.putBoolean("isConnectable", true);

                    map.putMap("advertising", advertising);
              } catch (Exception e) { // this shouldn't happen
                e.printStackTrace();
              }
              sendEvent("BleManagerDiscoverPeripheral", map);
            }
          });
        }
    }
}
