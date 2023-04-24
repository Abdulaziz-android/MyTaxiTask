package com.abdulaziz.mytaxi.ui.view.trip_details_screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.data.local.TripDao
import com.abdulaziz.mytaxi.databinding.FragmentTripDetailsBinding
import com.abdulaziz.mytaxi.ui.dialogs.TripDetailsDialogFragment
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ARG_PARAM1 = "trip"
private const val ARG_PARAM2 = "param2"

@AndroidEntryPoint
class TripDetailsFragment : Fragment() {

    private var trip: Trip? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            trip = it.getSerializable(ARG_PARAM1) as Trip
            param2 = it.getString(ARG_PARAM2)
        }
    }

    private var _binding: FragmentTripDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

    @Inject lateinit var tripDao: TripDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripDetailsBinding.inflate(layoutInflater, container, false)

        mapView = binding.map
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)


        return binding.root
    }

    private fun openBottomSheetDialog() {
        val dialogFragment = TripDetailsDialogFragment()

        dialogFragment.setOnChooseReasonListener(object :
            TripDetailsDialogFragment.OnBottomSheetListener {
            override fun onClosed(isSaved:Boolean, isRemoved:Boolean) {
                if (isRemoved){
                    val tripOfDay = tripDao.getTripByID(trip?.tripDayID!!)
                    tripOfDay.list?.remove(trip)
                    if (tripOfDay.list?.isNotEmpty()!!) {
                        tripDao.insert(tripOfDay)
                    }else tripDao.delete(tripOfDay)
                }
                (activity as MainView?)?.backPressed()
            }
        })

        dialogFragment.setTrip(trip!!)
        dialogFragment.show(childFragmentManager, "bottomsheetdialog")
    }


    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
// copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }


    @SuppressLint("Lifecycle")
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    @SuppressLint("Lifecycle")
    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    @SuppressLint("Lifecycle")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    @SuppressLint("Lifecycle")
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }



}