package com.screenmate;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
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
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.net.Uri;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;

public class FloatingService extends Service {

    private static final String TAG = "FloatingService";
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
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

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
        public void openAccessibilitySettings() {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        @JavascriptInterface
        public void captureScreenNow(final String mode) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!AutoClickService.isRunning()) {
                    Toast.makeText(FloatingService.this, "Aktifkan Layanan Aksesibilitas 'ScreenMate AI' terlebih dahulu untuk tangkap layar & auto-click!", Toast.LENGTH_LONG).show();
                    openAccessibilitySettings();
                    return;
                }

                // Sembunyikan panel dan bubble sementara agar tangkapan layar bersih hanya menampilkan konten HP
                FloatingService.this.closePanel();
                floatingBubble.setVisibility(View.GONE);

                // Berikan jeda 300ms agar UI hilang dari frame layar
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    AutoClickService.getInstance().captureScreen(new AutoClickService.ScreenshotListener() {
                        @Override
                        public void onSuccess(Bitmap bitmap) {
                            try {
                                // Kompres dan perkecil ukuran jika terlalu besar agar pengiriman cepat
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                float maxDim = 1080f;
                                if (width > maxDim || height > maxDim) {
                                    float scale = Math.min(maxDim / width, maxDim / height);
                                    width = Math.round(width * scale);
                                    height = Math.round(height * scale);
                                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                                }

                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                                byte[] byteArray = stream.toByteArray();
                                final String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);

                                new Handler(Looper.getMainLooper()).post(() -> {
                                    floatingBubble.setVisibility(View.VISIBLE);
                                    FloatingService.this.openPanel();
                                    floatingPanel.evaluateJavascript("window.onScreenCaptured('" + base64Image + "', '" + mode + "');", null);
                                });
                            } catch (Exception e) {
                                onError("Gagal encode gambar: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onError(final String errorMsg) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                floatingBubble.setVisibility(View.VISIBLE);
                                FloatingService.this.openPanel();
                                floatingPanel.evaluateJavascript("window.onCaptureError('" + errorMsg.replace("'", "\\'") + "');", null);
                            });
                        }
                    });
                }, 300);
            });
        }

        @JavascriptInterface
        public void fetchUrlContent(final String urlString) {
            new Thread(() -> {
                try {
                    String targetUrl = urlString.trim();
                    // Konversi link Google Docs ke export format txt
                    java.util.regex.Pattern docPattern = java.util.regex.Pattern.compile("/document/d/([a-zA-Z0-9-_]+)");
                    java.util.regex.Matcher matcher = docPattern.matcher(targetUrl);
                    if (matcher.find()) {
                        String docId = matcher.group(1);
                        targetUrl = "https://docs.google.com/document/d/" + docId + "/export?format=txt";
                    }

                    java.net.URL url = new java.net.URL(targetUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307) {
                        String redirectUrl = conn.getHeaderField("Location");
                        if (redirectUrl != null) {
                            conn = (java.net.HttpURLConnection) new java.net.URL(redirectUrl).openConnection();
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0");
                        }
                    }

                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                        if (sb.length() > 60000) break;
                    }
                    reader.close();

                    final String rawText = sb.toString();
                    if (rawText.trim().isEmpty()) {
                        throw new Exception("Dokumen kosong atau memerlukan login akun Google.");
                    }
                    if (rawText.contains("<html") && (rawText.contains("accounts.google.com") || rawText.contains("ServiceLogin") || rawText.contains("Sign in") || rawText.contains("signin"))) {
                        throw new Exception("Dokumen ini masih DIPRIVAT (hanya pemilik yang bisa akses). Ubah akses link Google Docs menjadi 'Siapa saja yang memiliki link' (Anyone with the link), atau buka dokumen di layar HP lalu klik tombol 'SCAN DOKUMEN DI LAYAR'.");
                    }

                    final String base64Text = Base64.encodeToString(rawText.getBytes("UTF-8"), Base64.NO_WRAP);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        floatingPanel.evaluateJavascript("window.onUrlContentFetchedBase64('" + base64Text + "');", null);
                    });
                } catch (final Exception e) {
                    final String err = e.getMessage() != null ? e.getMessage() : "Gagal mengambil isi link.";
                    new Handler(Looper.getMainLooper()).post(() -> {
                        floatingPanel.evaluateJavascript("window.onUrlFetchError('" + err.replace("'", "\\'") + "');", null);
                    });
                }
            }).start();
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
                    openAccessibilitySettings();
                });
            }
        }

        @JavascriptInterface
        public void openBrowserUrl(final String url) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(FloatingService.this, "Gagal membuka browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void createPublicDocLink(final String content) {
            new Thread(() -> {
                String publicUrl = null;
                try {
                    java.net.URL url = new java.net.URL("https://paste.rs");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    byte[] postData = content.getBytes("UTF-8");
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(postData);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200 || code == 201 || code == 206) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        publicUrl = reader.readLine();
                        reader.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error upload paste.rs: " + e.getMessage());
                }

                if (publicUrl == null || !publicUrl.startsWith("http")) {
                    try {
                        java.net.URL url = new java.net.URL("https://bytebin.lucko.me/post");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);

                        byte[] postData = content.getBytes("UTF-8");
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            os.write(postData);
                        }

                        int code = conn.getResponseCode();
                        if (code == 201 || code == 200) {
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close();
                            org.json.JSONObject obj = new org.json.JSONObject(sb.toString());
                            if (obj.has("key")) {
                                publicUrl = "https://bytebin.lucko.me/" + obj.getString("key");
                            }
                        }
                    } catch (Exception e2) {
                        Log.e(TAG, "Error upload bytebin: " + e2.getMessage());
                    }
                }

                final String finalUrl = (publicUrl != null && publicUrl.startsWith("http")) ? publicUrl.trim() : "";
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!finalUrl.isEmpty()) {
                        try {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Link Jawaban Tugas", finalUrl);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(FloatingService.this, "📋 Link jawaban baru berhasil disalin ke Clipboard!", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {}
                    }
                    floatingPanel.evaluateJavascript("window.onPublicDocLinkCreated('" + finalUrl.replace("'", "\\'") + "');", null);
                });
            }).start();
        }

        @JavascriptInterface
        public void sendGeminiRequest(final String promptText, final String base64Image, final String apiKey, final String requestId) {
            new Thread(() -> {
                String cleanKey = apiKey != null ? apiKey.trim().replace("\"", "").replace("'", "") : "";
                if (cleanKey.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        floatingPanel.evaluateJavascript("window.onGeminiError('" + requestId + "', 'API Key kosong. Masukkan Gemini API Key di ikon ⚙️.');", null);
                    });
                    return;
                }

                String[] candidateModels = new String[]{
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-2.0-flash",
                    "gemini-1.5-flash"
                };

                String lastError = "Gagal menghubungi server Google Gemini.";

                for (String model : candidateModels) {
                    try {
                        String urlString = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + cleanKey;
                        java.net.URL url = new java.net.URL(urlString);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        conn.setConnectTimeout(25000);
                        conn.setReadTimeout(45000);

                        JSONObject payload = new JSONObject();
                        JSONArray contents = new JSONArray();
                        JSONObject contentObj = new JSONObject();
                        JSONArray parts = new JSONArray();

                        if (promptText != null && !promptText.isEmpty()) {
                            JSONObject textPart = new JSONObject();
                            textPart.put("text", promptText);
                            parts.put(textPart);
                        }

                        if (base64Image != null && !base64Image.isEmpty()) {
                            JSONObject imagePart = new JSONObject();
                            JSONObject inlineData = new JSONObject();
                            inlineData.put("mimeType", "image/jpeg");
                            inlineData.put("data", base64Image);
                            imagePart.put("inlineData", inlineData);
                            parts.put(imagePart);
                        }

                        contentObj.put("parts", parts);
                        contents.put(contentObj);
                        payload.put("contents", contents);

                        byte[] out = payload.toString().getBytes("UTF-8");
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            os.write(out);
                        }

                        int statusCode = conn.getResponseCode();
                        if (statusCode == 200) {
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();

                            JSONObject responseObj = new JSONObject(sb.toString());
                            JSONArray candidates = responseObj.optJSONArray("candidates");
                            if (candidates != null && candidates.length() > 0) {
                                JSONObject firstCandidate = candidates.getJSONObject(0);
                                JSONObject content = firstCandidate.optJSONObject("content");
                                if (content != null) {
                                    JSONArray resParts = content.optJSONArray("parts");
                                    if (resParts != null && resParts.length() > 0) {
                                        String generatedText = resParts.getJSONObject(0).optString("text", "");
                                        final String b64Text = Base64.encodeToString(generatedText.getBytes("UTF-8"), Base64.NO_WRAP);
                                        new Handler(Looper.getMainLooper()).post(() -> {
                                            floatingPanel.evaluateJavascript("window.onGeminiSuccess('" + requestId + "', '" + b64Text + "');", null);
                                        });
                                        return;
                                    }
                                }
                            }
                        } else {
                            java.io.InputStream es = conn.getErrorStream();
                            if (es != null) {
                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(es, "UTF-8"));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) sb.append(line);
                                reader.close();
                                lastError = "HTTP " + statusCode + ": " + sb.toString();
                            } else {
                                lastError = "HTTP " + statusCode;
                            }
                            Log.w(TAG, "Model " + model + " error: " + lastError);
                        }
                    } catch (Exception e) {
                        lastError = e.getMessage() != null ? e.getMessage() : e.toString();
                        Log.e(TAG, "Exception calling Gemini with " + model + ": " + lastError);
                    }
                }

                final String finalError = lastError;
                new Handler(Looper.getMainLooper()).post(() -> {
                    floatingPanel.evaluateJavascript("window.onGeminiError('" + requestId + "', '" + finalError.replace("'", "\\'").replace("\n", " ").replace("\r", "") + "');", null);
                });
            }).start();
        }
    }
}
