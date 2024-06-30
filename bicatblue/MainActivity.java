package com.banledcamung.bicatblue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLEConnect";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler, handler1, handler2;
    private boolean scanning, isResponseReceive;
    Button sendButton, debugBtn;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<String> discoveredDevices = new ArrayList<>();
    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();

    HashMap<String,String> addressToUUID;
    private BluetoothGatt bluetoothGatt;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private static final UUID SERVICE_UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private EditText inputText;
    private TextView receivedText;
    private TextView deviceInfoText;
    private TextView servicesText;

    TextView debugText;
    String uuidStr ="";
    private BluetoothDevice selectedDevice;

    BottomNavigationView bottomNavigationView;
    RelativeLayout settingLayout;
    RelativeLayout runBtnPanel;
    SharedPreferences ItemData;
    ToggleButton connectBtn, runBtn;
    String fileName = "";
    String lastMessage = "";
    String lastFileName = "";

    boolean debugMode;
    int debugCount;
    int tryCount;
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setCustomDensity();
        ItemData = getSharedPreferences("ItemData",MODE_PRIVATE);
        SharedPreferences.Editor editor = ItemData.edit();
        editor.remove("titleText");
        editor.remove("imgID");
        editor.remove("fileName");
        editor.apply();

        Button scanButton = findViewById(R.id.scan_button);
        ListView listView = findViewById(R.id.device_list);
        inputText = findViewById(R.id.input_text);
        receivedText = findViewById(R.id.received_text);
        deviceInfoText = findViewById(R.id.device_info);
        servicesText = findViewById(R.id.services_text);
        debugBtn = findViewById(R.id.debug_btn);
        debugText = findViewById(R.id.debug_text);
        Button connectButton = findViewById(R.id.connect_button);
        connectBtn = findViewById(R.id.connectBtn);
        runBtn = findViewById(R.id.run_btn);
        sendButton = findViewById(R.id.send_button);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        settingLayout = findViewById(R.id.setting_layout);
        runBtnPanel= findViewById(R.id.run_button_pannel);
        addressToUUID = new HashMap<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, discoveredDevices);
        listView.setAdapter(devicesArrayAdapter);
        sendButton.setEnabled(false);
        handler = new Handler();
        handler1 = new Handler();
        handler2 = new Handler();
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        //checkPermissionsAndStartScan();

        isResponseReceive = false;
        tryCount = 0;
        debugCount = 0;
        replaceFragment(new HomeFragment());
        setPanelVisible(View.VISIBLE);

        bottomNavigationView.setOnItemSelectedListener(item ->{
            if(item.getItemId()==R.id.home_){
                replaceFragment(new HomeFragment());
                setPanelVisible(View.VISIBLE);
            } else if (item.getItemId()==R.id.list_){
                replaceFragment(new ListFragment());
            } else if (item.getItemId()==R.id.setting_){
                moveToSettingFragment();

            } else {
                replaceFragment(new HelpFragment());
            }
            return true;
        });
        scanButton.setOnClickListener(v -> {
            if (scanning) {
                stopScan();
            } else {
                checkPermissionsAndStartScan();
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (scanning) {
                stopScan();
            }
            selectedDevice = bluetoothDevices.get(position);
            deviceInfoText.setText("Selected device: " + selectedDevice.getName() + " (" + selectedDevice.getAddress() + ")");
            uuidStr = addressToUUID.get(selectedDevice.getAddress());
        });

        connectButton.setOnClickListener(v -> {
            if (selectedDevice != null) {
                connectToDevice(selectedDevice);
                if(uuidStr!=null){
                    sendButton.setEnabled(true);
                } else {
                    sendButton.setEnabled(false);
                }
            } else {
                Toast.makeText(MainActivity.this, "No device selected", Toast.LENGTH_SHORT).show();
            }
        });

        connectBtn.setOnClickListener(v->{
            if(connectBtn.isChecked()){
                if (selectedDevice != null) {
                    connectToDevice(selectedDevice);
                } else {
                    Toast.makeText(MainActivity.this, "No device selected", Toast.LENGTH_SHORT).show();
                    connectBtn.setChecked(false);
                }
            } else {
                sendMessage("Disconnect");
                if(bluetoothGatt != null){
                    bluetoothGatt.disconnect();
                }
            }
        });

        runBtn.setOnClickListener(v->{
            if(debugMode){
                StringBuilder sb = new StringBuilder();
                sb.append("lastFileName = " + lastFileName+"\n"+"fileName = " + fileName);
                debugText.setText(sb.toString());
            }
            if(runBtn.isChecked()) {
                if("".equals(fileName)||fileName.equals(lastFileName)) {
                    sendMessage("Continue");
                } else if(!fileName.equals(lastFileName)){
                    lastFileName = fileName;
                    isResponseReceive = false;
                    sendMessage(fileName);
                    tryCount = 0;
                    handler1.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(!isResponseReceive && tryCount < 5) {
                                sendMessage("Continue");
                                tryCount++;
                            }
                            handler1.postDelayed(this, 500);
                        }
                    },500);
                }
            } else {
                sendMessage("Pause");
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = inputText.getText().toString();
            sendMessage(message);
        });

        debugBtn.setOnClickListener(v->{
            if(!debugMode) {
                debugCount++;
                if (debugCount >= 3) {
                    debugMode = true;
                    debugText.setText("Debug ON");
                }
            } else {
                debugCount--;
                if (debugCount <= 0) {
                    debugMode = false;
                    debugText.setText("");
                    debugCount = 0;
                }
            }
        });

    }

    public void setCustomDensity(){
        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float currentDensity = displayMetrics.density;
        float scalingFactor = displayMetrics.widthPixels*1.0f/1080;
        displayMetrics.density = 2.625f*scalingFactor;
        displayMetrics.densityDpi=(int)(480*scalingFactor+0.5);
        //displayMetrics.scaledDensity = 2.8875f*scalingFactor;
//        displayMetrics.xdpi = 537.88135f*scalingFactor;
//        displayMetrics.ydpi = 539.1013f*scalingFactor;

        //  debugText.setText(displayMetrics.xdpi + " " + displayMetrics.ydpi+" "+ displayMetrics.scaledDensity+" "+displayMetrics.widthPixels);
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frame_layout,fragment);
        fragmentTransaction.commit();
        setPanelVisible(View.GONE);
        settingLayout.setVisibility(View.GONE);
    }

    private void moveToSettingFragment() {
        replaceFragment(new SettingFragment());
        settingLayout.setVisibility(View.VISIBLE);
        if(!scanning){
            checkPermissionsAndStartScan();
        }
    }

    private void setPanelVisible(int visible) {
        runBtnPanel.setVisibility(visible);
    }


    @SuppressLint("MissingPermission")
    private void sendMessage(String message) {
        if (bluetoothGatt != null && message.length() > 0) {
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic != null) {
                    //setCharacteristicIndication(characteristic, true);
                    characteristic.setValue(message);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    servicesText.setText("Sent '" + message +"'");
                    if(debugMode){
                        debugText.setText("Sent '" + message +"'");
                    }
                    lastMessage = message;
                } else {
                    servicesText.setText("Null Characteristic");
                }
            } else {
                servicesText.setText("Null serivce");
            }
        }
    }


    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);

    private void checkPermissionsAndStartScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION});
            } else {
                startScan();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
            } else {
                startScan();
            }
        }
    }

    private void onPermissionsResult(Map<String, Boolean> permissions) {
        boolean allGranted = true;
        for (Boolean granted : permissions.values()) {
            if (!granted) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            startScan();
        } else {
            Toast.makeText(this, "Permissions denied. Cannot scan for BLE devices.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        discoveredDevices.clear();
        bluetoothDevices.clear();
        devicesArrayAdapter.notifyDataSetChanged();
        handler.postDelayed(this::stopScan, SCAN_PERIOD);
        scanning = true;
        bluetoothLeScanner.startScan(leScanCallback);
        Toast.makeText(this, "Scanning for BLE devices...", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        scanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show();
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            @SuppressLint("MissingPermission") String deviceInfo = device.getName() + " (" + device.getAddress() + ")";

            // Lấy UUID từ gói quảng cáo (nếu có)
            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
            if (serviceUuids != null && !serviceUuids.isEmpty()) {
                for (ParcelUuid uuid : serviceUuids) {
                    //deviceInfo += "\n  Service UUID: " + uuid.getUuid().toString();
                    addressToUUID.put(address,uuid.getUuid().toString());
                }
            }

            if (!discoveredDevices.contains(deviceInfo)) {
                discoveredDevices.add(deviceInfo);
                bluetoothDevices.add(device);
                devicesArrayAdapter.notifyDataSetChanged();
            }
            Log.d(TAG, "Device found: " + deviceInfo);
        }
    };


    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
    }

    @SuppressLint("MissingPermission")
    private void setCharacteristicIndication(BluetoothGattCharacteristic characteristic, boolean enable) {
        bluetoothGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
        if (descriptor != null) {
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        } else {
            servicesText.setText("Null descriptor");
        }
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                bluetoothGatt.discoverServices();
                runOnUiThread(()->{
                    showToast("Connect successful");
                    runBtn.setEnabled(true);
                });
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                //runOnUiThread(() -> sendButton.setEnabled(false));
                runOnUiThread(()->{
                    showToast("Disconnected");
                    connectBtn.setChecked(false);
                    runBtn.setChecked(false);
                    runBtn.setEnabled(false);
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.");
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.i(TAG, "Service UUID: " + service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        Log.i(TAG, "Characteristic UUID: " + characteristic.getUuid().toString());
                        setCharacteristicIndication(characteristic, true);
                    }
                }
                runOnUiThread(() -> sendButton.setEnabled(true));
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String value = new String(characteristic.getValue());
                runOnUiThread(() -> receivedText.setText("(Read) Received: " + value));
                Log.i(TAG, "Characteristic read: " + value);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String value = new String(characteristic.getValue());
            Log.i(TAG, "Characteristic written: " + value);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String value = new String(characteristic.getValue());
            runOnUiThread(() -> {
                receivedText.setText("(Change) Received: " + value);
                handler2.post(()->{
                    if(lastMessage.contains("Name") && !isResponseReceive){
                        sendMessage("Continue");
                        isResponseReceive = true;
                    }
                });
                //sendMessage("Reponse from app");
            });



            Log.i(TAG, "Characteristic changed: " + value);
        }
    };

    private void showToast(String toast) {
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    public void moveToListFragment() {
        replaceFragment(new ListFragment());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SharedPreferences.Editor editor = ItemData.edit();
        editor.remove("titleText");
        editor.remove("imgID");
        editor.remove("fileName");
        editor.apply();

        sendMessage("Disconnect");
    }

    public void selectItem(int position, int tabIndex) {
        bottomNavigationView.setSelectedItemId(R.id.home_);
        int curPos = position;
        String keytab = "b";
        if(tabIndex == 1){
            keytab = "b";
        } else if (tabIndex == 2){
            keytab = "c";
        }
        String imgIDstr = keytab+curPos;
        lastFileName = fileName;
        fileName = "Name;;" + curPos;

        //debugText.setText("fileName: " + fileName + "\n lastFileName: " + lastFileName);

        int imgID = getResources().getIdentifier(imgIDstr,"drawable",getPackageName());

//        String nameIDstr = "s" + curPos+"_name";
//        int nameID = getResources().getIdentifier(nameIDstr, "string",getPackageName());

        String name = getString(R.string.s_name) + " " + (curPos+1);
        SharedPreferences.Editor editor = ItemData.edit();
        editor.putInt("imgID",imgID);
        editor.putString("titleText",name);
        editor.putString("fileName",fileName);
        editor.apply();
        replaceFragment(new HomeFragment());
        setPanelVisible(View.VISIBLE);
    }
}