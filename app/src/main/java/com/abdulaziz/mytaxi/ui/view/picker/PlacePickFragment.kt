package com.abdulaziz.mytaxi.ui.view.picker

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.model.MapPoint
import com.abdulaziz.mytaxi.databinding.FragmentPlacePickBinding
import com.abdulaziz.mytaxi.ui.view.map_screen.MapFragment
import com.abdulaziz.mytaxi.ui.view.map_screen.MapFragment.Companion.MAP_POINT_KEY
import com.abdulaziz.mytaxi.ui.view.map_screen.MapFragment.Companion.MAP_POINT_REQUEST_CODE
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.search.*
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.result.SearchResult
import kotlinx.coroutines.launch

class PlacePickFragment : Fragment() {

    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var searchEngine: SearchEngine
    private var _binding: FragmentPlacePickBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var routeType = MapFragment.ROUTE_TYPE_FROM
    private val TAG = "PlacePickFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeType = arguments?.getString(MapFragment.ROUTE_TYPE_KEY) ?: MapFragment.ROUTE_TYPE_FROM
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacePickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMapComponents()
        initUiComponents()
        onMapReady()
    }


    private fun initUiComponents() = with(binding) {
        fab.setOnClickListener {
            focusCurrentLocation()
        }
        nextIv.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                MAP_POINT_REQUEST_CODE,
                bundleOf(
                    MAP_POINT_KEY to
                            MapPoint(
                                routeType,
                                placeTv.text.toString(),
                                mapView.getMapboxMap().cameraState.center.latitude(),
                                mapView.getMapboxMap().cameraState.center.longitude()
                            )
                )
            )
            parentFragmentManager.popBackStack()
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
        placeAutocomplete = PlaceAutocomplete.create(getString(R.string.mapbox_access_token))
        searchEngine =
            SearchEngine.createSearchEngine(SearchEngineSettings(getString(R.string.mapbox_access_token)))
    }

    @SuppressLint("MissingPermission")
    private fun focusCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
            if (location != null) {
                val cameraPosition = CameraOptions.Builder()
                    .zoom(16.0)
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .build()

                mapView.getMapboxMap().setCamera(cameraPosition)
            }
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
            focusCurrentLocation()
        }
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
    }

    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }


    private val onMoveListener = object : OnMoveListener {
        @SuppressLint("Range")
        override fun onMoveBegin(detector: MoveGestureDetector) {
            binding.lottieView.playAnimation()
            binding.placeTv.text = getString(R.string.search)
            Log.d(TAG, "onMoveBegin: ")
        }

        @SuppressLint("Range")
        override fun onMove(detector: MoveGestureDetector): Boolean {
            Log.d(TAG, "onMove: ")
            return false
        }

        @SuppressLint("Range")
        override fun onMoveEnd(detector: MoveGestureDetector) {
            Log.d(TAG, "onMoveEnd: ")
            binding.lottieView.pauseAnimation()
            binding.lottieView.progress = 0.0f
            setPickedPlace()
        }
    }

    private fun setPickedPlace() = lifecycleScope.launch {
        val mapCenter = mapView.getMapboxMap().cameraState.center
        searchEngine.search(ReverseGeoOptions(center = mapCenter, limit = 1), searchCallback)
    }

    private val searchCallback = object : SearchCallback {
        override fun onError(e: Exception) {}
        override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) {
            if (results.isNotEmpty()) {
                val suggestion = results[0]
                binding.placeTv.text = suggestion.fullAddress
            } else {
                binding.placeTv.text = getString(R.string.unknown_place)
            }
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
        mapView.gestures.addOnMoveListener(onMoveListener)
        mapView.onDestroy()
    }

}