package com.codersanx.busview

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlinx.coroutines.*
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.codersanx.busview.utils.Route
import com.codersanx.busview.utils.network.GetUpdate
import android.widget.Toast
import com.codersanx.busview.main.GpsControl
import com.codersanx.busview.main.MapControl
import com.codersanx.busview.main.BusNetwork
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory


open class MainActivity : AppCompatActivity(), GetUpdate.UpdateCallback {
    private lateinit var sensorManager: SensorManager
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    lateinit var map: MapView
    lateinit var route: AutoCompleteTextView
    lateinit var sharedPreferences: SharedPreferences
    lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var gpsControl: GpsControl
    lateinit var mapControl: MapControl
    lateinit var busNetwork: BusNetwork

    val busMarkers = mutableListOf<Marker>()
    val routeCoordinates = mutableListOf<GeoPoint>()
    val routes: MutableList<Route> = mutableListOf()
    val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    var currentUserMarker: Marker? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        gpsControl = GpsControl(fusedLocationClient, this)
        gpsControl.setupLocationUpdates()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        sharedPreferences = getSharedPreferences("Settings", MODE_PRIVATE)
        sharedPreferencesEditor = sharedPreferences.edit()

        val fetchData = GetUpdate("https://codersanx.netlify.app/api/appsn", this, this)
        fetchData.getUpdateInformation()

        map = findViewById(R.id.map)
        route = findViewById(R.id.currentBus)

        mapControl = MapControl(map, this)
        busNetwork = BusNetwork(map, this)

        coroutineScope.launch {
            val allRoutes = busNetwork.getRoutes(this@MainActivity)
            val names: MutableList<String> = mutableListOf()

            allRoutes.forEach { route ->
                val data = route.split("###")
                routes.add(Route(data[0], data[1], data[2]))
                names.add(data[0])
            }

            val adapter: ArrayAdapter<String> =
                ArrayAdapter<String>(this@MainActivity, R.layout.route_item, names)
            route.isFocusable = false
            route.isFocusableInTouchMode = false
            route.setAdapter(adapter)

            route.hint = "Choose route"

            setupMap()
            startBusUpdates()
        }

        val imageView: ImageView = findViewById(R.id.imageView2)
        imageView.setOnClickListener {
            if (currentUserMarker == null) {
                return@setOnClickListener
            }

            MapControl(map, this).centerMapOnLocation(currentUserMarker!!.position)
        }

        val settings: ImageView = findViewById(R.id.imageView3)
        settings.setOnClickListener {
            showSeekBarDialog()
        }

        route.setOnItemClickListener { _, _, _, _ ->
            initializeData()
            findViewById<TextView>(R.id.warning).visibility = View.GONE
            gpsControl.getUserLocation(false)
        }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(10.0)
        mapController.setCenter(GeoPoint(35.185566, 33.382276))
        gpsControl.getUserLocation()
    }

    private fun startBusUpdates() {
        coroutineScope.launch {
            while (isActive) {
                busNetwork.showBuses()
                delay(5000)
            }
        }
    }

    private fun initializeData() {
        coroutineScope.launch {
            map.overlays.clear()
            routeCoordinates.clear()
            busNetwork.loadStops()
            busNetwork.loadRoute()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSeekBarDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Choose speed value:")

        val currentSpeed = sharedPreferences.getInt("speed", 25)

        val valueTextView = TextView(this).apply {
            text = "Speed: $currentSpeed"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(35, 16, 0, 16)
            }
        }

        val seekBar = SeekBar(this).apply {
            max = 30
            progress = currentSpeed - 10
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueTextView.text = "Speed: ${progress + 10}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(valueTextView)
            addView(seekBar)
        }

        dialogBuilder.setView(layout)
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            val value = seekBar.progress
            sharedPreferencesEditor.putInt("speed", value + 10).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.borders)
        dialog.show()
    }

    fun promptUserToEnableLocation() {
        AlertDialog.Builder(this)
            .setMessage("GPS is off. Please enable it.")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                gpsControl.getUserLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required to show your location on the map",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager.registerListener(
            gpsControl.sensorEventListener,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        gpsControl.startLocationUpdates()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(gpsControl.sensorEventListener)
        gpsControl.stopLocationUpdates()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onUpdateReceived(update: Array<out String>) {
        val description = update[0]
        val link = update[1]

        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle("Update Available")
        builder.setMessage("Whats new?\n $description")
        builder.setPositiveButton("Update") { _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }

        if (update[3].toLong() - update[2].toLong() == 1L) {
            builder.setNegativeButton("Later") { dialogInterface, _ -> dialogInterface.dismiss() }
        }

        runOnUiThread { builder.show() }
    }
}
