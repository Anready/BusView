package com.codersanx.busview.utils.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class Network {
    suspend fun getRoutes(): MutableList<String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/Anready/BusRoutes/refs/heads/main/routes.json")
            .build()

        val response = client.newCall(request).execute()
        val routesArray = JSONArray(response.body!!.string())

        val allRoutes: MutableList<String> = mutableListOf()
        for (i in 0 until routesArray.length()) {
            allRoutes.add(routesArray.getString(i))
        }

        allRoutes
    }
}
