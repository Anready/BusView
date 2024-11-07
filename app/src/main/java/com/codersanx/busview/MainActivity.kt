package com.codersanx.busview

import android.content.Context
import com.codersanx.busview.utils.network.Network
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.*
import org.json.JSONArray
import android.preference.PreferenceManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.codersanx.busview.utils.Route
import com.codersanx.busview.utils.network.GetUpdate
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.codersanx.busview.utils.Bus
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class MainActivity : AppCompatActivity(), GetUpdate.UpdateCallback {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var map: MapView
    private lateinit var route: AutoCompleteTextView
    private lateinit var sensorManager: SensorManager
    private val busMarkers = mutableListOf<Marker>()
    var selectedStopMarker: Marker? = null
    var currentUserMarker: Marker? = null
    private val stopMarkers = mutableListOf<Marker>()
    private var routeLine: Polyline? = null
    private val routeCoordinates = mutableListOf<GeoPoint>()
    private val client = OkHttpClient()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val bottomSheetFragment = ShowTimeBuses()
    private val routes: MutableList<Route> = mutableListOf()
    private var currentBusMarker: Marker? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null

    private fun setupLocationUpdates() {
        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create a LocationRequest for real-time updates
        locationRequest = LocationRequest.create().apply {
            interval = 5000  // Update every 5 seconds (adjust as needed)
            fastestInterval = 2000  // Fastest update rate
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Initialize the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    val userGeoPoint = GeoPoint(it.latitude, it.longitude)
                    val bearing = it.bearing
                    updateUserMarker(userGeoPoint, bearing) // Update the marker with new location
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setupLocationUpdates()

        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        val fetchData = GetUpdate("https://codersanx.netlify.app/api/appsn", this, this)
        fetchData.getUpdateInformation()

        map = findViewById(R.id.map)
        route = findViewById(R.id.currentBus)

        coroutineScope.launch {
            val allRoutes = Network().getRoutes()
            val names: MutableList<String> = mutableListOf()

            allRoutes.forEach { route ->
                val data = route.split("###")
                routes.add(Route(data[0], data[1], data[2]))
                names.add(data[0])
            }

            val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this@MainActivity, R.layout.route_item, names)
            route.isFocusable = false
            route.isFocusableInTouchMode = false
            route.setAdapter(adapter)

           // route.setText(adapter.getItem(0))
            route.hint = "Choose route"

            setupMap()
            initializeData()
            startBusUpdates()
        }

        val imageView: ImageView = findViewById(R.id.imageView2)
        imageView.setOnClickListener {
            centerMapOnLocation(currentUserMarker!!.position)
        }

        route.setOnItemClickListener { _, _, _, _ ->
            initializeData()
        }
    }

    private fun updateUserMarker(location: GeoPoint, bearing: Float) {
        if (currentUserMarker == null) {
            // Initialize the marker if it doesn't already exist
            currentUserMarker = Marker(map).apply {
                title = "You are here"
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                map.overlays.add(this)  // Add the marker to the map overlays
            }
        }

        // Update position and bearing of the existing marker
        currentUserMarker?.apply {
            position = location
            rotation = bearing
        }
        map.invalidate()  // Refresh the map to show the updated marker
    }

    private fun getUserLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    val bearing = location.bearing
                    addUserMarker(userLocation, bearing)
                    centerMapOnLocation(userLocation)
                } ?: run {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun centerMapOnLocation(location: GeoPoint) {
        val mapController = map.controller
        mapController.setCenter(location)
        mapController.setZoom(15.0)  // Set to a reasonable zoom level, adjust as needed
    }


    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        val mapController = map.controller
        mapController.setZoom(10.0)
        mapController.setCenter(GeoPoint(35.185566, 33.382276))
        getUserLocation()
    }

    private fun initializeData() {
        coroutineScope.launch {
            map.overlays.clear()
            getUserLocation()
            routeCoordinates.clear()
            loadStops()
            loadRoute()
        }
    }

    private suspend fun loadStops() = withContext(Dispatchers.IO) {
        println("https://raw.githubusercontent.com/Anready/anready.github.io/refs/heads/main/${route.text.toString().replace(" ", "_")}stops.json")
        try {
            val request = Request.Builder()
                .url("${getLink(route.text.toString())}stops.json")
                .build()

            val response = client.newCall(request).execute()
            val stopsArray = JSONArray(response.body!!.string())

            withContext(Dispatchers.Main) {
                for (i in 0 until stopsArray.length()) {
                    val stop = stopsArray.getJSONObject(i)
                    val marker = Marker(map).apply {
                        position = GeoPoint(stop.getDouble("lat"), stop.getDouble("lng"))
                        title = stop.optString("name", "Stop ${i + 1}")
                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.bus_stop)
                        setOnMarkerClickListener { marker, _ ->
                            selectStop(marker)
                            true
                        }
                    }
                    stopMarkers.add(marker)
                    map.overlays.add(marker)
                }
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadRoute() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getLink(route.text.toString())}.json")
                .build()

            val response = client.newCall(request).execute()
            val routePointsArray = JSONArray(response.body!!.string())

            withContext(Dispatchers.Main) {
                for (i in 0 until routePointsArray.length()) {
                    val point = routePointsArray.getJSONObject(i)
                    routeCoordinates.add(GeoPoint(point.getDouble("lat"), point.getDouble("lng")))
                }

                routeLine = Polyline().apply {
                    setPoints(routeCoordinates)
                    color = Color.RED
                    width = 4f
                }
                map.overlays.add(routeLine)
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLink(name: String): String {
        routes.forEach { route ->
            if (route.label == name) {
                return route.routeLink
            }
        }

        return ""
    }

    private fun startBusUpdates() {
        coroutineScope.launch {
            while (isActive) {
                showBuses()
                delay(5000)
            }
        }
    }

    private suspend fun showBuses() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://cyprusbus.info/api/buses")
                .build()

            val response = client.newCall(request).execute()
            val busesJson = response.body?.string()?.let {
                JSONObject(it).getJSONObject("Buses")
            }

            withContext(Dispatchers.Main) {
                busMarkers.forEach { map.overlays.remove(it) }
                busMarkers.clear()

                busesJson?.keys()?.forEach { key ->
                    val bus = busesJson.getJSONObject(key)
                    if (bus.getString("RouteLongName") == getLongName(route.text.toString())) {
                        if (currentBusMarker == null || currentBusMarker?.title != "Bus ${bus.getString("Label")}") {
                            val busMarker = Marker(map).apply {
                                position = GeoPoint(bus.getDouble("Latitude"), bus.getDouble("Longitude"))
                                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.bus_map)
                                title = "Bus ${bus.getString("Label")}\nSpeed: ${bus.getDouble("SpeedKmPerHour")} km/h"
                                rotation = bus.getInt("Bearing").toFloat()

                                setOnMarkerClickListener { marker, _ ->
                                Toast.makeText(this@MainActivity, "Bus ${bus.getString("Label")}\nSpeed: ${bus.getDouble("SpeedKmPerHour")} km/h", Toast.LENGTH_SHORT).show()
                                  true
                                }
                            }
                            busMarkers.add(busMarker)
                            map.overlays.add(busMarker)
                            currentBusMarker = busMarker
                        } else {
                            currentBusMarker?.apply {
                                position = GeoPoint(bus.getDouble("Latitude"), bus.getDouble("Longitude"))
                                title = "Bus ${bus.getString("Label")}\nSpeed: ${bus.getDouble("SpeedKmPerHour")} km/h"
                                map.overlays.add(this)
                                setOnMarkerClickListener { marker, _ ->
                                Toast.makeText(this@MainActivity, "Bus ${bus.getString("Label")}\nSpeed: ${bus.getDouble("SpeedKmPerHour")} km/h", Toast.LENGTH_SHORT).show()
                                  true
                                }
                            }
                        }
                    }
                }

                calculateDistancesToStop()
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLongName(name: String): String {
        routes.forEach { route ->
            if (route.label == name) {
                return route.routLong
            }
        }

        return ""
    }

    private fun selectStop(marker: Marker) {
        selectedStopMarker?.icon = ContextCompat.getDrawable(this, R.drawable.bus_stop)
        selectedStopMarker = marker
        marker.icon = ContextCompat.getDrawable(this, R.drawable.bus_stop_selected)
        calculateDistancesToStop()
        map.invalidate()
    }

    private fun calculateDistancesToStop() {
        selectedStopMarker?.let { stopMarker ->
            val stopPoint = stopMarker.position
            val infoBuilder: MutableList<Bus> = mutableListOf()

            busMarkers.forEach { busMarker ->
                val busPoint = busMarker.position
                val routeDistance = calculateRouteDistance(stopPoint, busPoint)
                if (routeDistance == -1.0) {
                    return@forEach
                }

                val time = ((routeDistance / 35)*60).roundToInt()
                infoBuilder.add(Bus("${busMarker.title?.split("\n")?.get(0)}: $time min\n(${String.format("%.3f", routeDistance)} km)", busPoint))
            }

            val sortedInfo = infoBuilder.sortedBy {
                it.name.substringAfter(": ").substringBefore(" min").toInt()
            }

            val url = "geo:${selectedStopMarker!!.position.latitude},${selectedStopMarker!!.position.longitude}?q=${selectedStopMarker!!.position.latitude},${selectedStopMarker!!.position.longitude}(${selectedStopMarker!!.title})"
            bottomSheetFragment.updateView(sortedInfo, selectedStopMarker!!.title, Uri.parse(url))

            if (!bottomSheetFragment.isAdded && !bottomSheetFragment.isVisible) {
                bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
            }
        }
    }

    private fun calculateRouteDistance(startPoint: GeoPoint, endPoint: GeoPoint): Double {
        val nearestStartIndex = findNearestPointIndex(startPoint, routeCoordinates)
        val nearestEndIndex = findNearestPointIndex(endPoint, routeCoordinates)

        if (nearestStartIndex < nearestEndIndex) {
            return -1.0
        }

        val startIndex = minOf(nearestStartIndex, nearestEndIndex)
        val endIndex = maxOf(nearestStartIndex, nearestEndIndex)

        var totalDistance = 0.0
        for (i in startIndex until endIndex) {
            totalDistance += calculateDistance(
                routeCoordinates[i].latitude, routeCoordinates[i].longitude,
                routeCoordinates[i + 1].latitude, routeCoordinates[i + 1].longitude
            )
        }
        return totalDistance
    }

    private fun findNearestPointIndex(point: GeoPoint, routeCoordinates: List<GeoPoint>): Int {
        return routeCoordinates.indices.minByOrNull {
            calculateDistance(point.latitude, point.longitude, routeCoordinates[it].latitude, routeCoordinates[it].longitude)
        } ?: 0
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun addUserMarker(location: GeoPoint, bearing: Float) {
        val userMarker = Marker(map).apply {
            position = location
            title = "You are here"
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location)
            rotation = bearing  // Rotate the marker
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)  // Set the rotation pivot
        }

        if (currentUserMarker != null) {
            map.overlays?.remove(currentUserMarker)
        }

        currentUserMarker = userMarker
        map.overlays.add(userMarker)
        map.controller.setCenter(location)
        map.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getUserLocation()
            } else {
                Toast.makeText(this, "Location permission is required to show your location on the map", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()

                // Update marker rotation based on bearing
                currentUserMarker?.rotation = bearing
                map.invalidate()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    override fun onResume() {
        super.onResume()
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        startLocationUpdates()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
        stopLocationUpdates()
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
