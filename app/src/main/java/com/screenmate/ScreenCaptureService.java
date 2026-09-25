package com.screenmate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
// Import lain yang dibutuhkan untuk MediaProjection, ImageReader, dll.

public class ScreenCaptureService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ScreenCapture", "Memulai capture layar...");
        
        // Logika untuk MediaProjection, ImageReader, menyimpan ke File,
        // lalu upload ke endpoint /api/analyze melalui HTTP POST.
        // Setelah selesai, kirim broadcast atau panggil Javascript interface di WebView
        // untuk mengupdate UI.
        
        return START_NOT_STICKY;
    }
}
