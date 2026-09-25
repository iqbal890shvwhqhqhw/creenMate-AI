package com.screenmate;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;

public class AutoClickService extends AccessibilityService {

    private static final String TAG = "AutoClickService";
    private static AutoClickService instance;

    public interface ScreenshotListener {
        void onSuccess(Bitmap bitmap);
        void onError(String error);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Layanan Aksesibilitas ScreenMate Siap.");
    }

    public static AutoClickService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Tangkap layar HP secara instan dan hening (tanpa dialog popup) menggunakan Accessibility API
     */
    public void captureScreen(ScreenshotListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
                        try {
                            HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                            ColorSpace colorSpace = screenshotResult.getColorSpace();
                            Bitmap hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
                            Bitmap copy = hwBitmap.copy(Bitmap.Config.ARGB_8888, false);
                            buffer.close();
                            listener.onSuccess(copy);
                        } catch (Exception e) {
                            Log.e(TAG, "Gagal memproses bitmap layar: ", e);
                            listener.onError("Gagal memproses bitmap layar: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        Log.e(TAG, "Gagal tangkap layar. Error code: " + errorCode);
                        listener.onError("Gagal tangkap layar (Kode: " + errorCode + "). Pastikan izin Aksesibilitas aktif.");
                    }
                });
            } catch (Exception e) {
                listener.onError("Error menjalankan tangkap layar: " + e.getMessage());
            }
        } else {
            listener.onError("Fitur tangkap layar otomatis membutuhkan Android 11 ke atas.");
        }
    }

    /**
     * Mengetuk layar HP secara fisik/virtual pada koordinat piksel (x, y)
     */
    public void autoClickAt(float x, float y) {
        Log.d(TAG, "Mengetuk layar otomatis pada koordinat: (" + x + ", " + y + ")");

        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(clickPath, 0, 75);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(stroke);

        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Ketukan virtual berhasil dieksekusi di layar.");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.w(TAG, "Ketukan virtual dibatalkan oleh sistem.");
            }
        }, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
