package com.codersanx.busview.main

import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import com.codersanx.busview.MainActivity
import com.codersanx.busview.R
import com.codersanx.busview.ShowTimeBuses
import com.codersanx.busview.models.Bus
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MapControl(private val map: MapView, private val activity: MainActivity) {
    private val bottomSheetFragment = ShowTimeBuses()
    var selectedStopMarker: Marker? = null

    fun selectStop(marker: Marker) {
        selectedStopMarker?.icon = ContextCompat.getDrawable(activity, R.drawable.bus_stop)
        selectedStopMarker = marker
        marker.icon = ContextCompat.getDrawable(activity, R.drawable.bus_stop_selected)
        calculateDistancesToStop()
        bottomSheetFragment.show(activity.supportFragmentManager, bottomSheetFragment.tag)
        activity.centerOnStop.visibility = View.VISIBLE
        map.invalidate()
    }

    fun calculateDistancesToStop() {
        selectedStopMarker?.let { stopMarker ->
            val stopPoint = stopMarker.position
            val infoBuilder: MutableList<Bus> = mutableListOf()

            activity.busMarkers.forEach { busMarker ->
                val busPoint = busMarker.position
                val routeDistance = calculateRouteDistance(stopPoint, busPoint)
                if (routeDistance == -1.0) {
                    return@forEach
                }

                var time = ((routeDistance / activity.sharedPreferences.getInt("speed", 25)) * 60).roundToInt()
                if (activity.btnEmulated.isChecked) {
                    time = ((routeDistance / 25) * 60).roundToInt()
                }

                infoBuilder.add(
                    Bus(
                        "${
                            busMarker.title?.split("\n")?.get(0)
                        }: $time min\n(${String.format(Locale.getDefault(), "%.3f", routeDistance)} km)", busPoint
                    )
                )
            }

            val sortedInfo = infoBuilder.sortedBy {
                it.name.substringAfter(": ").substringBefore(" min").toInt()
            }

            val url =
                "geo:${selectedStopMarker!!.position.latitude},${selectedStopMarker!!.position.longitude}?q=${selectedStopMarker!!.position.latitude},${selectedStopMarker!!.position.longitude}(${selectedStopMarker!!.title})"
            bottomSheetFragment.updateView(sortedInfo, selectedStopMarker!!.title, Uri.parse(url))
        }
    }

    private fun calculateRouteDistance(startPoint: GeoPoint, endPoint: GeoPoint): Double {
        val nearestStartIndex = findNearestPointIndex(startPoint, activity.routeCoordinates)
        val nearestEndIndex = findNearestPointIndex(endPoint, activity.routeCoordinates)

        if (nearestStartIndex < nearestEndIndex) {
            return -1.0
        }

        val startIndex = minOf(nearestStartIndex, nearestEndIndex)
        val endIndex = maxOf(nearestStartIndex, nearestEndIndex)

        var totalDistance = 0.0
        for (i in startIndex until endIndex) {
            totalDistance += calculateDistance(
                activity.routeCoordinates[i].latitude, activity.routeCoordinates[i].longitude,
                activity.routeCoordinates[i + 1].latitude, activity.routeCoordinates[i + 1].longitude
            )
        }
        return totalDistance
    }

    private fun findNearestPointIndex(point: GeoPoint, routeCoordinates: List<GeoPoint>): Int {
        return routeCoordinates.indices.minByOrNull {
            calculateDistance(
                point.latitude,
                point.longitude,
                routeCoordinates[it].latitude,
                routeCoordinates[it].longitude
            )
        } ?: 0
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun updateUserMarker(location: GeoPoint, bearing: Float?) {
        if (activity.currentUserMarker == null) {
            activity.currentUserMarker = Marker(map).apply {
                title = activity.getString(R.string.you_are_here)
                icon = ContextCompat.getDrawable(activity, R.drawable.ic_location)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                map.overlays.add(this)
            }
        }

        activity.currentUserMarker?.apply {
            position = location
            rotation = bearing ?: 0F
        }
        map.invalidate()
    }

    fun centerMapOnLocation(location: GeoPoint) {
        val mapController = map.controller
        mapController.animateTo(location, 20.0, 1000L)
    }

    fun addUserMarker(location: GeoPoint, bearing: Float) {
        val userMarker = Marker(map).apply {
            position = location
            title = activity.getString(R.string.you_are_here)
            icon = ContextCompat.getDrawable(activity, R.drawable.ic_location)
            rotation = bearing  // Rotate the marker
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)  // Set the rotation pivot
        }

        if (activity.currentUserMarker != null) {
            map.overlays?.remove(activity.currentUserMarker)
        }

        activity.currentUserMarker = userMarker
        map.overlays.add(userMarker)
        map.invalidate()
    }
}