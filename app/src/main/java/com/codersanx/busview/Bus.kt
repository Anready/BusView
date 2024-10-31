package com.codersanx.busview

data class Bus(
    val label: String,
    val routeShortName: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmPerHour: Double
)
