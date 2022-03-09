package com.abdulaziz.mytaxi.ui.view.map_screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.abdulaziz.mytaxi.ui.view.main.MainActivity
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.data.local.TripDao
import com.abdulaziz.mytaxi.data.local.TripOfDay
import com.abdulaziz.mytaxi.databinding.FragmentMapBinding
import com.abdulaziz.mytaxi.ui.dialogs.TripDetailsDialogFragment
import com.abdulaziz.mytaxi.utils.NetworkHelper
import com.google.gson.JsonObject
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.mapboxsdk.plugins.places.picker.PlacePicker
import com.mapbox.mapboxsdk.plugins.places.picker.model.PlacePickerOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

private const val REQUEST_CODE_AUTOCOMPLETE = 1
private const val PLACE_SELECTION_REQUEST_CODE = 2
private const val MAP_CHOOSE_ID = "map_choose"
private const val CURRENT_LOCATION_ID = "current_location"
private const val FROM = "from"
private const val TO = "to"
private const val MY_LOCATION = "Мое текущее местоположение"
private const val SELECT_PLACE_ON_MAP = "Выбрать место на карте"
private const val YOUR_LOCATION_NOT_FOUND = "Ваше текущее местоположение не найдено"
private const val BOTTOM_SHEET_TAG = "bottom_sheet_tag"

@AndroidEntryPoint
class MapFragment : Fragment(),
    OnMapReadyCallback, LocationEngineListener, PermissionListener {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private var locationEngine: LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null
    private var originLocation: Location? = null
    private var currentLocation: Location? = null
    private var originPosition: Point? = null
    private var destinationPosition: Point? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var navigationMapRoute: NavigationMapRoute? = null

    private var chooseCF: CarmenFeature? = null
    private var currentCF: CarmenFeature? = null

    private var direction: String = ""

    @Inject
    lateinit var tripDao: TripDao

    @Inject
    lateinit var networkHelper: NetworkHelper


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Mapbox.getInstance(context?.applicationContext!!, getString(R.string.access_token))
        _binding = FragmentMapBinding.inflate(layoutInflater, container, false)
        (activity as MainView?)?.showToolbar()
        mapView = binding.map
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        binding.toTv.setOnClickListener {
            openAutoComplete(it.id)
        }

        binding.fromTv.setOnClickListener {
            openAutoComplete(it.id)
        }

        binding.nextIv.setOnClickListener {
            if (originPosition != null && destinationPosition != null
                && binding.toTv.text.toString().isNotEmpty() && binding.fromTv.text.toString()
                    .isNotEmpty()
            ) {
                openBottomSheetDialog()
            }
        }

        return binding.root
    }

    @SuppressLint("MissingPermission")
    private fun openBottomSheetDialog() {
        (activity as MainView?)?.hideToolbar()
        locationLayerPlugin!!.setLocationLayerEnabled(false)
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
                locationLayerPlugin!!.setLocationLayerEnabled(true)
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
            tripOfDay.list?.forEach {
                if (it == trip) {
                    isExist = true
                    return@forEach
                }
            }
            if (!isExist) {
                tripOfDay.list?.add(trip)
                tripDao.insert(tripOfDay)
            }
        } else {
            val tripOfDay1 = TripOfDay(date = today)
            tripOfDay1.list?.add(trip)
            tripDao.insert(tripOfDay1)
        }
    }


    private fun addFeaturesForAutoComplete() {
        chooseCF = CarmenFeature.builder().text(SELECT_PLACE_ON_MAP)
            .geometry(Point.fromLngLat(69.240562, 41.311081))
            .id(MAP_CHOOSE_ID)
            .properties(JsonObject())
            .build()
        currentCF = CarmenFeature.builder().text(MY_LOCATION)
            .geometry(Point.fromLngLat(69.240562, 41.311081))
            .id(CURRENT_LOCATION_ID)
            .properties(JsonObject())
            .build()
    }

    private fun markCurrentLocation() {
        if (currentLocation != null) {
            val reverseGeocode = MapboxGeocoding.builder()
                .accessToken(Mapbox.getAccessToken())
                .query(Point.fromLngLat(currentLocation!!.longitude, currentLocation!!.latitude))
                .geocodingTypes(GeocodingCriteria.TYPE_ADDRESS)
                .build()

            reverseGeocode.enqueueCall(object : Callback<GeocodingResponse> {
                override fun onResponse(
                    call: Call<GeocodingResponse>,
                    response: Response<GeocodingResponse>
                ) {
                    if (response.isSuccessful) {
                        if (direction == "") direction = FROM
                        val results = response.body()?.features()
                        if (results != null && results.isNotEmpty()) {
                            val firstResult = results[0]
                            markLocation(firstResult)
                        } else {
                            val latitude = currentLocation!!.latitude
                            val longitude = currentLocation!!.longitude
                            when (direction) {
                                FROM -> {
                                    binding.fromTv.text = MY_LOCATION
                                    if (originMarker != null) {
                                        map.removeMarker(originMarker!!)
                                    }

                                    val drawable =
                                        ContextCompat.getDrawable(
                                            binding.root.context,
                                            R.drawable.ic_baseline_gps_red
                                        )
                                    val bitmap = convertDrawableToBitmap(drawable)
                                    val icon = IconFactory.getInstance(binding.root.context)
                                        .fromBitmap(bitmap!!)

                                    originMarker =
                                        map.addMarker(
                                            MarkerOptions().position(LatLng(latitude, longitude))
                                                .setIcon(icon)
                                        )

                                    originPosition = Point.fromLngLat(longitude, latitude)

                                }
                                TO -> {
                                    binding.toTv.text = MY_LOCATION

                                    if (destinationMarker != null) {
                                        map.removeMarker(destinationMarker!!)
                                    }

                                    val drawable =
                                        ContextCompat.getDrawable(
                                            binding.root.context,
                                            R.drawable.ic_baseline_gps_fixed_24
                                        )
                                    val bitmap = convertDrawableToBitmap(drawable)
                                    val icon = IconFactory.getInstance(binding.root.context)
                                        .fromBitmap(bitmap!!)

                                    destinationMarker =
                                        map.addMarker(
                                            MarkerOptions().position(LatLng(latitude, longitude))
                                                .setIcon(icon)
                                        )

                                    destinationPosition = Point.fromLngLat(longitude, latitude)

                                }
                            }
                            if (originPosition != null && destinationPosition != null && binding.fromTv.text.toString()
                                    .isNotEmpty() && binding.toTv.text.toString().isNotEmpty()
                            ) {
                                getRoute(originPosition!!, destinationPosition!!)
                            }else {
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(
                                            latitude,
                                            longitude
                                        ), 16.0
                                    )
                                )
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {

                }

            })
        } else {
            Toast.makeText(binding.root.context, YOUR_LOCATION_NOT_FOUND, Toast.LENGTH_SHORT).show()
        }
    }

    private fun markLocation(place: CarmenFeature) {
        val latitude = (place.geometry() as Point).latitude()
        val longitude = (place.geometry() as Point).longitude()

        when (direction) {
            FROM -> {
                binding.fromTv.text = place.placeName()
                if (originMarker != null) {
                    map.removeMarker(originMarker!!)
                }

                val drawable =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.ic_baseline_gps_red)
                val bitmap = convertDrawableToBitmap(drawable)
                val icon = IconFactory.getInstance(binding.root.context).fromBitmap(bitmap!!)

                originMarker =
                    map.addMarker(
                        MarkerOptions().position(LatLng(latitude, longitude)).setIcon(icon)
                    )

                originPosition = Point.fromLngLat(longitude, latitude)

            }
            TO -> {
                binding.toTv.text = place.placeName()
                if (destinationMarker != null) {
                    map.removeMarker(destinationMarker!!)
                }

                val drawable =
                    ContextCompat.getDrawable(
                        binding.root.context,
                        R.drawable.ic_baseline_gps_fixed_24
                    )
                val bitmap = convertDrawableToBitmap(drawable)
                val icon = IconFactory.getInstance(binding.root.context).fromBitmap(bitmap!!)

                destinationMarker =
                    map.addMarker(
                        MarkerOptions().position(LatLng(latitude, longitude)).setIcon(icon)
                    )

                destinationPosition = Point.fromLngLat(longitude, latitude)
            }
        }
        if (originPosition != null && destinationPosition != null && binding.toTv.text.toString()
                .isNotEmpty() && binding.fromTv.text.toString().isNotEmpty()
        ) {
            getRoute(originPosition!!, destinationPosition!!)
        } else {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        latitude,
                        longitude
                    ), 16.0
                )
            )
        }
    }

    private fun openPlacePicker() {
        var targetLatLng: LatLng? = null
        if (direction.isEmpty()) direction = FROM
        when (direction) {
            FROM -> {
                targetLatLng = originMarker?.position ?: LatLng(
                    currentLocation?.latitude ?: 41.311081,
                    currentLocation?.longitude ?: 69.240562
                )
            }
            TO -> {
                targetLatLng = destinationMarker?.position ?: originMarker?.position ?: LatLng(
                    currentLocation?.latitude ?: 41.311081,
                    currentLocation?.longitude ?: 69.240562
                )
            }
        }

        val intent = PlacePicker.IntentBuilder()
            .accessToken(Mapbox.getAccessToken()!!)
            .placeOptions(
                PlacePickerOptions.builder()
                    .statingCameraPosition(
                        CameraPosition.Builder()
                            .target(targetLatLng)
                            .zoom(16.0)
                            .build()
                    )
                    .build()
            )
            .build(activity as MainActivity)
        startActivityForResult(intent, PLACE_SELECTION_REQUEST_CODE)
    }

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
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
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap
        addFeaturesForAutoComplete()
        enableLocation()
    }

    private fun openAutoComplete(editTextId: Int) {
        direction = when (editTextId) {
            R.id.to_tv -> TO
            R.id.from_tv -> FROM
            else -> TO
        }
        val intent = PlaceAutocomplete.IntentBuilder()
            .accessToken(Mapbox.getAccessToken())
            .placeOptions(
                PlaceOptions.builder()
                    .backgroundColor(Color.parseColor("#EEEEEE"))
                    .limit(10)
                    .addInjectedFeature(chooseCF)
                    .addInjectedFeature(currentCF)
                    .build(PlaceOptions.MODE_CARDS)
            )
            .build(activity as MainActivity)
        startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE)

    }

    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(binding.root.context)) {
            initializeLocationEngine()
            initializeLocationLayer()
        } else {
            Dexter.withContext(binding.root.context)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(this)
                .check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine =
            LocationEngineProvider(binding.root.context).obtainBestLocationEngineAvailable()
        locationEngine!!.priority = LocationEnginePriority.BALANCED_POWER_ACCURACY
        locationEngine!!.activate()

        val lastLocation = locationEngine!!.lastLocation
        if (lastLocation != null) {
            currentLocation = lastLocation
            if (originLocation == null) {
                originLocation = lastLocation
                markCurrentLocation()
                if (networkHelper.isNetworkConnected()) {
                    openPlacePicker()
                }
            }
            setCameraPosition(lastLocation)
        } else {
            locationEngine!!.addLocationEngineListener(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationLayer() {
        locationLayerPlugin = LocationLayerPlugin(mapView, map, locationEngine)
        locationLayerPlugin!!.setLocationLayerEnabled(true)
        locationLayerPlugin!!.cameraMode = CameraMode.TRACKING
        locationLayerPlugin!!.renderMode = RenderMode.NORMAL
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    location.latitude,
                    location.longitude
                ), 16.0
            )
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_AUTOCOMPLETE && resultCode == Activity.RESULT_OK) {

            val place = PlaceAutocomplete.getPlace(data)
            if (place.id() == MAP_CHOOSE_ID) {
                openPlacePicker()
            } else if (place.id() == CURRENT_LOCATION_ID) {
                markCurrentLocation()
            } else {
                markLocation(place)
            }
        } else if (requestCode == PLACE_SELECTION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val place = PlacePicker.getPlace(data)
            place?.let { markLocation(it) }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine!!.requestLocationUpdates()
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            currentLocation = location
            if (originLocation == null) {
                originLocation = location
                markCurrentLocation()
                if (networkHelper.isNetworkConnected()) {
                    openPlacePicker()
                }
            }
        }
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder()
            .accessToken(Mapbox.getAccessToken())
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    if (response.isSuccessful) {
                        val currentRoute = response.body()?.routes()?.get(0)
                        if (navigationMapRoute != null) {
                            navigationMapRoute!!.removeRoute()
                        } else {
                            navigationMapRoute = NavigationMapRoute(null, mapView, map)
                        }
                        navigationMapRoute!!.addRoute(currentRoute)

                        // Animate Camera
                        val bounds = LatLngBounds.Builder()
                            .include(
                                LatLng(
                                    originPosition!!.latitude(),
                                    originPosition!!.longitude(),
                                )
                            )
                            .include(
                                LatLng(
                                    destinationPosition!!.latitude(),
                                    destinationPosition!!.longitude(), 100.0
                                )
                            ).build()
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
                            map.getCameraForLatLngBounds(
                                bounds,
                                intArrayOf(
                                    paddingHorizontal,
                                    paddingTop,
                                    paddingHorizontal,
                                    paddingBottom
                                )
                            )
                        map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 3000)
                        openBottomSheetDialog()
                    }
                }


                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {

                }

            })
    }


    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
        initializeLocationEngine()
        initializeLocationLayer()
    }

    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
        Toast.makeText(binding.root.context, "Пожалуйста, включите разрешение!", Toast.LENGTH_SHORT)
            .show()
    }

    override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {

    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        if (locationEngine != null) {
            locationEngine!!.requestLocationUpdates()
        }
        if (locationLayerPlugin != null) {
            locationLayerPlugin!!.onStart()
        }
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
        if (locationEngine != null) {
            locationEngine!!.removeLocationUpdates()
            locationEngine!!.removeLocationEngineListener(this)
        }
        if (locationLayerPlugin != null) {
            locationLayerPlugin!!.onStop()
        }
        mapView.onStop()
        navigationMapRoute = null
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
        if (locationEngine != null) {
            locationEngine!!.deactivate()
        }
        mapView.onDestroy()
    }

}