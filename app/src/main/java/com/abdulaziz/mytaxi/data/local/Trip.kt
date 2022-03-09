package com.abdulaziz.mytaxi.data.local

import java.io.Serializable

data class Trip(
    val originName:String,
    val destinationName:String,
    val details:String,
    val originLatitude:Double,
    val originLongitude:Double,
    val destinationLatitude:Double,
    val destinationLongitude:Double,
    var tripDayID : Int?=null
):Serializable