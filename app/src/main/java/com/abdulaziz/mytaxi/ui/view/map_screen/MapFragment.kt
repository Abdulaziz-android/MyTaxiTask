package com.abdulaziz.mytaxi.ui.view.map_screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
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
import com.abdulaziz.mytaxi.data.model.getPoint
import com.abdulaziz.mytaxi.databinding.FragmentMapBinding
import com.abdulaziz.mytaxi.ui.dialogs.TripDetailsDialogFragment
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.abdulaziz.mytaxi.ui.view.picker.PlacePickFragment
import com.abdulaziz.mytaxi.utils.NetworkHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.CameraAnimatorOptions.Companion.cameraAnimatorOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
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
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.clearRouteLine
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.search.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

private const val MY_LOCATION = "Мое текущее местоположение"
private const val SELECT_PLACE_ON_MAP = "Выбрать место на карте"
private const val YOUR_LOCATION_NOT_FOUND = "Ваше текущее местоположение не найдено"
private const val BOTTOM_SHEET_TAG = "bottom_sheet_tag"

@AndroidEntryPoint
class MapFragment : Fragment(), PermissionListener {

    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var routeLineApi: MapboxRouteLineApi
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private var originPosition: Point? = null
    private var destinationPosition: Point? = null

    @Inject
    lateinit var tripDao: TripDao

    @Inject
    lateinit var networkHelper: NetworkHelper

    private var currentLocation: Point? = null
    private val TAG = "MapFragment"
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(layoutInflater, container, false)
        (activity as MainView?)?.showToolbar()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMapComponents()
        initUiComponents()
        onMapReady()
        observeFragmentResults()
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            // var lastLocation = locationMatcherResult.enhancedLocation
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }

    private fun initMapComponents() {
        with(binding.map) {
            logo.enabled = false
            compass.enabled = false
            attribution.enabled = false
            scalebar.enabled = false
            mapView = this
        }
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val routeLineOptions = MapboxRouteLineOptions.Builder(requireContext()).build()
        routeLineApi = MapboxRouteLineApi(routeLineOptions)
        routeLineView = MapboxRouteLineView(routeLineOptions)

        MapboxNavigationApp.current()?.registerLocationObserver(locationObserver)
    }

    private fun observeFragmentResults() {
        parentFragmentManager.setFragmentResultListener(
            MAP_POINT_REQUEST_CODE,
            viewLifecycleOwner
        ) { _, data ->
            val mapPoint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getSerializable(MAP_POINT_KEY, MapPoint::class.java)
            } else {
                data.getSerializable(MAP_POINT_KEY) as MapPoint
            }

            if (mapPoint != null) {
                when (mapPoint.routeType) {
                    ROUTE_TYPE_FROM -> {
                        binding.fromTv.text = mapPoint.directionName
                        originPosition = mapPoint.getPoint()
                        addOriginMarkerToMap(originPosition!!)
                    }
                    ROUTE_TYPE_TO -> {
                        binding.toTv.text = mapPoint.directionName
                        destinationPosition = mapPoint.getPoint()
                        addDestinationMarkerToMap(destinationPosition!!)
                    }
                }

                if (originPosition != null && destinationPosition != null) {
                    requestRoutes(originPosition!!, destinationPosition!!)
                }
                //addAnnotationToMap(mapPoint.getPoint())
            }
        }
    }

    var requestId : Long?=null
    private fun requestRoutes(origin: Point, destination: Point) {
        requestId = MapboxNavigationApp.current()?.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(requireContext())
                .coordinatesList(listOf(origin, destination))
                .alternatives(false)
                .build(),
            callback = routerCallback
        )
    }

    private val routerCallback = object : NavigationRouterCallback {
        override fun onCanceled(
            routeOptions: RouteOptions,
            routerOrigin: RouterOrigin
        ) {
            Log.d(TAG, "onCanceled: ")
        }

        override fun onFailure(
            reasons: List<RouterFailure>,
            routeOptions: RouteOptions
        ) {
            Log.d(TAG, "onFailure: ")
        }

        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
            // note: the first route in the list is considered the primary route
            /*binding.navigationView.api.routeReplayEnabled(true)
            binding.navigationView.api.startActiveGuidance(routes)*/
            Log.d(TAG, "onRoutesReady: ")
            routeLineApi.setNavigationRoutes(routes) { value ->
                mapView.getMapboxMap().getStyle()
                    ?.let { style ->
                        routeLineView.renderRouteDrawData(style, value)
                        onCameraTrackingDismissed()
                        openBottomSheetDialog()
                        animateCamera()
                        addOriginMarkerToMap(originPosition!!)
                        addDestinationMarkerToMap(destinationPosition!!)
                    }
            }
        }

    }

    private fun animateCamera() {
        val configuration: Configuration = resources.configuration
        val width = configuration.screenWidthDp
        val height = configuration.screenHeightDp
        val paddingBottom = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (height * 3 / 4).toFloat(),
            resources.displayMetrics
        ).roundToInt().toDouble()
        val paddingTop = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (height / 10).toFloat(),
            resources.displayMetrics
        ).roundToInt().toDouble()
        val paddingHorizontal = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (width / 10).toFloat(),
            resources.displayMetrics
        ).roundToInt().toDouble()

        val cameraOptions = mapView.getMapboxMap()
            .cameraForCoordinates(
                listOf(originPosition!!, destinationPosition!!),
                EdgeInsets(
                    paddingTop,
                    paddingHorizontal,
                    paddingBottom,
                    paddingHorizontal
                )
            )
        mapView.getMapboxMap().setCamera(cameraOptions)
    }

    private fun initUiComponents() = with(binding) {
        fromTv.setOnClickListener {
            openPlacePicker(ROUTE_TYPE_FROM)
        }
        toTv.setOnClickListener {
            openPlacePicker(ROUTE_TYPE_TO)
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
            focusCurrentLocation()
        }

      /*  mapView.getMapboxMap().addOnMapClickListener {
            addAnnotationToMap(it)
            true
        }*/
    }

    private fun openPlacePicker(routeType: String) {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                PlacePickFragment::class.java,
                bundleOf(ROUTE_TYPE_KEY to routeType)
            ).addToBackStack("map").commit()
    }

    @SuppressLint("MissingPermission")
    private fun focusCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val cameraPosition = CameraOptions.Builder()
                    .zoom(16.0)

                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .build()
                mapView.getMapboxMap().setCamera(cameraPosition)
            }
        }
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
                Toast.makeText(requireContext(), "cancel clicked!", Toast.LENGTH_SHORT).show()
                clearRouteLines()
                clearMarkers()
                focusCurrentLocation()
                if (isSaved) {
                    saveTrip(trip)
                }
            }
        })

        dialogFragment.setTrip(trip)
        dialogFragment.setSaveButton()
        dialogFragment.show(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    private fun clearMarkers(){
        val annotationApi = mapView.annotations
        annotationApi.cleanup()
    }

    private fun clearRouteLines() {
        lifecycleScope.launch {
            mapView.getMapboxMap().getStyle()
                ?.let { style ->
                    routeLineView.renderClearRouteLineValue(style, routeLineApi.clearRouteLine())
                }
        }
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


    private fun onMapReady() {
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(16.0)
                .build()
        )
        mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS
        ) {
            initLocationComponent()
            setupGesturesListener()
        }
    }

  /*  private fun addAnnotationToMap(point: Point) {
        // Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_location_on_24
        )?.let {
            val annotationApi = mapView.annotations
            //annotationApi.cleanup()

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
    }*/

    private fun addOriginMarkerToMap(point: Point) {
        // Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_gps_red
        )?.let {
            val annotationApi = mapView.annotations
            //annotationApi.cleanup()

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
                .withIconSize(2.0)
            // Add the resulting pointAnnotation to the map.
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }


    private fun addDestinationMarkerToMap(point: Point) {
        // Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_gps_fixed_24
        )?.let {
            val annotationApi = mapView.annotations

            val pointAnnotationManager = annotationApi.createPointAnnotationManager(
                AnnotationConfig(layerId = "Destination")
            )

            // Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
            // Define a geographic coordinate.
                .withPoint(point)
            // Specify the bitmap you assigned to the point annotation
            // The bitmap will be added to map style automatically.
                .withIconImage(it)
                .withIconSize(2.0)
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


    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        Log.d(TAG, "bearing changed: ")
        // mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
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
        onCameraTrackingDismissed()
        mapView.onDestroy()
        routeLineApi.cancel()
        routeLineView.cancel()
        MapboxNavigationApp.current()?.unregisterLocationObserver(locationObserver)
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


    companion object {
        const val MAP_POINT_REQUEST_CODE = "point_request_code"
        const val MAP_POINT_KEY = "map_point_key"
        const val ROUTE_TYPE_KEY = "map_point_key"
        const val ROUTE_TYPE_TO = "to"
        const val ROUTE_TYPE_FROM = "from"
        const val ROUTE_DEFAULT_TYPE = "from"
    }

    /* sealed class RouteType {
         object TO : RouteType()
         object FROM : RouteType()
     }*/
}