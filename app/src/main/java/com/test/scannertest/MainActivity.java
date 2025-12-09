package com.test.scannertest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ZXING = 1;
    private static final int REQUEST_MLKIT = 2;

    private TextView txtResult;
    private int pendingRequest = 0;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                launchScanner(pendingRequest);
            } else {
                Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtResult = findViewById(R.id.txtResult);
        Button btnZXing = findViewById(R.id.btnZXing);
        Button btnMLKit = findViewById(R.id.btnMLKit);

        btnZXing.setOnClickListener(v -> checkPermissionAndLaunch(REQUEST_ZXING));
        btnMLKit.setOnClickListener(v -> checkPermissionAndLaunch(REQUEST_MLKIT));
    }

    private void checkPermissionAndLaunch(int requestType) {
        pendingRequest = requestType;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            launchScanner(requestType);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchScanner(int requestType) {
        Intent intent;
        if (requestType == REQUEST_ZXING) {
            intent = new Intent(this, ZXingScanActivity.class);
        } else {
            intent = new Intent(this, MLKitScanActivity.class);
        }
        startActivityForResult(intent, requestType);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            String barcode = data.getStringExtra("barcode");
            String format = data.getStringExtra("format");
            String method = requestCode == REQUEST_ZXING ? "ZXing" : "ML Kit";
            txtResult.setText("✅ " + method + "\nCódigo: " + barcode + "\nFormato: " + format);
        } else if (resultCode == RESULT_CANCELED) {
            txtResult.setText("Escaneo cancelado");
        }
    }
}
