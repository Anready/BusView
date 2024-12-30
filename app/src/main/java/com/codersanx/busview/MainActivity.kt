package com.codersanx.busview

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.codersanx.busview.adapters.SelectRouteAdapter
import com.codersanx.busview.buses.Emulation
import com.codersanx.busview.main.BusNetwork
import com.codersanx.busview.main.GpsControl
import com.codersanx.busview.main.MapControl
import com.codersanx.busview.models.Route
import com.codersanx.busview.network.GetUpdate
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale


open class MainActivity : AppCompatActivity(), GetUpdate.UpdateCallback {
    private lateinit var sensorManager: SensorManager
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var gpsControl: GpsControl
    private lateinit var busNetwork: BusNetwork
    private lateinit var emulated: Emulation

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    lateinit var map: MapView
    lateinit var route: AutoCompleteTextView
    lateinit var sharedPreferences: SharedPreferences
    lateinit var centerOnStop: FloatingActionButton
    lateinit var mapControl: MapControl
    lateinit var btnRealtime: RadioButton
    lateinit var btnEmulated: RadioButton

    val busMarkers = mutableListOf<Marker>()
    val routeCoordinates = mutableListOf<GeoPoint>()
    val routes: MutableList<Route> = mutableListOf()

    var currentUserMarker: Marker? = null

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

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        route = findViewById(R.id.currentBus)
        centerOnStop = findViewById(R.id.fab)

        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sharedPreferencesEditor = sharedPreferences.edit()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapControl = MapControl(map, this)
        busNetwork = BusNetwork(map, this)
        gpsControl = GpsControl(fusedLocationClient, this)
        gpsControl.setupLocationUpdates()

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            promptUserToEnableLocation()
        }

        btnRealtime = findViewById(R.id.btn_realtime)
        btnEmulated = findViewById(R.id.btn_emulated)

        btnRealtime.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnRealtime.elevation = 4f
                btnEmulated.elevation = 2f
            } else {
                btnRealtime.elevation = 2f
                btnEmulated.elevation = 4f
            }
        }

        btnEmulated.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnEmulated.elevation = 4f
                btnRealtime.elevation = 2f
            } else {
                btnEmulated.elevation = 2f
                btnRealtime.elevation = 4f
            }
        }

        requestedOrientation = resources.configuration.orientation
        emulated = Emulation(this)

        val fetchData = GetUpdate(
            "https://codersanx.netlify.app/api/appsn",
            this,
            this
        )
        fetchData.getUpdateInformation()

        coroutineScope.launch {
            val allRoutes = busNetwork.getRoutes(this@MainActivity)
            val names: MutableList<String> = mutableListOf()

            allRoutes.forEach { route ->
                val data = route.split("###")
                routes.add(Route(data[0], data[1], data[2]))
                names.add(data[0])
            }

            val adapter = SelectRouteAdapter(this@MainActivity, names, sharedPreferences)
            adapter.sortByStarred()

            route.isFocusable = false
            route.isFocusableInTouchMode = false
            route.setAdapter(adapter)

            val screenHeight = resources.displayMetrics.heightPixels
            val dropdownHeight = (screenHeight * 0.40).toInt()

            route.dropDownHeight = dropdownHeight
            route.hint = getString(R.string.choose_route)

            setupMap()
            startBusUpdates()
        }

        val floatingActionButton: FloatingActionButton = findViewById(R.id.floatingActionButton)
        floatingActionButton.setOnClickListener {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                promptUserToEnableLocation()
                return@setOnClickListener
            }

            if (currentUserMarker == null) {
                return@setOnClickListener
            }

            MapControl(map, this).centerMapOnLocation(currentUserMarker!!.position)
        }

        val settings: FloatingActionButton = findViewById(R.id.floatingSettings)
        settings.setOnClickListener {
            showSeekBarDialog()
        }

        route.setOnItemClickListener { _, _, _, _ ->
            initializeData()
            findViewById<TextView>(R.id.warning).visibility = View.GONE
            centerOnStop.visibility = View.INVISIBLE
            gpsControl.getUserLocation(false)
        }

        centerOnStop.setOnClickListener {
            if (mapControl.selectedStopMarker != null) {
                mapControl.centerMapOnLocation(mapControl.selectedStopMarker!!.position)
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
                if (btnRealtime.isChecked) {
                    busNetwork.showBuses(true, null)
                } else {
                    busNetwork.showBuses(false, emulated)
                }

                delay(5000)
            }
        }
    }

    private fun showSeekBarDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(getString(R.string.settings))

        val currentSpeed = sharedPreferences.getInt("speed", 25)
        val currentPercent = sharedPreferences.getInt("percent", 45)

        val valueTextView = TextView(this).apply {
            text = getString(R.string.current_speed, currentSpeed)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(55, 36, 0, 16)
            }
        }

        val seekBar = SeekBar(this).apply {
            max = 30
            progress = currentSpeed - 10
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(15, 16, 15, 16)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueTextView.text = getString(R.string.current_speed, progress + 10)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        val valuePercent = TextView(this).apply {
            text = getString(R.string.percent, currentPercent)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(55, 36, 0, 16)
            }
        }

        val seekBarPercent = SeekBar(this).apply {
            max = 60
            progress = currentPercent - 20
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(15, 16, 15, 16)
            }
        }

        seekBarPercent.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valuePercent.text = getString(R.string.percent, progress + 20)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val themeSwitch = SwitchMaterial(this).apply {
            text = getString(R.string.dark_mode)
            textSize = 16f
            isChecked = sharedPreferences.getBoolean("dark_mode", false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(55, 16, 55, 16)
            }
        }

        themeSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondary))

        val languages = arrayOf("English", "Русский", "Українська", "Ελληνικά")
        val codes = arrayOf("en", "ru", "uk", "el")

        val languageChooser = Spinner(this).apply {
            val adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                languages
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(sharedPreferences.getInt("language_index", 0))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(35, 16, 35, 16)
            }
        }

        val version = TextView(this).apply {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            text = getString(R.string.version, packageInfo.versionName)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(55, 36, 0, 16)
            }
        }


        val checkUpdate = Button(this).apply {
            text = getString(R.string.check_updates)
            background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.borders_inverse)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            isAllCaps = false
            setOnClickListener {
                val fetchData = GetUpdate(
                    "https://codersanx.netlify.app/api/appsn",
                    this@MainActivity,
                    this@MainActivity
                )
                fetchData.getUpdateInformation()
                Toast.makeText(this@MainActivity, "Checking for updates...", Toast.LENGTH_SHORT).show()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(55, 36, 55, 16)
            }
        }

        val lay = ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(valueTextView)
                addView(seekBar)
                addView(valuePercent)
                addView(seekBarPercent)
                addView(themeSwitch)
                addView(languageChooser)
                addView(version)
                addView(checkUpdate)
            })
        }

        dialogBuilder.setView(lay)
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            var wasChanged = false
            val value = seekBar.progress
            sharedPreferencesEditor.putInt("speed", value + 10).apply()

            val percent = seekBarPercent.progress
            sharedPreferencesEditor.putInt("percent", percent + 20).apply()

            if (sharedPreferences.getInt("language_index", 0) != languageChooser.selectedItemPosition) {
                sharedPreferencesEditor.putInt("language_index", languageChooser.selectedItemPosition).apply()
                updateLocale(codes[languageChooser.selectedItemPosition])
                wasChanged = true
            }

            if (sharedPreferences.getBoolean("dark_mode", false) != themeSwitch.isChecked){
                sharedPreferencesEditor.putBoolean("dark_mode", themeSwitch.isChecked).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (themeSwitch.isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                wasChanged = true
            }

            if (wasChanged) {
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }

            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.borders)
        dialog.show()
    }

    private fun updateLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun promptUserToEnableLocation() {
        val builder = AlertDialog.Builder(this)
            .setMessage(getString(R.string.enable_location))
            .setPositiveButton(getString(R.string.enable)) { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(getString(R.string.cancel), null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.borders)
        dialog.show()
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
                    getString(R.string.give_permission),
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
