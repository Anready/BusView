package com.codersanx.busview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private val handler = Handler(Looper.getMainLooper())
    private val markers = mutableListOf<Marker>()
    private val updateInterval = 5000L // Update every 5 seconds
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize OSMDroid
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osm_pref", MODE_PRIVATE))

        // Setup MapView
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)

        // Center map on Cyprus
        mapController = map.controller
        mapController.setZoom(10.0)
        val cyprus = GeoPoint(35.185566, 33.382276)
        mapController.setCenter(cyprus)

        // Start periodic update of bus locations
        handler.postDelayed(::updateBuses, updateInterval)
    }

    // Function to fetch bus data and update markers
    private fun updateBuses() {
        thread {
            try {
                val proxyUrl = "https://cors-anywhere.herokuapp.com/" // CORS proxy
                val apiUrl = "https://cyprusbus.info/api/buses"

                // Build the request
                val request = Request.Builder()
                    .url(apiUrl)
                    .build()

                // Execute the request
                val response: Response = client.newCall(request).execute()

                // Process the response if successful
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseData ->
                        runOnUiThread { displayBuses(responseData) }
                    }
                } else {
                    runOnUiThread {Toast.makeText(this, response.code, Toast.LENGTH_LONG).show()}
                    Log.e("MainActivity", "Error fetching bus data: ${response.code}")
                }

                // Close the response body
                response.close()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching bus data", e)
                runOnUiThread {Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()}
            }

            handler.postDelayed(::updateBuses, updateInterval) // Schedule the next update
        }
    }

    // Display bus locations on the map
    private fun displayBuses(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val buses = jsonObject.getJSONObject("Buses")

            // Remove existing markers
            markers.forEach { map.overlays.remove(it) }
            markers.clear()

            // Load bus icon

            // Add new markers
            buses.keys().forEach { key ->
                val bus = buses.getJSONObject(key)
                val latitude = bus.getDouble("Latitude")
                val longitude = bus.getDouble("Longitude")
                val label = bus.getString("Label")
                val routeShortName = bus.getString("RouteShortName")
                val routeLongName = bus.getString("RouteLongName")
                val speed = bus.getInt("SpeedKmPerHour")

                val busIconDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_android_black_24dp, theme)



                // Create and add a marker with a custom icon
                val marker = Marker(map)
                //marker.icon = busIconDrawable
                marker.position = GeoPoint(latitude, longitude)
                marker.title = "Bus $label"
                marker.snippet = "Route: $routeShortName - $routeLongName Speed: $speed"
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Set custom icon if available

// Set the Drawable icon
                // Add marker to map and list
                map.overlays.add(marker)
                markers.add(marker)
            }
            map.invalidate() // Refresh the map to display the new markers
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing bus data", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null) // Stop the updates when activity is destroyed
        super.onDestroy()
    }
}
