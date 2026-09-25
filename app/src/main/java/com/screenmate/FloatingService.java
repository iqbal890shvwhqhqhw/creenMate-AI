package com.screenmate;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingBubble;
    private View floatingPanel;
    private WebView webView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupBubble();
        setupPanel();
    }

    private void setupBubble() {
        floatingBubble = new ImageView(this);
        ((ImageView) floatingBubble).setImageResource(android.R.drawable.ic_dialog_info); // Mock icon

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                150, 150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        floatingBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        int diffX = (int) (event.getRawX() - initialTouchX);
                        int diffY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            togglePanel();
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBubble, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingBubble, params);
    }

    private void setupPanel() {
        floatingPanel = new WebView(this);
        webView = (WebView) floatingPanel;
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        
        // Load local index.html or remote PWA url
        webView.loadUrl("file:///android_asset/index.html");

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        floatingPanel.setVisibility(View.GONE);
        windowManager.addView(floatingPanel, params);
    }

    private void togglePanel() {
        if (floatingPanel.getVisibility() == View.GONE) {
            floatingPanel.setVisibility(View.VISIBLE);
        } else {
            floatingPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubble != null) windowManager.removeView(floatingBubble);
        if (floatingPanel != null) windowManager.removeView(floatingPanel);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void captureScreen() {
            Intent intent = new Intent(FloatingService.this, ScreenCaptureService.class);
            startService(intent);
        }

        @JavascriptInterface
        public void closePanel() {
            floatingPanel.post(() -> togglePanel());
        }

        @JavascriptInterface
        public boolean isAccessibilityEnabled() {
            return AutoClickService.isRunning();
        }

        @JavascriptInterface
        public void performAutoClick(float normalizedX, float normalizedY) {
            if (AutoClickService.isRunning()) {
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                float absX = normalizedX * metrics.widthPixels;
                float absY = normalizedY * metrics.heightPixels;

                // Sembunyikan panel sebentar agar ketukan langsung mengenai game di bawahnya
                floatingPanel.post(() -> {
                    floatingPanel.setVisibility(View.GONE);
                    floatingBubble.postDelayed(() -> {
                        AutoClickService.getInstance().autoClickAt(absX, absY);
                    }, 250);
                });
            } else {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    android.widget.Toast.makeText(FloatingService.this,
                            "Harap aktifkan Layanan Aksesibilitas ScreenMate di Pengaturan HP!",
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }
    }
}
