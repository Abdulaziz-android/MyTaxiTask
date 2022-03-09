package com.abdulaziz.mytaxi.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class TripConverter {
    @TypeConverter
    fun fromStringList(list: ArrayList<Trip>?): String? {
        if (list == null) {
            return null
        }
        val gson = Gson()
        val type: Type = object : TypeToken<ArrayList<Trip?>?>() {}.type
        return gson.toJson(list, type)
    }

    @TypeConverter
    fun toStringList(companyString: String?): ArrayList<Trip>? {
        if (companyString == null) {
            return null
        }
        val gson = Gson()
        val type: Type = object : TypeToken<ArrayList<Trip?>?>() {}.type
        return gson.fromJson<ArrayList<Trip>>(companyString, type)
    }
}