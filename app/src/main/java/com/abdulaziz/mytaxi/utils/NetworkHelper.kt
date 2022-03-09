package com.abdulaziz.mytaxi.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


class NetworkHelper @Inject constructor(@ApplicationContext val context: Context) {

    fun isNetworkConnected(): Boolean {
        var result = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            result = when{
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return true
                else -> return false
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when(type){
                        ConnectivityManager.TYPE_WIFI ->return true
                        ConnectivityManager.TYPE_MOBILE ->return true
                        ConnectivityManager.TYPE_ETHERNET ->return true
                        else -> return false
                    }
                }
            }
        }
        return result
    }

}