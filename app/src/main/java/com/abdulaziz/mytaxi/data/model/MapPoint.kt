package com.abdulaziz.mytaxi.data.model

import java.io.Serializable

data class MapPoint(
    val routeType: String,
    val directionName: String? = null,
    val latitude: Double,
    val longitude: Double
) : Serializable
