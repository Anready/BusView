package com.codersanx.busview;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.codersanx.busview.network.GetUpdate;

import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements GetUpdate.UpdateCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        SharedPreferences sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE);

        String[] codes = {"en", "ru", "uk", "el"};
        updateLocale(codes[sharedPreferences.getInt("language_index", 0)]);

        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() |
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        setContentView(R.layout.activity_main);

        setRequestedOrientation(getResources().getConfiguration().orientation);

        GetUpdate fetchData = new GetUpdate(
                "https://codersanx.netlify.app/api/appsn",
                this,
                this
        );
        fetchData.getUpdateInformation();

        WebView webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://")) && url.contains("limassolbus.online")) {
                    view.loadUrl(url);
                    return true;
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        webView.loadUrl("https://limassolbus.online");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = findViewById(R.id.webview);
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void updateLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        android.content.res.Configuration config = getResources().getConfiguration();
        config.setLocale(locale);

        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    @Override
    public void onUpdateReceived(String[] update) {
        String description = update[0];
        String link = update[1];

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(getString(R.string.update_available));
        builder.setMessage(getString(R.string.whats_new, description));
        builder.setPositiveButton(getString(R.string.update), (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))));

        if (Long.parseLong(update[3]) - Long.parseLong(update[2]) == 1L) {
            builder.setNegativeButton(getString(R.string.later), (dialogInterface, i) -> dialogInterface.dismiss());
        }

        runOnUiThread(() -> {
            AlertDialog dialog = builder.create();
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(R.drawable.borders);
            dialog.show();
        });
    }
}