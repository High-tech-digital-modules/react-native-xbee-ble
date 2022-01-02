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
import com.digi.xbee.api.listeners.IUserDataRelayReceiveListener;
import com.digi.xbee.api.models.UserDataRelayMessage;

import android.os.AsyncTask;
import android.os.Environment;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

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
    private Map<String, UserDataRelayListener> relayDevicesListener;

    public XbeeBleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
        this.reactContext = reactContext;
        connectedXbeeDevices = new HashMap<String, XBeeBLEDevice>();
        relayDevicesListener = new HashMap<String, UserDataRelayListener>();
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
    public void requestConnectionPriority(String address, int priority) {
        XBeeBLEDevice xbeeDevice = connectedXbeeDevices.get(address);
        if (xbeeDevice != null) {
          xbeeDevice.requestConnectionPriority(priority);
        }
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
            connectedXbeeDevices.put(address, xbeeDevice);
            UserDataRelayListener relayListener = new UserDataRelayListener(address);
            relayDevicesListener.put(address, relayListener);
            xbeeDevice.addUserDataRelayListener(relayListener);
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
            UserDataRelayListener relayListener = relayDevicesListener.get(address);
            xbeeDevice.removeUserDataRelayListener(relayListener);
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
    private void sendFile(ReadableMap options, Callback callback) {
        if(!options.hasKey("address")) {
          callback.invoke("Address of device need to be provided");
        } else if (!options.hasKey("url")) {
          callback.invoke("Url of file does not exists");
        }
        if(!checkIfConnected(options.getString("address"))) {
          callback.invoke("Device is not connected");
        }
        new DownloadFileFromURL(options.getString("address"), callback)
          .execute(options.getString("url"));
    }

    public static WritableMap byteArrayToWritableMap(byte[] bytes) throws JSONException {
    		WritableMap object = Arguments.createMap();
    		object.putString("CDVType", "ArrayBuffer");
    		object.putString("data", bytes != null ? Base64.encodeToString(bytes, Base64.NO_WRAP) : null);
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

    /**
     * Listener to be notified when new User Data Relay messages are received.
     */
    private class UserDataRelayListener implements IUserDataRelayReceiveListener {
        private String id;
        public UserDataRelayListener(String id) {
          this.id = id;
        }

        @Override
        public void userDataRelayReceived(final UserDataRelayMessage userDataRelayMessage) {
            final String btId = this.id;
            (new Runnable() {
                @Override
                public void run() {
                  WritableMap map = Arguments.createMap();
                  map.putInt("sourceInterface", userDataRelayMessage.getSourceInterface().getID());
                  map.putString("id", btId);
                  WritableArray data = Arguments.createArray();
                  byte[] originData = userDataRelayMessage.getData();
                  for(int i = 0; i < originData.length; i++) {
                    data.pushInt(originData[i]);
                  }
                  map.putArray("data", data);
                  sendEvent("XbeeReceivedUserDataRelay", map);
                }
            }).run();
        }
    }

    /**
     * Background Async Task to download file
     * */
    private class DownloadFileFromURL extends AsyncTask<String, ReadableMap, String> {

        String address;
        Callback callback;

        public DownloadFileFromURL(String address, Callback callback) {
          this.address = address;
          this.callback = callback;
        }

        /**
         * Before starting background thread Show Progress Bar Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /**
         * Downloading file in background thread and send it to interface
         * */
        @Override
        protected String doInBackground(String... f_url) {
            int count;
            try {
                URL url = new URL(f_url[0]);
                URLConnection connection = "https".equals(url.getProtocol())
                  ? (HttpsURLConnection)url.openConnection()
                  : (URLConnection)url.openConnection();
                //URLConnection connection = url.openConnection();
                connection.connect();

                // this will be useful so that you can show a 0-100%
                // progress bar
                int lengthOfFile = connection.getContentLength();

                // download the file
                InputStream input = new BufferedInputStream(url.openStream(),
                        8192);

                int sizeOfBuffer = 234;
                byte data[] = new byte[sizeOfBuffer];
                long total = 0;
                long total_chunks = 0;
                long start = System.currentTimeMillis();
                final XBeeBLEDevice xbeeDevice = connectedXbeeDevices.get(address);
                while ((count = input.read(data)) != -1) {
                    total += count;
                    total_chunks++;

                    Log.i(LOG_TAG, "Chunks: " + Long.toString(total_chunks) + " Bytes: " + Long.toString(total)) ;

                    final XBeePacket xbeePacket = new UserDataRelayPacket(xbeeDevice.getNextFrameID(),
                            XBeeLocalInterface.SERIAL, count != sizeOfBuffer ? Arrays.copyOfRange(data, 0, count) : data);
                    try {
                        xbeeDevice.sendPacketAsync(xbeePacket);
                    } catch (XBeeException e) {
                        callback.invoke("Failed to send xbee relay message");
                        e.printStackTrace();
                        break;
                    }
                    WritableMap map = Arguments.createMap();
                    map.putString("address", address);
                    map.putInt("chunks", (int)total_chunks);
                    map.putInt("bytes", (int)total);
                    map.putInt("fileLength", lengthOfFile);
                    map.putInt("progress", (int) ((total * 100) / lengthOfFile));
                    map.putDouble("speed", (double)(total) / (System.currentTimeMillis() - start));
                    map.putBoolean("isDone", total == lengthOfFile);
                    publishProgress(map);
                }
                long end = System.currentTimeMillis();
                Log.i(LOG_TAG, "Total chunks: " + Long.toString(total_chunks) + " Total: " + Long.toString(end - start) + " [ms] "
                      + Long.toString(total) + " bytes => " + Double.toString(new Double(total) / new Double(end - start)) + " kB/s");

                input.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                callback.invoke(e.getMessage());
            }

            return null;
        }

        protected void onProgressUpdate(ReadableMap... map) {
            // setting progress percentage
            sendEvent("XbeeFileSendProgress", (WritableMap)map[0]);
        }

        @Override
        protected void onPostExecute(String result) {
          super.onPostExecute(result);
          callback.invoke();
        }
    }
}
