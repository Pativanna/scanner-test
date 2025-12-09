package com.test.scannertest;

import android.content.Intent;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZXingScanActivity extends AppCompatActivity {

    private static final String TAG = "ZXingScan";
    private PreviewView previewView;
    private TextView txtStatus;
    private TextView txtResult;
    private ExecutorService cameraExecutor;
    private MultiFormatReader reader;
    private boolean found = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        Button btnClose = findViewById(R.id.btnClose);

        txtStatus.setText("ZXing Scanner - Apunta a un código");

        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupZXingReader();
        startCamera();
    }

    private void setupZXingReader() {
        reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX
        ));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
        Log.i(TAG, "✅ ZXing reader initialized");
    }

    private void startCamera() {
        Log.i(TAG, "📷 Starting camera...");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                Log.i(TAG, "✅ Camera started successfully");
                runOnUiThread(() -> txtStatus.setText("Cámara activa - ZXing"));

            } catch (Exception e) {
                Log.e(TAG, "❌ Camera start failed", e);
                runOnUiThread(() -> txtStatus.setText("Error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (found) {
            imageProxy.close();
            return;
        }

        Image image = imageProxy.getImage();
        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) {
            imageProxy.close();
            return;
        }

        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            byte[] yData = new byte[yBuffer.remaining()];
            yBuffer.get(yData);

            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                yData, width, height, 0, 0, width, height, false
            );
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                Result result = reader.decodeWithState(bitmap);
                if (result != null) {
                    found = true;
                    String value = result.getText();
                    String format = result.getBarcodeFormat().toString();
                    Log.i(TAG, "✅ Barcode found: " + value + " (" + format + ")");
                    
                    runOnUiThread(() -> {
                        txtResult.setText(value);
                        txtStatus.setText("✅ Encontrado!");
                    });

                    // Return result after brief delay
                    previewView.postDelayed(() -> {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("barcode", value);
                        resultIntent.putExtra("format", format);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }, 1000);
                }
            } catch (NotFoundException e) {
                // No barcode in this frame, continue
            } finally {
                reader.reset();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing frame", e);
        } finally {
            imageProxy.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
