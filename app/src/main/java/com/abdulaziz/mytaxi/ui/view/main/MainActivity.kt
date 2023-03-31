package com.abdulaziz.mytaxi.ui.view.main

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.databinding.ActivityMainBinding
import com.abdulaziz.mytaxi.databinding.ItemAlertDialogBinding
import com.abdulaziz.mytaxi.ui.menu.SlideMenuAdapter
import com.abdulaziz.mytaxi.ui.menu.SlideMenuItem
import com.abdulaziz.mytaxi.ui.view.map_screen.MapFragment
import com.abdulaziz.mytaxi.ui.view.trip_history_screen.TripHistoryFragment
import com.abdulaziz.mytaxi.utils.ConnectionLiveData
import com.abdulaziz.mytaxi.utils.NetworkHelper
import com.mobeedev.library.SlidingMenuBuilder
import com.mobeedev.library.SlidingNavigation
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainView {

    private lateinit var binding: ActivityMainBinding
    private lateinit var slidingNavigation: SlidingNavigation
    @Inject lateinit var networkHelper: NetworkHelper
    private val TAG = "MapsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
     //   setSupportActionBar(binding.toolbar as Toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setUpMenu(savedInstanceState)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    MapFragment()
                ).commit()
            setConnectionChecker()
        }else{
            showInfoDialog()
        }

    }

    @SuppressLint("SetTextI18n")
    private fun showInfoDialog() {

        val alertDialog = AlertDialog.Builder(binding.root.context, R.style.AlertDialogTheme).create()
        val itemDialog = ItemAlertDialogBinding.inflate(layoutInflater)
        alertDialog.setView(itemDialog.root)
        itemDialog.imageView.visibility = View.GONE
        itemDialog.aboutTv.text = "Это приложение работает на Android 10 и ниже."
        alertDialog.setCancelable(false)
        alertDialog.show()
    }

    private fun setConnectionChecker() {
        val alertDialog = AlertDialog.Builder(binding.root.context, R.style.AlertDialogTheme).create()
        val itemDialog = ItemAlertDialogBinding.inflate(layoutInflater)
        alertDialog.setView(itemDialog.root)
        alertDialog.setCancelable(false)

        val connectionLiveData = ConnectionLiveData(application)
        connectionLiveData.observe(this) { isConnect ->
            if (isConnect) {
                alertDialog.dismiss()
            } else {
                alertDialog.show()
            }
        }

        if (!networkHelper.isNetworkConnected()){
            alertDialog.show()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setUpMenu(savedInstanceState: Bundle?) {
        val screenWidth = getScreenWidth()
        val dragDistance = (screenWidth * 0.74).toInt()
        Log.d(TAG, "setUpMenu: $screenWidth")
        slidingNavigation = SlidingMenuBuilder(this)
            .withMenuOpened(false)
            .withContentClickableWhenMenuOpened(false)
            .withSavedState(savedInstanceState)
            .withRootViewScale(0.8f)
            .withDragDistance(dragDistance)
            .withMenuLayout(R.layout.fragment_menu)
            .withToolbarMenuToggle(binding.toolbar)
            .inject()

        val tmpElements = mutableListOf(
            SlideMenuItem(R.drawable.ic_outline_near_me_24, "Мои поездки"),
            SlideMenuItem(R.drawable.ic_baseline_account_balance_wallet_24, "Способы оплаты"),
            SlideMenuItem(R.drawable.ic_outline_star_24, "Избранные адреса")
        )
        val adapter =
            SlideMenuAdapter(tmpElements, object : SlideMenuAdapter.OnMenuItemClickListener {
                override fun onItemClicked() {
                        supportActionBar?.title = "Мои поездки"
                        supportFragmentManager.beginTransaction()
                            .replace(
                                R.id.fragment_container,
                                TripHistoryFragment()
                            ).addToBackStack("map").commit()
                        slidingNavigation.closeMenu()
                }
            })
        val imageView = findViewById<ImageView>(R.id.avatar_iv)
        val recyclerView = findViewById<RecyclerView>(R.id.menu_rv)
        Picasso.get()
            .load("https://www.mrdustbin.com/us/wp-content/uploads/2021/11/Tom-Hiddleston.jpg")
            .into(imageView)
        recyclerView.adapter = adapter
    }

    private fun getScreenWidth(): Int {
        val configuration: Configuration = resources.configuration
        return configuration.screenWidthDp
    }

    override fun hideToolbar() {
        binding.toolbar.visibility = View.GONE
    }

    override fun showToolbar() {
        binding.toolbar.visibility = View.VISIBLE
    }

    override fun backPressed() {
        onBackPressed()
    }


}