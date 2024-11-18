package com.codersanx.busview.utils.network

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray


class Network {
    suspend fun getRoutes(context: Context): MutableList<String> = withContext(Dispatchers.IO) {
        val allRoutes: MutableList<String> = mutableListOf()

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/Anready/BusRoutes/refs/heads/main/routes.json")
                .build()

            val response = client.newCall(request).execute()
            val routesArray = JSONArray(response.body!!.string())

            for (i in 0 until routesArray.length()) {
                allRoutes.add(routesArray.getString(i))
            }

            // ONLY FOR TESTING !!!
            allRoutes.add("1560: Parklane Hotel - My Mall###https://raw.githubusercontent.com/Anready/BusRoutes/refs/heads/main/30/Parklane_Hotel_-_My_Mall###Parklane Hoteo - New Port - My Mall")

            allRoutes
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No internet", Toast.LENGTH_SHORT).show()
            }
            delay(5000)
            getRoutes(context)
        }
    }
}
