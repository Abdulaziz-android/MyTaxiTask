package com.abdulaziz.mytaxi.di.module

import android.content.Context
import androidx.room.Room
import com.abdulaziz.mytaxi.data.local.AppDatabase
import com.abdulaziz.mytaxi.data.local.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "database")
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    @Singleton
    fun provideTripDao(appDatabase: AppDatabase):TripDao = appDatabase.tripDao()



}