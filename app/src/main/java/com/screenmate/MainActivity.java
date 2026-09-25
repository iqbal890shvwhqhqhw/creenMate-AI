package com.screenmate;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY_PERMISSION = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStartBubble = findViewById(R.id.btnStartBubble);
        Button btnStopBubble = findViewById(R.id.btnStopBubble);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);

        btnStartBubble.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                startFloatingService();
            }
        });

        btnStopBubble.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingService.class));
            Toast.makeText(this, "Floating Bubble dihentikan.", Toast.LENGTH_SHORT).show();
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    private boolean checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Berikan izin 'Tampilkan di Atas Aplikasi Lain' terlebih dahulu.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService();
            } else {
                Toast.makeText(this, "Izin Overlay ditolak.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startFloatingService() {
        startService(new Intent(this, FloatingService.class));
        Toast.makeText(this, "Floating Bubble aktif! Klik ikon di layar untuk membuka.", Toast.LENGTH_SHORT).show();
    }
}
