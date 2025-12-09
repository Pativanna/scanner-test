package com.test.scannertest;

import android.content.Intent;
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
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MLKitScanActivity extends AppCompatActivity {

    private static final String TAG = "MLKitScan";
    private PreviewView previewView;
    private TextView txtStatus;
    private TextView txtResult;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private boolean found = false;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        Button btnClose = findViewById(R.id.btnClose);

        txtStatus.setText("ML Kit Scanner - Apunta a un código");

        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupMLKitScanner();
        startCamera();
    }

    private void setupMLKitScanner() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build();
        scanner = BarcodeScanning.getClient(options);
        Log.i(TAG, "✅ ML Kit scanner initialized");
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
                runOnUiThread(() -> txtStatus.setText("Cámara activa - ML Kit"));

            } catch (Exception e) {
                Log.e(TAG, "❌ Camera start failed", e);
                runOnUiThread(() -> txtStatus.setText("Error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (found || isProcessing) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        InputImage inputImage = InputImage.fromMediaImage(
            mediaImage, 
            imageProxy.getImageInfo().getRotationDegrees()
        );

        scanner.process(inputImage)
            .addOnSuccessListener(barcodes -> {
                if (!found && !barcodes.isEmpty()) {
                    Barcode barcode = barcodes.get(0);
                    String value = barcode.getRawValue();
                    if (value != null && !value.isEmpty()) {
                        found = true;
                        String format = formatToString(barcode.getFormat());
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
                }
                isProcessing = false;
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "ML Kit scan failed", e);
                isProcessing = false;
            })
            .addOnCompleteListener(task -> imageProxy.close());
    }

    private String formatToString(int format) {
        switch (format) {
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_QR_CODE: return "QR_CODE";
            case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
            default: return "UNKNOWN";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (scanner != null) {
            scanner.close();
        }
    }
}
