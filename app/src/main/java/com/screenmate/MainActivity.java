package com.screenmate;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY_PERMISSION = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            promptAccessibilityIfNeeded();
            startFloatingService();
        }
    }

    private void promptAccessibilityIfNeeded() {
        if (!AutoClickService.isRunning()) {
            android.widget.Toast.makeText(this,
                    "Aktifkan 'ScreenMate AI' di menu Aksesibilitas agar bisa Auto-Click jawaban!",
                    android.widget.Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                promptAccessibilityIfNeeded();
                startFloatingService();
            }
        }
    }

    private void startFloatingService() {
        startService(new Intent(this, FloatingService.class));
        finish();
    }
}
