package com.codersanx.busview.main

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.codersanx.busview.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint

class GpsControl(private val fusedLocationClient: FusedLocationProviderClient, private val activity: MainActivity) {
    private val locationCode = 1
    private var locationCallback: LocationCallback? = null
    lateinit var locationRequest: LocationRequest

    fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    val userGeoPoint = GeoPoint(it.latitude, it.longitude)
                    val bearing = activity.currentUserMarker?.rotation
                    activity.mapControl.updateUserMarker(userGeoPoint, bearing)
                }
            }
        }
    }

    fun getUserLocation(center: Boolean = true) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationCode
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    val bearing = location.bearing
                    activity.mapControl.addUserMarker(userLocation, bearing)

                    if (center) {
                        activity.mapControl.centerMapOnLocation(userLocation)
                    }
                } ?: run {
                    activity.promptUserToEnableLocation()
                }
            }
        }
    }

    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationCode
            )
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()

                // Update marker rotation based on bearing
                activity.currentUserMarker?.rotation = bearing
                activity.map.invalidate()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}