package com.muse.chatapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private volatile int serverPort = 8080;
    private ServerSocket serverSocket;
    private volatile boolean serverRunning = false;
    private byte[] cachedIndexHtml = null;

    private static class Msg {
        long id; long time; String sender; String text; String image; String timeStr;
    }
    private final List<Msg> messages = Collections.synchronizedList(new ArrayList<Msg>());
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadIndexHtml();
        startServer();
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        webView.addJavascriptInterface(new JsBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                Intent chooser = Intent.createChooser(intent, "Pilih Foto");
                try { startActivityForResult(chooser, FILE_CHOOSER_REQUEST); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });
        webView.loadUrl("http://127.0.0.1:" + serverPort + "/");
    }

    public class JsBridge {
        @JavascriptInterface public String getLocalIp() { return getWifiIp(); }
        @JavascriptInterface public int getPort() { return serverPort; }
    }

    private void loadIndexHtml() {
        try {
            InputStream is = getAssets().open("index.html");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            cachedIndexHtml = baos.toByteArray();
        } catch (Exception e) { cachedIndexHtml = "<h1>index.html tidak ditemukan</h1>".getBytes(StandardCharsets.UTF_8); }
    }

    private void startServer() {
        for (int p = 8080; p <= 8090; p++) {
            try { serverSocket = new ServerSocket(p); serverPort = p; serverRunning = true; break; }
            catch (Exception ignored) {}
        }
        if (!serverRunning) return;
        Thread t = new Thread(() -> {
            while (serverRunning) {
                try { Socket s = serverSocket.accept(); new Thread(() -> handleConnection(s)).start(); }
                catch (Exception e) { if (!serverRunning) break; }
            }
        });
        t.setDaemon(true); t.start();
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(15000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) { socket.close(); return; }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { socket.close(); return; }
            String method = parts[0];
            String rawPath = parts[1];
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String low = line.toLowerCase(Locale.US);
                if (low.startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) {}
                }
            }
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(buf, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                body = new String(buf, 0, read);
            }
            String path = rawPath; String query = "";
            int qi = rawPath.indexOf('?');
            if (qi >= 0) { path = rawPath.substring(0, qi); query = rawPath.substring(qi + 1); }
            if (method.equals("OPTIONS")) { sendResponse(out, 200, "text/plain", "OK".getBytes(StandardCharsets.UTF_8), true); socket.close(); return; }
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) { sendResponse(out, 200, "text/html; charset=utf-8", cachedIndexHtml, true); socket.close(); return; }
            if (method.equals("GET") && path.equals("/info")) {
                String json = "{\"isHost\":true,\"ip\":\"" + escapeJson(getWifiIp()) + "\",\"port\":" + serverPort + ",\"count\":" + messages.size() + "}";
                sendResponse(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), true); socket.close(); return;
            }
            if (method.equals("GET") && path.equals("/messages")) {
                long since = 0;
                try { for (String kv : query.split("&")) { if (kv.startsWith("since=")) since = Long.parseLong(URLDecoder.decode(kv.substring(6), "UTF-8")); } } catch (Exception ignored) {}
                StringBuilder sb = new StringBuilder(); sb.append("{\"messages\":["); boolean first = true;
                synchronized (messages) { for (Msg m : messages) { if (since != 0 && m.time <= since) continue; if (!first) sb.append(","); first = false; sb.append(msgToJson(m)); } }
                sb.append("]}");
                sendResponse(out, 200, "application/json; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8), true); socket.close(); return;
            }
            if (method.equals("POST") && path.equals("/send")) {
                String sender = extractJsonString(body, "sender"); String text = extractJsonString(body, "text"); String image = extractJsonString(body, "image");
                if (sender == null || sender.isEmpty()) sender = "Anonim";
                if (text == null) text = ""; if (image == null) image = "";
                if (text.length() > 4000) text = text.substring(0, 4000);
                if (image.length() > 2600000) { String err = "{\"ok\":false,\"error\":\"Foto terlalu besar, max ~2MB\"}"; sendResponse(out, 413, "application/json; charset=utf-8", err.getBytes(StandardCharsets.UTF_8), true); socket.close(); return; }
                if (text.isEmpty() && image.isEmpty()) { String err = "{\"ok\":false,\"error\":\"Pesan kosong\"}"; sendResponse(out, 400, "application/json; charset=utf-8", err.getBytes(StandardCharsets.UTF_8), true); socket.close(); return; }
                Msg m = new Msg(); m.id = nextId.getAndIncrement(); m.time = System.currentTimeMillis(); m.sender = sender; m.text = text; m.image = image; m.timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.time));
                messages.add(m);
                synchronized (messages) { while (messages.size() > 500) messages.remove(0); }
                String ok = "{\"ok\":true,\"id\":" + m.id + ",\"time\":" + m.time + "}";
                sendResponse(out, 200, "application/json; charset=utf-8", ok.getBytes(StandardCharsets.UTF_8), true); socket.close(); return;
            }
            String nf = "{\"error\":\"not_found\"}"; sendResponse(out, 404, "application/json; charset=utf-8", nf.getBytes(StandardCharsets.UTF_8), true); socket.close();
        } catch (Exception e) { try { socket.close(); } catch (Exception ignored) {} }
    }

    private void sendResponse(OutputStream out, int code, String contentType, byte[] body, boolean cors) throws Exception {
        String status = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 404 ? " Not Found" : code == 400 ? " Bad Request" : code == 413 ? " Too Large" : "") + "\r\n";
        String headers = "Content-Type: " + contentType + "\r\n" + "Content-Length: " + body.length + "\r\n" + "Connection: close\r\n";
        if (cors) headers += "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\n";
        headers += "\r\n";
        out.write(status.getBytes(StandardCharsets.UTF_8)); out.write(headers.getBytes(StandardCharsets.UTF_8)); out.write(body); out.flush();
    }

    private String msgToJson(Msg m) { return "{\"id\":" + m.id + ",\"sender\":\"" + escapeJson(m.sender) + "\",\"text\":\"" + escapeJson(m.text) + "\",\"image\":\"" + escapeJson(m.image) + "\",\"time\":" + m.time + ",\"timeStr\":\"" + escapeJson(m.timeStr) + "\"}"; }
    private static String escapeJson(String s) { if (s == null) return ""; return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private static String extractJsonString(String json, String key) {
        if (json == null) return null; String needle = "\"" + key + "\""; int idx = json.indexOf(needle); if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length()); if (colon < 0) return null; int i = colon + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) i++;
        if (i >= json.length() || json.charAt(i) != '"') { if (i + 4 <= json.length() && json.substring(i, i + 4).equals("null")) return null; return ""; }
        StringBuilder sb = new StringBuilder(); i++;
        while (i < json.length()) { char c = json.charAt(i); if (c == '\\' && i + 1 < json.length()) { char n = json.charAt(i + 1); if (n == '"') sb.append('"'); else if (n == '\\') sb.append('\\'); else if (n == '/') sb.append('/'); else if (n == 'n') sb.append('\n'); else if (n == 'r') sb.append('\r'); else if (n == 't') sb.append('\t'); else if (n == 'u' && i + 5 < json.length()) { try { sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16)); } catch (Exception ignored) {} i += 4; } else sb.append(n); i += 2; } else if (c == '"') break; else { sb.append(c); i++; } }
        return sb.toString();
    }

    private String getWifiIp() {
        try { WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); if (wm != null) { int ipInt = wm.getConnectionInfo().getIpAddress(); if (ipInt != 0) return (ipInt & 0xFF) + "." + ((ipInt >> 8) & 0xFF) + "." + ((ipInt >> 16) & 0xFF) + "." + ((ipInt >> 24) & 0xFF); } } catch (Exception ignored) {}
        try { Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); while (en.hasMoreElements()) { NetworkInterface ni = en.nextElement(); if (!ni.isUp() || ni.isLoopback()) continue; Enumeration<InetAddress> addrs = ni.getInetAddresses(); while (addrs.hasMoreElements()) { InetAddress a = addrs.nextElement(); if (a instanceof Inet4Address && !a.isLoopbackAddress()) { String ip = a.getHostAddress(); if (ip != null && !ip.startsWith("10.0.2.")) return ip; } } } } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == FILE_CHOOSER_REQUEST) { if (filePathCallback == null) return; Uri[] results = null; if (resultCode == RESULT_OK && data != null) { Uri uri = data.getData(); if (uri != null) results = new Uri[]{uri}; } filePathCallback.onReceiveValue(results); filePathCallback = null; } }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { super.onDestroy(); serverRunning = false; try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {} }
}
