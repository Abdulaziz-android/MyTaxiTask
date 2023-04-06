package com.abdulaziz.mytaxi.ui.view.map_screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.data.local.TripDao
import com.abdulaziz.mytaxi.data.local.TripOfDay
import com.abdulaziz.mytaxi.data.model.MapPoint
import com.abdulaziz.mytaxi.databinding.FragmentMapBinding
import com.abdulaziz.mytaxi.ui.dialogs.TripDetailsDialogFragment
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.abdulaziz.mytaxi.ui.view.search.SearchPlaceFragment
import com.abdulaziz.mytaxi.ui.view.trip_history_screen.TripHistoryFragment
import com.abdulaziz.mytaxi.utils.NetworkHelper
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationManager
import com.mapbox.maps.plugin.annotation.AnnotationManagerImpl
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.search.*
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autofill.AddressAutofill
import com.mapbox.search.autofill.AddressAutofillOptions
import com.mapbox.search.autofill.Query
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

private const val FROM = "from"
private const val TO = "to"
private const val MY_LOCATION = "Мое текущее местоположение"
private const val SELECT_PLACE_ON_MAP = "Выбрать место на карте"
private const val YOUR_LOCATION_NOT_FOUND = "Ваше текущее местоположение не найдено"
private const val BOTTOM_SHEET_TAG = "bottom_sheet_tag"

@AndroidEntryPoint
class MapFragment : Fragment(), PermissionListener {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private var originPosition: Point? = null
    private var destinationPosition: Point? = null

    private var direction: String = ""

    @Inject
    lateinit var tripDao: TripDao

    @Inject
    lateinit var networkHelper: NetworkHelper

    private var currentLocation: Point? = null
    private val TAG = "MapFragment"



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(layoutInflater, container, false)
        (activity as MainView?)?.showToolbar()
        with(binding.map) {
            logo.enabled = false
            compass.enabled = false
            attribution.enabled = false
            scalebar.enabled = false
            mapView = this
        }
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUiComponents()
        onMapReady()

        parentFragmentManager.setFragmentResultListener(
            SearchPlaceFragment.MAP_POINT_REQUEST_CODE,
            viewLifecycleOwner
        ) { _, data ->
            val mapPoint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getSerializable(SearchPlaceFragment.MAP_POINT_KEY, MapPoint::class.java)
            }else{
                data.getSerializable(SearchPlaceFragment.MAP_POINT_KEY) as MapPoint
            }

            if (mapPoint!=null){
                when(mapPoint.routeType){
                    "from" -> {
                        binding.fromTv.text = mapPoint.directionName
                    }
                    "to" -> {
                        binding.toTv.text = mapPoint.directionName
                    }
                }
            }
        }
    }

    private fun initUiComponents() = with(binding) {

        fromTv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    SearchPlaceFragment::class.java,
                    bundleOf(SearchPlaceFragment.ROUTE_TYPE_KEY to "from")
                ).addToBackStack("map").commit()
            //openAutoComplete(it.id)
        }

        nextIv.setOnClickListener {
            if (originPosition != null && destinationPosition != null
                && toTv.text.toString().isNotEmpty() && fromTv.text.toString()
                    .isNotEmpty()
            ) {
                openBottomSheetDialog()
            }
        }

        fab.setOnClickListener {
            onCameraTrackingEnabled()
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(currentLocation)
                    .zoom(16.0)
                    .build()
            )
        }

        mapView.getMapboxMap().addOnMapClickListener {
            addAnnotationToMap(it)
            true
        }
    }

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        currentLocation = it
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(it).build()
        )
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }


    private fun onCameraTrackingDismissed() {
        Toast.makeText(requireContext(), "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    private fun onCameraTrackingEnabled() {
        Toast.makeText(requireContext(), "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.addOnMoveListener(onMoveListener)
    }

    @SuppressLint("MissingPermission")
    private fun openBottomSheetDialog() {
        (activity as MainView?)?.hideToolbar()
        val dialogFragment = TripDetailsDialogFragment()
        val trip = Trip(
            originName = binding.fromTv.text.toString(),
            destinationName = binding.toTv.text.toString(),
            details = SimpleDateFormat("HH:mm", Locale("ru")).format(Date()),
            originLatitude = originPosition!!.latitude(),
            originLongitude = originPosition!!.longitude(),
            destinationLatitude = destinationPosition!!.latitude(),
            destinationLongitude = destinationPosition!!.longitude()
        )

        dialogFragment.setOnChooseReasonListener(object :
            TripDetailsDialogFragment.OnBottomSheetListener {
            override fun onClosed(isSaved: Boolean, isRemoved: Boolean) {
                (activity as MainView?)?.showToolbar()
                if (isSaved) {
                    saveTrip(trip)
                }
            }
        })

        dialogFragment.setTrip(trip)
        dialogFragment.setSaveButton()
        dialogFragment.show(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    private fun saveTrip(trip: Trip) {
        val today = SimpleDateFormat("d MMMM, EEEE", Locale("ru")).format(Date())
        val tripOfDay = tripDao.getTripByDate(today)
        val lastTripID = tripDao.getLastTripID()
        val tripID = if (tripOfDay != null && tripOfDay.id != 0) {
            tripOfDay.id
        } else if (lastTripID != 0) {
            lastTripID
        } else 1
        trip.tripDayID = tripID
        if (tripOfDay != null && tripOfDay.list?.isNotEmpty()!!) {
            var isExist = false
            tripOfDay.list.forEach {
                if (it == trip) {
                    isExist = true
                    return@forEach
                }
            }
            if (!isExist) {
                tripOfDay.list.add(trip)
                tripDao.insert(tripOfDay)
            }
        } else {
            val tripOfDay1 = TripOfDay(date = today)
            tripOfDay1.list?.add(trip)
            tripDao.insert(tripOfDay1)
        }
    }


    private fun openAutoComplete(editTextId: Int) {
        direction = when (editTextId) {
            R.id.to_tv -> TO
            R.id.from_tv -> FROM
            else -> TO
        }
    }

    private fun onMapReady() {
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(16.0)
                .build()
        )
        mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS
        ) {
            mapView.getMapboxMap()
            initLocationComponent()
            setupGesturesListener()
        }
    }

    private fun addAnnotationToMap(point: Point) {
        val searchEngine = SearchEngine.createSearchEngine(SearchEngineSettings(getString(R.string.mapbox_access_token)))
        val options = ReverseGeoOptions(
            center = point,
            limit = 1
        )
        searchEngine.search("fergana", SearchOptions(), object : SearchSuggestionsCallback{
            override fun onError(e: Exception) {
                Log.d(TAG, "onError: ${e.message}")
            }

            override fun onSuggestions(
                suggestions: List<SearchSuggestion>,
                responseInfo: ResponseInfo
            ) {

                Log.d(TAG, "onResults: ${suggestions[0].fullAddress}")
            }


        })
// Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_location_on_24
        )?.let {
            val annotationApi = mapView.annotations
            annotationApi.cleanup()
            val pointAnnotationManager = annotationApi.createPointAnnotationManager(
                AnnotationConfig()
            )
            // Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
// Define a geographic coordinate.
                .withPoint(point)
// Specify the bitmap you assigned to the point annotation
// The bitmap will be added to map style automatically.
                .withIconImage(it)
// Add the resulting pointAnnotation to the map.
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

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

    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                topImage = AppCompatResources.getDrawable(
                    requireContext(),
                    com.mapbox.maps.plugin.locationcomponent.R.drawable.mapbox_user_icon,
                ),
                bearingImage = AppCompatResources.getDrawable(
                    requireContext(),
                    com.mapbox.maps.plugin.locationcomponent.R.drawable.mapbox_user_bearing_icon,
                ),
                shadowImage = AppCompatResources.getDrawable(
                    requireContext(),
                    com.mapbox.maps.plugin.locationcomponent.R.drawable.mapbox_user_stroke_icon,
                ),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson()
            )
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            onIndicatorPositionChangedListener
        )
        locationComponentPlugin.addOnIndicatorBearingChangedListener(
            onIndicatorBearingChangedListener
        )
    }

    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(binding.root.context)) {
            /* initializeLocationEngine()
             initializeLocationLayer()*/

        } else {
            Dexter.withContext(binding.root.context)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(this)
                .check()
        }
    }

    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
        /*initializeLocationEngine()
        initializeLocationLayer()*/
    }

    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
        Toast.makeText(binding.root.context, "Пожалуйста, включите разрешение!", Toast.LENGTH_SHORT)
            .show()
    }

    override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {

    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

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
        onCameraTrackingDismissed()
        mapView.onDestroy()
    }

/*  private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
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
    }*/
}