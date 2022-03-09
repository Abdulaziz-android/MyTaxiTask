package com.abdulaziz.mytaxi.data.local

import androidx.room.*

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tripOfDay: TripOfDay)

    @Delete
    fun delete(tripOfDay: TripOfDay)

    @Query("select * from tripofday")
    fun getAllTrips():List<TripOfDay>

    @Query("select * from tripofday where date = :date")
    fun getTripByDate(date:String):TripOfDay

    @Query("select * from tripofday where id = :id")
    fun getTripByID(id:Int):TripOfDay

    @Query("select MAX(id) FROM tripofday")
    fun getLastTripID():Int
}