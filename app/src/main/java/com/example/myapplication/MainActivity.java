package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private BluetoothSocket bluetoothSocket;
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private OutputStream outputStream;
    private InputStream inputStream;
    Button captureButton = null, searchButton = null;
    ImageView imageView = null;
    TextView imageDesc = null;
    private Handler mainHandler;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                searchButton.setText("Procurando...");
                searchButton.setEnabled(false);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                searchButton.setText("Procurar");
                searchButton.setEnabled(true);

            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (device != null && "ESP32-CAM".equals(device.getName())) {
                    Toast.makeText(getApplicationContext(), "Found ESP32-CAM", Toast.LENGTH_SHORT).show();

                    // Cancel discovery to save resources
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    bluetoothAdapter.cancelDiscovery();

                    // Call the method to connect to the device
                    connectToDevice(device);
                }
            }
        }
    };

    private void sendMessage(String message) {
        try {
            if (outputStream != null) {
                outputStream.write(message.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to update the UI (ImageView) from a background thread
    public void updateImageView(final Bitmap bitmap) {
        if (bitmap != null) {
            captureButton.setVisibility(View.INVISIBLE);
            imageView.setImageBitmap(bitmap);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    public void updateImageDescription(String desc){
        imageDesc.setVisibility(View.VISIBLE);
        imageDesc.setText(desc);
    }

    public void updateCaptureButton(String text){
        captureButton.setClickable(false);
        captureButton.setText(text);
    }

    private void catchImage() throws Exception {
        //capture protocol
        sendMessage("C");
        BluetoothImageReceiver btrec = new BluetoothImageReceiver(bluetoothSocket, mainHandler, MainActivity.this);
        //writing on the byte buffer based on the esp retrieval
        btrec.start(); //external reading/displaying image thread
    }

    private void listenTest() {
        try {
            byte[] buffer = new byte[1024];
            int bytes;
            // Continuously read data from the input stream
            while ((bytes = inputStream.read(buffer)) != -1) {
                String receivedMessage = new String(buffer, 0, bytes).trim();
                if(receivedMessage.equals("Connected")){
                    Toast.makeText(MainActivity.this, "ESP32 is " + receivedMessage, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connection lost or error occurred.", Toast.LENGTH_SHORT).show()
            );
        }
    }

    //Bluetooth Socket
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(BluetoothDevice device){
        try {
            Toast.makeText(this, "Trying to establish BT socket...", Toast.LENGTH_SHORT).show();
            if(bluetoothSocket == null){
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
            }
            // Get the output stream for sending data
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            sendMessage("Test");
            listenTest();
            showPairedWindow();

        }catch(IOException e){
            Toast.makeText(this, "BT socket error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPairedWindow() {
        // Create the dialog
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.pairedwindow, null);
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        //Shutter button
        captureButton = dialogView.findViewById(R.id.capturebutton);

        imageView = dialogView.findViewById(R.id.imageView);
        imageDesc = dialogView.findViewById(R.id.img_description);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    catchImage();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // Find the close button in the dialog layout
        ImageButton closeButton = dialogView.findViewById(R.id.closebtn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss(); // Close the dialog
            }
        });

        // Show the dialog
        alertDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH}, 1);
        }

        mainHandler = new Handler(Looper.getMainLooper());
        searchButton = findViewById(R.id.btn);

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            finish(); // Close the app
            return;
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, 1);
        }

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);

        // Set up button click listener to start discovery
        searchButton.setOnClickListener(v -> bluetoothAdapter.startDiscovery());

    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        try {
            bluetoothSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

}