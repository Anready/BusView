package com.codersanx.busview.main

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.codersanx.busview.MainActivity
import com.codersanx.busview.R
import com.codersanx.busview.buses.Emulation
import com.codersanx.busview.buses.RealTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class BusNetwork(private val map: MapView, private val activity: MainActivity) {
    private val stopMarkers = mutableListOf<Marker>()
    private val client = OkHttpClient()

    suspend fun getRoutes(context: Context): MutableList<String> = withContext(Dispatchers.IO) {
        val allRoutes: MutableList<String> = mutableListOf()

        try {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/Anready/BusRoutes/refs/heads/main/routes.json")
                .build()

            val response = client.newCall(request).execute()
            val routesArray = JSONArray(response.body!!.string())

            for (i in 0 until routesArray.length()) {
                allRoutes.add(routesArray.getString(i))
            }

            // ONLY FOR TESTING !!!
            // allRoutes.add("1560: Parklane Hotel - My Mall###https://raw.githubusercontent.com/Anready/BusRoutes/refs/heads/main/30/Parklane_Hotel_-_My_Mall###Parklane Hoteo - New Port - My Mall")

            allRoutes
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, activity.getString(R.string.no_internet), Toast.LENGTH_SHORT).show()
            }
            delay(5000)
            getRoutes(context)
        }
    }

    suspend fun loadStops() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getLink(activity.route.text.toString())}stops.json")
                .build()

            val response = client.newCall(request).execute()
            val stopsArray = JSONArray(response.body!!.string())

            withContext(Dispatchers.Main) {
                for (i in 0 until stopsArray.length()) {
                    val stop = stopsArray.getJSONObject(i)
                    val marker = Marker(map).apply {
                        position = GeoPoint(stop.getDouble("lat"), stop.getDouble("lng"))
                        title = stop.optString("name", "Stop ${i + 1}")
                        icon = ContextCompat.getDrawable(activity, R.drawable.bus_stop)
                        setOnMarkerClickListener { marker, _ ->
                            activity.mapControl.selectStop(marker)
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

    suspend fun loadRoute() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getLink(activity.route.text.toString())}.json")
                .build()

            val response = client.newCall(request).execute()
            val routePointsArray = JSONArray(response.body!!.string())

            withContext(Dispatchers.Main) {
                for (i in 0 until routePointsArray.length()) {
                    val point = routePointsArray.getJSONObject(i)
                    activity.routeCoordinates.add(GeoPoint(point.getDouble("lat"), point.getDouble("lng")))
                }

                val routeLine = Polyline().apply {
                    setPoints(activity.routeCoordinates)
                    outlinePaint.color = Color.RED
                    outlinePaint.strokeWidth = 4f
                }
                map.overlays.add(routeLine)
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun showBuses(isRealTime: Boolean, emulated: Emulation?) = withContext(Dispatchers.IO) {
        try {
            if (activity.route.text.toString() == "") {
                return@withContext
            }

            val realTime = RealTime(activity)

            val response = if(isRealTime) {
                realTime.buses
            } else {
                emulated!!.buses
            }

            val busesJson = JSONObject(response).getJSONObject("Buses")

            withContext(Dispatchers.Main) {
                activity.busMarkers.forEach { map.overlays.remove(it) }
                activity.busMarkers.clear()

                busesJson.keys().forEach { key ->
                    val bus = busesJson.getJSONObject(key)
                    if (bus.getString("RouteLongName") == getLongName(activity.route.text.toString())) {
                        val bearing = -(bus.getDouble("Bearing").toFloat() - 90F)

                        val busMarker = Marker(map).apply {
                            position =
                                GeoPoint(bus.getDouble("Latitude"), bus.getDouble("Longitude"))
                            icon =
                                ContextCompat.getDrawable(activity, R.drawable.bus_map)
                            title =
                                activity.getString(
                                    R.string.label_bus,
                                    bus.getString("Label"),
                                    bus.getDouble("SpeedKmPerHour").toInt()
                                )
                            rotation = bearing


                            setOnMarkerClickListener { _, _ ->
                                Toast.makeText(
                                    activity,
                                    activity.getString(
                                        R.string.label_bus,
                                        bus.getString("Label"),
                                        bus.getDouble("SpeedKmPerHour").toInt()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                        activity.busMarkers.add(busMarker)
                        map.overlays.add(busMarker)
                    }
                }

                if (activity.busMarkers.isEmpty()) {
                    activity.findViewById<TextView>(R.id.warning).visibility = View.VISIBLE
                } else {
                    activity.findViewById<TextView>(R.id.warning).visibility = View.GONE
                }

                activity.mapControl.calculateDistancesToStop()
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLongName(name: String): String {
        activity.routes.forEach { route ->
            if (route.label == name) {
                return route.routLong
            }
        }

        return ""
    }

    private fun getLink(name: String): String {
        activity.routes.forEach { route ->
            if (route.label == name) {
                return route.routeLink
            }
        }

        return ""
    }
}
