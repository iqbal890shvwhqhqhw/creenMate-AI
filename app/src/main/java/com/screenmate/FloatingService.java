package com.screenmate;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private ImageView floatingBubble;
    private WebView floatingPanel;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private boolean isPanelOpen = false;

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
        floatingBubble.setImageResource(R.drawable.ic_launcher);
        floatingBubble.setBackgroundColor(Color.TRANSPARENT);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                160, 160,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 30;
        bubbleParams.y = 250;

        floatingBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int diffX = (int) (event.getRawX() - initialTouchX);
                        int diffY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(diffX) > 15 || Math.abs(diffY) > 15) {
                            isClick = false;
                        }
                        bubbleParams.x = initialX + diffX;
                        bubbleParams.y = initialY + diffY;
                        windowManager.updateViewLayout(floatingBubble, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            togglePanel();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingBubble, bubbleParams);
    }

    private void setupPanel() {
        floatingPanel = new WebView(this);
        floatingPanel.setBackgroundColor(Color.TRANSPARENT);
        floatingPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings webSettings = floatingPanel.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        floatingPanel.setWebChromeClient(new WebChromeClient());
        floatingPanel.addJavascriptInterface(new WebAppInterface(), "Android");
        floatingPanel.loadUrl("file:///android_asset/index.html");

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        floatingPanel.setVisibility(View.GONE);
        windowManager.addView(floatingPanel, panelParams);
    }

    private void togglePanel() {
        if (isPanelOpen) {
            closePanel();
        } else {
            openPanel();
        }
    }

    private void openPanel() {
        isPanelOpen = true;
        floatingPanel.setVisibility(View.VISIBLE);
    }

    private void closePanel() {
        isPanelOpen = false;
        floatingPanel.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubble != null) {
            try { windowManager.removeView(floatingBubble); } catch (Exception ignored) {}
        }
        if (floatingPanel != null) {
            try { windowManager.removeView(floatingPanel); } catch (Exception ignored) {}
        }
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void closePanel() {
            new Handler(Looper.getMainLooper()).post(() -> {
                FloatingService.this.closePanel();
            });
        }

        @JavascriptInterface
        public void exitApp() {
            new Handler(Looper.getMainLooper()).post(() -> {
                FloatingService.this.closePanel();
                Toast.makeText(FloatingService.this, "ScreenMate AI dinonaktifkan.", Toast.LENGTH_SHORT).show();
                stopSelf();
            });
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

                new Handler(Looper.getMainLooper()).post(() -> {
                    FloatingService.this.closePanel();
                    floatingBubble.postDelayed(() -> {
                        AutoClickService.getInstance().autoClickAt(absX, absY);
                    }, 300);
                });
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(FloatingService.this,
                            "Aktifkan Layanan Aksesibilitas ScreenMate di Pengaturan HP terlebih dahulu!",
                            Toast.LENGTH_LONG).show();
                });
            }
        }
    }
}
