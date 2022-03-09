package com.abdulaziz.mytaxi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TripOfDay::class], version = 1)
@TypeConverters(TripConverter::class)
abstract class AppDatabase : RoomDatabase(){

    abstract fun tripDao() : TripDao

}