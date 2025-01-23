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
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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
import java.util.Locale;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private BluetoothSocket bluetoothSocket;
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private OutputStream outputStream;
    private InputStream inputStream;
    Button captureButton = null, searchButton = null;
    ImageView imageView = null;
    TextView imageDesc = null;
    AlertDialog alertDialog = null;
    TextToSpeech tts = null;
    private Handler mainHandler;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                searchButton.setText("Procurando...");
                speakOutLoud("Procurando");
                searchButton.setEnabled(false);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(searchButton.getVisibility() == View.VISIBLE) {
                    searchButton.setText("Procurar");
                    speakOutLoud("Procurar");
                    searchButton.setEnabled(true);
                }

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
                    connectToDevice(device);
                }
            }
        }
    };

    private void speakOutLoud(String text) {
        // Speak out loud without needing a new Activity
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

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

    public void updateImageDescription(String desc) {
        // First, update the UI with the description text
        imageDesc.setVisibility(View.VISIBLE);
        imageDesc.setText(desc);

        // Start TTS to speak the description
        speakOutLoud(desc);

        // Run the checking of TTS status in a background thread to avoid blocking the UI
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Wait for TTS to finish speaking
                while (tts.isSpeaking()) {
                    try {
                        Thread.sleep(100);  // Sleep for a short period to prevent busy-waiting
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // Once speaking is done, dismiss the AlertDialog on the main thread, spawn a new one
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (alertDialog != null && alertDialog.isShowing()) {
                            alertDialog.dismiss();  // Close the AlertDialog
                            showPairedWindow();
                        }
                    }
                });
            }
        }).start();
    }


    public void updateCaptureButton(String text) {
        captureButton.setClickable(false);
        captureButton.setText(text);
        speakOutLoud("Carregando");
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
                if (receivedMessage.equals("Connected")) {
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
    private void connectToDevice(BluetoothDevice device) {
        try {
            Toast.makeText(this, "Trying to establish BT socket...", Toast.LENGTH_SHORT).show();
            if (bluetoothSocket == null) {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
            }
            // Get the output stream for sending data
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            sendMessage("Test");
            listenTest();
            showPairedWindow();
            searchButton.setVisibility(View.INVISIBLE);

        } catch (IOException e) {
            Toast.makeText(this, "BT socket error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPairedWindow() {
        // Create the dialog
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.pairedwindow, null);
        alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        //Shutter button
        captureButton = dialogView.findViewById(R.id.capturebutton);
        speakOutLoud("Capturar");
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

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // Set language to Portuguese
                    int langResult = tts.setLanguage(new Locale("pt", "BR"));
                    speakOutLoud("Procurar");
                }
            }
        });
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
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothAdapter.startDiscovery();
            }
        });
    }

    protected void onDestroy() {
        tts.shutdown();
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