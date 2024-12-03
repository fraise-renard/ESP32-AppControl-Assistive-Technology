package com.example.myapplication;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;


//DATENA: ME DE IBAGENS, ME DE IBAGENS!!
public class BluetoothImageReceiver extends Thread {
    private final InputStream inputStream;
    private Handler mainHandler;
    private Context context;
    private final BufferedInputStream bufferedInputStream;
    private ClarifaiApiClient clarifai = null;
    private String concept = null;
    private int distance = 0;

    public BluetoothImageReceiver(BluetoothSocket socket, Handler mainHandler, Context context) {
        InputStream tempIn = null;
        try {
            tempIn = socket.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.inputStream = tempIn;
        this.mainHandler = mainHandler;
        this.context = context;
        this.bufferedInputStream = new BufferedInputStream(inputStream);
    }

    @Override
    public void run() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (context instanceof MainActivity) {
                    // Call the updateImageView method in the MainActivity
                    ((MainActivity) context).updateCaptureButton("CARREGANDO...");
                } else {
                    Log.e("BluetoothThread", "Context is not an instance of MainActivity.");
                }
            }
        });
        Log.d("BluetoothThread", "Started. Reading image size metadata.");
        try {
            currentThread().setPriority(Thread.MAX_PRIORITY);

            // Step 0: Read the 1-byte distance
            byte[] distanceBuffer = new byte[1];  // 1 byte for the distance
            int bytesReadDistance = inputStream.read(distanceBuffer); // Reads the first byte for distance
            if (bytesReadDistance != 1) {
                Log.e("BluetoothThread", "Failed to read distance byte.");
                return;
            }

            // Convert the distance byte to an integer
            distance = Byte.toUnsignedInt(distanceBuffer[0]);  // Convert byte to unsigned int (0-200)

            // Log the distance value
            Log.d("BluetoothThread", "Distance received: " + distance + " cm");

            // Step 1: Read the 4-byte image size
            byte[] sizeBuffer = new byte[4];
            int bytesReadSize = inputStream.read(sizeBuffer); // Reads the first 4 bytes for image size
            if (bytesReadSize != 4) {
                Log.e("BluetoothThread", "Failed to read image size metadata.");
                return;
            }

            // Convert sizeBuffer to an integer (Little-Endian)
            int imageSize = ByteBuffer.wrap(sizeBuffer)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            // Log the image size
            Log.d("BluetoothThread", "Image size received: " + imageSize + " bytes");


            // Step 2: Read image data
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; // 8KB buffer for faster chunk reading
            int totalBytesRead = 0;
            long startTime = System.currentTimeMillis();
            long timeout = 20000; // 20 seconds timeout for large images

            while (totalBytesRead < imageSize) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Log.e("BluetoothReceiver", "Read operation timed out after " + (System.currentTimeMillis() - startTime) + "ms.");
                    break;
                }

                int availableBytes = inputStream.available();
                if (availableBytes > 0) {
                    // Calculate the chunk size to read
                    int bytesToRead = Math.min(availableBytes, imageSize - totalBytesRead);
                    int bytesReadInChunk = inputStream.read(buffer, 0, bytesToRead);

                    if (bytesReadInChunk == -1) {
                        Log.e("BluetoothReceiver", "End of stream reached unexpectedly.");
                        break;
                    }

                    byteArrayOutputStream.write(buffer, 0, bytesReadInChunk);
                    totalBytesRead += bytesReadInChunk;

                    Log.d("BluetoothThread", "Bytes read: " + totalBytesRead + " / " + imageSize);
                }
            }

            // Step 3: Convert the accumulated byte data to a byte array
            byte[] imageData = byteArrayOutputStream.toByteArray();

            // After reading, check if image data is complete

            if (imageData.length == imageSize) {

                // Image data matches with the size
                Log.d("BluetoothThread", "Image data received successfully!");

                // Process the image (decode and classify) in a background thread to avoid UI freeze
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Decode the byte array into a Bitmap object
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                            if (context instanceof MainActivity) {
                                // Call the updateImageView method in the MainActivity
                                ((MainActivity) context).updateImageView(bitmap);
                                Log.d("BluetoothThread", "Image in activity set successfully!");

                            } else {
                                Log.e("BluetoothThread", "Context is not an instance of MainActivity.");
                            }

                        } catch (Exception e) {
                            Log.e("BluetoothThread", "Error decoding image: " + e.getMessage());
                        }
                    }
                });

                // IA THREAD:
                Log.d("BluetoothThread", "Sent the image to Clarifai, waiting for concept...");

                //Notifier
                CountDownLatch latch = new CountDownLatch(1);

                new Thread(() -> {
                    clarifai = new ClarifaiApiClient();

                    // Classify image using Clarifai API
                    try {
                        concept = clarifai.detectCentralObject(imageData);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    latch.countDown();  // Signal that the concept is ready
                }).start();

                // Wait for the result from Clarifai
                try {
                    latch.await();  // This will block until the latch count reaches 0 (i.e., when the concept is ready)
                    if (concept != null) {
                        mainHandler.post(() -> {
                            if (context instanceof MainActivity) {
                                // Call the updateImageView method in the MainActivity
                                ((MainActivity) context).updateImageDescription(concept + " a " + distance + "cm de distância");
                            } else {
                                Log.e("BluetoothThread", "Context is not an instance of MainActivity.");
                            }
                        });
                    } else {
                        Log.e("BluetoothThread", "Failed to classify image. Concept is null.");
                    }
                } catch (InterruptedException e) {
                    Log.e("BluetoothThread", "Error while waiting for Clarifai result", e);
                }
            } else {
                Log.e("BluetoothThread", "Image data received is incomplete or doesn't match the expected size!");
            }

        } catch (IOException e) {
            Log.e("BluetoothThread", "IOException occurred: " + e.getMessage());
        } catch (Exception e) {
            Log.e("BluetoothThread", "Unexpected error: " + e.getMessage());
        }
    }
}
