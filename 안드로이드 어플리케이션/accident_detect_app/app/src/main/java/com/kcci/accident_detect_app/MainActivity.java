package com.kcci.accident_detect_app;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Button btnSearch, btnSend, btnConnect, btnStop, btnClientStart, btnSendServer;
    private TextView mTvBluetoothStatus;
    private EditText editText;

    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> mBluetoothDevices;

    Handler mBluetoothHandler;
    static Handler clientHandler;

    ClientThread clientThread;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;
    LocationListener locationListener;
    LocationManager locationManager;
    String locationProvider;

    Location lastKnownLocation;

    double lng;
    double lat;


    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionCheck();

        btnSearch = findViewById(R.id.button);
        btnConnect = findViewById(R.id.button2);
        btnSend = findViewById(R.id.button3);
        btnStop = findViewById(R.id.button4);
        btnClientStart = findViewById(R.id.button5);
        btnSendServer = findViewById(R.id.button6);
        mTvBluetoothStatus = findViewById(R.id.textview);
        editText = findViewById(R.id.editText);

        mBluetoothDevices = new HashSet<>();


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
        }
        else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "블루투스가 이미 활성화 되어 있습니다.", Toast.LENGTH_LONG).show();
                mTvBluetoothStatus.append("활성화");
            }
            else {
                Toast.makeText(getApplicationContext(), "블루투스가 활성화 되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                Intent intentBluetoothEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(intentBluetoothEnable);
            }
        }

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanningDevice();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectSelectedDevice("IOT12");
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mThreadConnectedBluetooth != null) {

                    mThreadConnectedBluetooth.write(editText.getText().toString());
                    //mTvBluetoothStatus.setText("");
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOff();
                clientOff();
            }
        });

        btnClientStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clientOn();
            }
        });

        btnSendServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String strSend = editText.getText().toString();
                    //Log.d("Main", "longtitude=" + lng + ", latitude=" + lat);
                clientThread.sendData("[iotserver]GPS@"+ Double.toString(lng)+"@"+Double.toString(lat)+"@5");
                //mTvBluetoothStatus.append("[iotserver]GPS@"+ Double.toString(lng)+"@"+Double.toString(lat)+"@5");
                //.sendData("[iotserver]37.5421736@126.8414885@5");

            }
        });

        btnSend.setClickable(false);


        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); //BluetoothAdapter.ACTION_STATE_CHANGED : 블루투스 상태변화 액션
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED); //연결 확인
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED); //연결 끊김 확인
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);    //기기 검색됨
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);   //기기 검색 시작
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);  //기기 검색 종료
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        registerReceiver(mBluetoothReceiver,intentFilter);

        mBluetoothHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == BT_MESSAGE_READ){
                    int nValue = 0;
                    String readMessage = null;
                    try {
                        byte[] temp = (byte[])msg.obj;
                        byte temp2 = temp[0];

                        if (temp2 < 0)
                            nValue = (int)temp2 + 256;
                        else
                            nValue = (int)temp2;

                        //readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mTvBluetoothStatus.setText(Integer.toString(nValue) + "\n");
                    if(nValue >= 2){
                        if(clientThread != null){
                            clientThread.sendData("[iotserver]GPS@"+ Double.toString(lng)+"@"+Double.toString(lat)+"@"+Integer.toString(nValue));
                        }
                    }
                }
            }
        };

        clientHandler = new ClientHandler();






        // Acquire a reference to the system Location Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // GPS 프로바이더 사용가능여부
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 네트워크 프로바이더 사용가능여부
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        Log.d("Main", "isGPSEnabled="+ isGPSEnabled);
        Log.d("Main", "isNetworkEnabled="+ isNetworkEnabled);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                lat = location.getLatitude();
                lng = location.getLongitude();

                //mTvBluetoothStatus.setText("latitude: "+ lat +", longitude: "+ lng);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                mTvBluetoothStatus.setText("onStatusChanged");
            }

            public void onProviderEnabled(String provider) {
                mTvBluetoothStatus.setText("onProviderEnabled");
            }

            public void onProviderDisabled(String provider) {
                mTvBluetoothStatus.setText("onProviderDisabled");
            }
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);

        // 수동으로 위치 구하기
        locationProvider = LocationManager.GPS_PROVIDER;
        lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
        if (lastKnownLocation != null) {
            lng = lastKnownLocation.getLongitude();
            lat = lastKnownLocation.getLatitude();
            Log.d("Main", "longtitude=" + lng + ", latitude=" + lat);
        }

    }



    void permissionCheck(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_PHONE_NUMBERS,
                            Manifest.permission.READ_PHONE_STATE
                    },
                    1);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_PHONE_NUMBERS,
                            Manifest.permission.READ_PHONE_STATE
                    },
                    1);
        }


        locationPermissionRequest.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts
                            .RequestMultiplePermissions(), result -> {
                        Boolean fineLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarseLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_COARSE_LOCATION,false);
                        if (fineLocationGranted != null && fineLocationGranted) {
                            // Precise location access granted.
                        } else if (coarseLocationGranted != null && coarseLocationGranted) {
                            // Only approximate location access granted.
                        } else {
                            // No location access granted.
                        }
                    }
            );

    void clientOn() {
        if(clientThread == null){
            clientThread = new ClientThread(getPhoneNum(MainActivity.this));
            clientThread.start();
        }
    }

    void clientOff() {
        if(clientThread != null){
            clientThread.stopClient();
            ClientThread.socket = null;
        }
    }
    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
            mTvBluetoothStatus.setText("비활성화");
        }
        else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    void scanningDevice(){
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }else{
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothAdapter.isDiscovering()) {
                        Log.d(TAG, "run: cancel discovery" );
                        mBluetoothAdapter.cancelDiscovery();
                        btnSend.setClickable(true);
                    }
                }
            },10000);
            Log.d(TAG, "scanningDevice: ");
            mBluetoothAdapter.startDiscovery();
            if (mThreadConnectedBluetooth != null) {
                mThreadConnectedBluetooth.stop();
            }
        }
    }

    void connectSelectedDevice(String selectedDeviceName) {
        for(BluetoothDevice tempDevice : mBluetoothDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    class ClientHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Bundle bundle = msg.getData();
            String data = bundle.getString("msg");
            mTvBluetoothStatus.append(data+"\n");
            String[] splitStr = data.split("]");

            splitStr[1] = splitStr[1].replaceAll("[^a-zA-Z]","");
            mTvBluetoothStatus.append(splitStr[1].length()+"\n");
            if(splitStr[1].equals("sendmegps")){
                if(clientThread != null){
                    clientThread.sendData("[iotserver]GPS@"+ Double.toString(lng)+"@"+Double.toString(lat)+"@0");
                }
            }
            if(splitStr[1].equals("warning")){
                mTvBluetoothStatus.append(splitStr[1]+"\n");
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_warning, null);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setView(dialogView);

                AlertDialog alertDialog = builder.create();

                alertDialog.show();
            }
        }
    }

    public static String getPhoneNum(Context context) {
        String phoneNum = "";
        TelephonyManager telManager = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);
        phoneNum = telManager.getLine1Number().toString();
        Log.d(TAG, "getPhoneNum: "+ phoneNum);
        if(phoneNum.startsWith("+82")) {
            phoneNum = phoneNum.replace("+8210", "");
        }
        if(phoneNum.startsWith("010")) {
            phoneNum = phoneNum.replace("010", "");
        }
        return phoneNum;
    }

    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "데이터 전송 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 해제 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();   //입력된 action
            Toast.makeText(context, "받은 액션 : "+action , Toast.LENGTH_SHORT).show();
            Log.d(TAG, action);
            final BluetoothDevice device =   intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String name = null;
            if (device != null) {
                name = device.getName();    //broadcast를 보낸 기기의 이름을 가져온다.
            }
            //입력된 action에 따라서 함수를 처리한다
            switch (action){
                case BluetoothDevice.ACTION_ACL_CONNECTED:  //블루투스 기기 연결
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:   //블루투스 기기 끊어짐
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED: //블루투스 기기 검색 시작
                    break;
                case BluetoothDevice.ACTION_FOUND:  //블루투스 기기 검색 됨, 블루투스 기기가 근처에서 검색될 때마다 수행됨
                    String device_name = device.getName();
                    String device_Address = device.getAddress();
                    //본 함수는 블루투스 기기 이름의 앞글자가 "GSM"으로 시작하는 기기만을 검색하는 코드이다
                    if(device_name != null && device_name.length() > 0){
                        Log.d(TAG, device_name);
                        Log.d(TAG, device_Address);
                        mBluetoothDevices.add(device);
                        mTvBluetoothStatus.append("--" + device_name + "\n");
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:    //블루투스 기기 검색 종료
                    Log.d("Bluetooth", "Call Discovery finished");
                    //StartBluetoothDeviceConnection();   //원하는 기기에 연결
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:

                    break;
            }

        }
    };
}