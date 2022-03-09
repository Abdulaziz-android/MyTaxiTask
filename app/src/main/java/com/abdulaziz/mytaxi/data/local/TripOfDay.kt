package com.abdulaziz.mytaxi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity
data class TripOfDay(
    @PrimaryKey(autoGenerate = true)
    val id:Int=0,
    val date:String,
    @TypeConverters(TripConverter::class)
    val list:ArrayList<Trip>? = arrayListOf()
)
