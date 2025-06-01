package com.codersanx.busview

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.codersanx.busview.network.GetUpdate
import java.util.Locale

open class MainActivity : AppCompatActivity(), GetUpdate.UpdateCallback {
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)

        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Apply language
        val codes = arrayOf("en", "ru", "uk", "el")
        updateLocale(codes[sharedPreferences.getInt("language_index", 0)])

        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.activity_main)

        requestedOrientation = resources.configuration.orientation

        val fetchData = GetUpdate(
            "https://codersanx.netlify.app/api/appsn",
            this,
            this
        )
        fetchData.getUpdateInformation()

        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, true, false)
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        webView.loadUrl("https://limassolbus.online")

    }

    private fun updateLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onUpdateReceived(update: Array<out String>) {
        val description = update[0]
        val link = update[1]

        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(getString(R.string.update_available))
        builder.setMessage(getString(R.string.whats_new, description))
        builder.setPositiveButton(getString(R.string.update)) { _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }

        if (update[3].toLong() - update[2].toLong() == 1L) {
            builder.setNegativeButton(getString(R.string.later)) { dialogInterface, _ -> dialogInterface.dismiss() }
        }

        runOnUiThread {
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.borders)
            dialog.show()
        }
    }
}
