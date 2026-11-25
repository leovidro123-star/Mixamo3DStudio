package com.mixamostudio.openworld;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQ = 1001;
    private static final int LOCAL_FBX_IMPORT_REQ = 1002;
    private static final String VIRTUAL_HOST = "local.mixamo3d.app";
    private static final String MIXAMO_URL = "https://www.mixamo.com/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private FrameLayout rootLayout;
    private WebView worldWebView;
    private WebView mixamoWebView;

    private ValueCallback<Uri[]> fileUploadCallback;
    private File charactersRootDir;
    private String touchBridgeJs = "";
    private boolean isMixamoOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterFullscreen();

        charactersRootDir = new File(getFilesDir(), "characters");
        if (!charactersRootDir.exists()) charactersRootDir.mkdirs();

        touchBridgeJs = readAssetAsString("mixamo_touch_bridge.js");

        rootLayout = new FrameLayout(this);
        setContentView(rootLayout);

        setupWorldWebView();
        setupMixamoWebView();

        rootLayout.addView(worldWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(mixamoWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        showWorld3D();
    }

    private void enterFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    // =========================================================================
    // 1. WEBVIEW DO MUNDO ABERTO 3D (THREE.JS)
    // =========================================================================
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWorldWebView() {
        worldWebView = new WebView(this);
        WebSettings ws = worldWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        worldWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        worldWebView.addJavascriptInterface(new WorldNativeBridge(), "AndroidWorld");

        worldWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (VIRTUAL_HOST.equals(uri.getHost())) {
                    return serveVirtualRequest(uri);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        worldWebView.loadUrl("https://" + VIRTUAL_HOST + "/app/world3d.html");
    }

    private WebResourceResponse serveVirtualRequest(Uri uri) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");

        try {
            String path = uri.getPath();
            if (path == null) return null;

            if (path.equals("/app/world3d.html")) {
                InputStream is = getAssets().open("world3d.html");
                return new WebResourceResponse("text/html", "UTF-8", 200, "OK", headers, is);
            }

            if (path.startsWith("/files/")) {
                String relative = URLDecoder.decode(path.substring("/files/".length()), "UTF-8");
                File target = new File(charactersRootDir, relative);
                if (!target.getCanonicalPath().startsWith(charactersRootDir.getCanonicalPath())) {
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", headers, null);
                }
                if (target.exists() && target.isFile()) {
                    String mime = relative.endsWith(".json") ? "application/json" : "application/octet-stream";
                    return new WebResourceResponse(mime, null, 200, "OK", headers, new FileInputStream(target));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", headers, null);
    }

    // =========================================================================
    // 2. WEBVIEW FULLSCREEN DO MIXAMO
    // =========================================================================
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupMixamoWebView() {
        mixamoWebView = new WebView(this);
        WebSettings ws = mixamoWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setSupportMultipleWindows(false);
        ws.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(mixamoWebView, true);

        mixamoWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mixamoWebView.addJavascriptInterface(new MixamoNativeBridge(), "MixamoNative");

        mixamoWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("mixamo.com")) {
                    view.evaluateJavascript(touchBridgeJs, null);
                }
            }
        });

        mixamoWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Escolher Personagem 3D"), FILE_CHOOSER_REQ);
                return true;
            }
        });

        mixamoWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                if (!filename.toLowerCase().endsWith(".fbx") && !filename.toLowerCase().endsWith(".zip")) {
                    filename = filename + ".fbx";
                }
                if (url.startsWith("http")) {
                    downloadFromHttpAndPrompt(url, filename);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQ && fileUploadCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        } else if (requestCode == LOCAL_FBX_IMPORT_REQ && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importUriAsFbx(data.getData());
        }
    }

    private void importUriAsFbx(Uri uri) {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                String name = getDisplayName(uri);
                byte[] bytes = baos.toByteArray();
                runOnUiThread(() -> showSaveFbxDialog(bytes, name));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Erro ao importar arquivo local", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getDisplayName(Uri uri) {
        String name = "modelo_" + System.currentTimeMillis() + ".fbx";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        return name;
    }

    // =========================================================================
    // 3. CAPTURA E ORGANIZAÇÃO DOS DOWNLOADS FBX
    // =========================================================================
    private void downloadFromHttpAndPrompt(String fileUrl, String suggestedName) {
        runOnUiThread(() -> Toast.makeText(this, "Baixando " + suggestedName + "...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", DESKTOP_UA);
                String cookies = CookieManager.getInstance().getCookie(fileUrl);
                if (cookies != null) conn.setRequestProperty("Cookie", cookies);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                    byte[] buf = new byte[16384];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        baos.write(buf, 0, read);
                    }
                }
                byte[] fileBytes = baos.toByteArray();
                runOnUiThread(() -> showSaveFbxDialog(fileBytes, suggestedName));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Falha ao baixar FBX: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showSaveFbxDialog(byte[] fileBytes, String suggestedName) {
        String cleanName = sanitizeName(suggestedName.replace(".fbx", "").replace(".FBX", ""));
        String[] options = new String[]{
                "🧍 Salvar como NOVO Personagem (With Skin)",
                "🏃 Adicionar como MOVIMENTO a um personagem (Without Skin)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Arquivo FBX Capturado (" + suggestedName + ")")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        promptNewCharacterName(fileBytes, cleanName);
                    } else {
                        promptSelectCharacterForAnim(fileBytes, cleanName);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void promptNewCharacterName(byte[] fileBytes, String defaultName) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(defaultName);

        new AlertDialog.Builder(this)
                .setTitle("Nome do Novo Personagem")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    String charName = sanitizeName(input.getText().toString().trim());
                    if (charName.isEmpty()) charName = "Personagem_" + System.currentTimeMillis();

                    File charDir = new File(charactersRootDir, charName);
                    File animDir = new File(charDir, "animations");
                    animDir.mkdirs();

                    if (writeBytes(new File(charDir, "model.fbx"), fileBytes)) {
                        Toast.makeText(this, "Personagem '" + charName + "' salvo!", Toast.LENGTH_SHORT).show();
                        notifyWorldLibraryUpdated(charName);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void promptSelectCharacterForAnim(byte[] fileBytes, String defaultAnimName) {
        List<String> chars = listCharacterNames();
        if (chars.isEmpty()) {
            Toast.makeText(this, "Nenhum personagem salvo ainda. Salve primeiro um com pele (With Skin).", Toast.LENGTH_LONG).show();
            promptNewCharacterName(fileBytes, defaultAnimName);
            return;
        }

        String[] items = chars.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Escolha o Personagem")
                .setItems(items, (dialog, which) -> {
                    String selectedChar = items[which];
                    promptAnimationName(selectedChar, fileBytes, defaultAnimName);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void promptAnimationName(String charName, byte[] fileBytes, String defaultAnimName) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Ex: idle, walk, run, jump, capoeira");
        input.setText(defaultAnimName);

        new AlertDialog.Builder(this)
                .setTitle("Nome do Movimento para " + charName)
                .setView(input)
                .setPositiveButton("Salvar Movimento", (d, w) -> {
                    String animName = sanitizeName(input.getText().toString().trim());
                    if (animName.isEmpty()) animName = "mov_" + System.currentTimeMillis();

                    File animDir = new File(new File(charactersRootDir, charName), "animations");
                    animDir.mkdirs();

                    if (writeBytes(new File(animDir, animName + ".fbx"), fileBytes)) {
                        Toast.makeText(this, "Movimento '" + animName + "' adicionado a " + charName + "!", Toast.LENGTH_SHORT).show();
                        notifyWorldLibraryUpdated(charName);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void notifyWorldLibraryUpdated(String activeCharName) {
        runOnUiThread(() -> {
            String js = "if(window.onNativeLibraryUpdated){window.onNativeLibraryUpdated(" +
                    JSONObject.quote(activeCharName) + ");}";
            worldWebView.evaluateJavascript(js, null);
        });
    }

    // =========================================================================
    // 4. NAVEGAÇÃO ENTRE MIXAMO E MUNDO 3D
    // =========================================================================
    private void showMixamoPortal() {
        isMixamoOpen = true;
        if (mixamoWebView.getUrl() == null) {
            mixamoWebView.loadUrl(MIXAMO_URL);
        }
        mixamoWebView.setVisibility(View.VISIBLE);
        worldWebView.setVisibility(View.GONE);
    }

    private void showWorld3D() {
        isMixamoOpen = false;
        mixamoWebView.setVisibility(View.GONE);
        worldWebView.setVisibility(View.VISIBLE);
        worldWebView.evaluateJavascript("if(window.refreshLibrary){window.refreshLibrary();}", null);
    }

    @Override
    public void onBackPressed() {
        if (isMixamoOpen) {
            if (mixamoWebView.canGoBack()) {
                mixamoWebView.goBack();
            } else {
                showWorld3D();
            }
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================================
    // 5. PONTES JAVASCRIPT
    // =========================================================================
    private class MixamoNativeBridge {
        @JavascriptInterface
        public void openWorld3D() {
            runOnUiThread(MainActivity.this::showWorld3D);
        }

        @JavascriptInterface
        public void saveBlobDownload(String base64Data, String filename) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                runOnUiThread(() -> showSaveFbxDialog(bytes, filename));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class WorldNativeBridge {
        @JavascriptInterface
        public void openMixamo() {
            runOnUiThread(MainActivity.this::showMixamoPortal);
        }

        @JavascriptInterface
        public void importLocalFbx() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Importar arquivo .FBX"), LOCAL_FBX_IMPORT_REQ);
            });
        }

        @JavascriptInterface
        public String getCharactersJson() {
            JSONArray result = new JSONArray();
            try {
                for (String charName : listCharacterNames()) {
                    File charDir = new File(charactersRootDir, charName);
                    File modelFile = new File(charDir, "model.fbx");
                    if (!modelFile.exists()) continue;

                    JSONObject obj = new JSONObject();
                    obj.put("name", charName);
                    obj.put("modelUrl", "https://" + VIRTUAL_HOST + "/files/" + Uri.encode(charName) + "/model.fbx");

                    JSONArray anims = new JSONArray();
                    File animDir = new File(charDir, "animations");
                    if (animDir.exists() && animDir.listFiles() != null) {
                        File[] files = animDir.listFiles();
                        Arrays.sort(files);
                        for (File f : files) {
                            if (f.getName().toLowerCase().endsWith(".fbx")) {
                                JSONObject a = new JSONObject();
                                String clean = f.getName().substring(0, f.getName().length() - 4);
                                a.put("name", clean);
                                a.put("url", "https://" + VIRTUAL_HOST + "/files/" +
                                        Uri.encode(charName) + "/animations/" + Uri.encode(f.getName()));
                                anims.put(a);
                            }
                        }
                    }
                    obj.put("animations", anims);
                    result.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @JavascriptInterface
        public boolean deleteCharacter(String charName) {
            File charDir = new File(charactersRootDir, sanitizeName(charName));
            return deleteRecursive(charDir);
        }
    }

    // =========================================================================
    // UTILITÁRIOS DE ARQUIVO
    // =========================================================================
    private List<String> listCharacterNames() {
        List<String> list = new ArrayList<>();
        File[] dirs = charactersRootDir.listFiles();
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File d : dirs) {
                if (d.isDirectory() && new File(d, "model.fbx").exists()) {
                    list.add(d.getName());
                }
            }
        }
        return list;
    }

    private boolean writeBytes(File file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return file.delete();
    }

    private String sanitizeName(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
    }

    private String readAssetAsString(String assetName) {
        try (InputStream is = getAssets().open(assetName)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
