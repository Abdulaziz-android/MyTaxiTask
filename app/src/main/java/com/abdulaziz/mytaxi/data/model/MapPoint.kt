package com.abdulaziz.mytaxi.data.model

import com.abdulaziz.mytaxi.ui.view.map_screen.MapFragment
import com.mapbox.geojson.Point
import java.io.Serializable

data class MapPoint(
    val routeType: String,
    val directionName: String? = null,
    val latitude: Double,
    val longitude: Double
) : Serializable

fun MapPoint.getPoint():Point{
    return Point.fromLngLat(longitude, latitude)
}
