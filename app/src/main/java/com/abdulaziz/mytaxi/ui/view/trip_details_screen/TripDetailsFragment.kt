package com.abdulaziz.mytaxi.ui.view.trip_details_screen

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.data.local.TripDao
import com.abdulaziz.mytaxi.databinding.FragmentTripDetailsBinding
import com.abdulaziz.mytaxi.ui.dialogs.TripDetailsDialogFragment
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject
import kotlin.math.roundToInt

private const val ARG_PARAM1 = "trip"
private const val ARG_PARAM2 = "param2"

@AndroidEntryPoint
class TripDetailsFragment : Fragment(), OnMapReadyCallback {

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
    private lateinit var map: MapboxMap

    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null

    @Inject lateinit var tripDao: TripDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Mapbox.getInstance(context?.applicationContext!!, getString(R.string.access_token))
        _binding = FragmentTripDetailsBinding.inflate(layoutInflater, container, false)
        val options = MapboxMapOptions().textureMode(true)
        mapView = MapView(binding.root.context, options)
        mapView = binding.map
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)


        return binding.root
    }


    private fun getRoute() {
        NavigationRoute.builder()
            .accessToken(Mapbox.getAccessToken())
            .origin(Point.fromLngLat(trip!!.originLongitude, trip!!.originLatitude))
            .destination(Point.fromLngLat(trip!!.destinationLongitude, trip!!.destinationLatitude))
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    if (response.isSuccessful) {
                        val currentRoute = response.body()?.routes()?.get(0)

                        val navigationMapRoute = NavigationMapRoute(null, mapView, map)

                        navigationMapRoute.addRoute(currentRoute)

                        // Animate Camera
                        val bounds = LatLngBounds.Builder()
                            .include(
                                LatLng(
                                    trip!!.originLatitude,
                                    trip!!.originLongitude,
                                )
                            )
                            .include(
                                LatLng(
                                    trip!!.destinationLatitude,
                                    trip!!.destinationLongitude, 100.0
                                )
                            )
                            .build()

                        val configuration: Configuration = resources.configuration
                        val width = configuration.screenWidthDp
                        val height = configuration.screenHeightDp
                        val paddingBottom = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            (height * 3 / 4).toFloat(),
                            resources.displayMetrics
                        ).roundToInt()
                        val paddingTop = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            (height / 10).toFloat(),
                            resources.displayMetrics
                        ).roundToInt()
                        val paddingHorizontal = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            (width / 10).toFloat(),
                            resources.displayMetrics
                        ).roundToInt()

                        val cameraPosition =
                            map.getCameraForLatLngBounds(bounds, intArrayOf(paddingHorizontal, paddingTop, paddingHorizontal, paddingBottom))
                        map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
                        openBottomSheetDialog()
                    }
                }


                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {

                }

            })
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


    private fun markLocation() {

        val drawable1 =
            ContextCompat.getDrawable(binding.root.context, R.drawable.ic_baseline_gps_red)
        val bitmap1 = convertDrawableToBitmap(drawable1)
        val icon1 = IconFactory.getInstance(binding.root.context).fromBitmap(bitmap1!!)

        originMarker =
            map.addMarker(
                MarkerOptions().position(LatLng(trip!!.originLatitude, trip!!.originLongitude))
                    .setIcon(icon1)
            )

        val drawable2 =
            ContextCompat.getDrawable(binding.root.context, R.drawable.ic_baseline_gps_fixed_24)
        val bitmap2 = convertDrawableToBitmap(drawable2)
        val icon2 = IconFactory.getInstance(binding.root.context).fromBitmap(bitmap2!!)


        destinationMarker =
            map.addMarker(
                MarkerOptions().position(
                    LatLng(
                        trip!!.destinationLatitude,
                        trip!!.destinationLongitude
                    )
                ).setIcon(icon2)
            )

        getRoute()
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

    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap
        markLocation()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }



}