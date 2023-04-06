package com.abdulaziz.mytaxi.ui.view.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.model.MapPoint
import com.abdulaziz.mytaxi.databinding.FragmentSearchPlaceBinding
import com.abdulaziz.mytaxi.utils.hideKeyboard
import com.abdulaziz.mytaxi.utils.lastKnownLocation
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.search.autofill.AddressAutofill
import com.mapbox.search.autofill.AddressAutofillOptions
import com.mapbox.search.autofill.AddressAutofillSuggestion
import com.mapbox.search.autofill.Query
import com.mapbox.search.ui.adapter.autofill.AddressAutofillUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView
import kotlinx.coroutines.launch

class SearchPlaceFragment : Fragment() {

    private var _binding: FragmentSearchPlaceBinding? = null
    private val binding get() = _binding!!

    private lateinit var addressAutofill: AddressAutofill

    private lateinit var searchResultsView: SearchResultsView
    private lateinit var addressAutofillUiAdapter: AddressAutofillUiAdapter

    private lateinit var queryEditText: EditText

    private lateinit var apartmentEditText: EditText
    private lateinit var cityEditText: EditText
    private lateinit var stateEditText: EditText
    private lateinit var zipEditText: EditText
    private lateinit var fullAddress: TextView
    private lateinit var pinCorrectionNote: TextView
    private lateinit var mapView: MapView
    private lateinit var mapPin: View
    private lateinit var mapboxMap: MapboxMap

    private var ignoreNextMapIdleEvent: Boolean = false
    private var ignoreNextQueryTextUpdate: Boolean = false

    private val TAG = "SearchPlaceFragment"
    private var routeType = "from"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeType = arguments?.getString(ROUTE_TYPE_KEY) ?: "from"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addressAutofill = AddressAutofill.create(getString(R.string.mapbox_access_token))
        initUiComponents()
        setupUiComponents()
    }

    private fun initUiComponents() = with(binding) {
        queryEditText = queryText
        apartmentEditText = addressApartment
        cityEditText = addressCity
        stateEditText = addressState
        zipEditText = addressZip
        fullAddress = fullAddressTv
        pinCorrectionNote = pinCorrectionNoteTv

        searchResultsView = searchResultsV

        mapPin = mapPinIv
        mapView = map
        mapboxMap = mapView.getMapboxMap()
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)

        pinBtn.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                MAP_POINT_REQUEST_CODE,
                bundleOf(
                    MAP_POINT_KEY to
                            MapPoint(
                                routeType,
                                binding.addressCity.text.toString(),
                                mapboxMap.cameraState.center.latitude(),
                                mapboxMap.cameraState.center.longitude()
                            )
                )
            )
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupUiComponents() {
        mapboxMap.addOnMapIdleListener {
            if (ignoreNextMapIdleEvent) {
                ignoreNextMapIdleEvent = false
                return@addOnMapIdleListener
            }

            val mapCenter = mapboxMap.cameraState.center
            findAddress(mapCenter)
        }
        searchResultsView.initialize(
            SearchResultsView.Configuration(
                commonConfiguration = CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
            )
        )

        addressAutofillUiAdapter = AddressAutofillUiAdapter(
            view = searchResultsView,
            addressAutofill = addressAutofill
        )

        LocationEngineProvider.getBestLocationEngine(requireContext())
            .lastKnownLocation(requireContext()) { point ->
                point?.let {
                    mapView.getMapboxMap().setCamera(
                        CameraOptions.Builder()
                            .center(point)
                            .zoom(9.0)
                            .build()
                    )
                    ignoreNextMapIdleEvent = true
                }
            }

        addressAutofillUiAdapter.addSearchListener(object : AddressAutofillUiAdapter.SearchListener {

            override fun onSuggestionSelected(suggestion: AddressAutofillSuggestion) {
                showAddressAutofillSuggestion(
                    suggestion,
                    fromReverseGeocoding = false,
                )
            }

            override fun onSuggestionsShown(suggestions: List<AddressAutofillSuggestion>) {
                // Nothing to do
            }

            override fun onError(e: Exception) {
                // Nothing to do
            }
        })

        queryEditText.addTextChangedListener(object : TextWatcher {

            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                if (ignoreNextQueryTextUpdate) {
                    ignoreNextQueryTextUpdate = false
                    return
                }

                val query = Query.create(text.toString())
                if (query != null) {
                    lifecycleScope.launch {
                        addressAutofillUiAdapter.search(query)
                    }
                }
                searchResultsView.isVisible = query != null
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // Nothing to do
            }

            override fun afterTextChanged(s: Editable) {
                // Nothing to do
            }
        })

    }


    private fun findAddress(point: Point) {
        Log.d(TAG, "findAddress: $point")
        lifecycleScope.launch {
            val response = addressAutofill.suggestions(point, AddressAutofillOptions())
            response.onValue { suggestions ->
                Log.d(TAG, "findAddress: ${suggestions.size}")
                if (suggestions.isEmpty()) {
                    Log.d(TAG, "findAddress: empty")
                } else {
                    showAddressAutofillSuggestion(
                        suggestions.first(),
                        fromReverseGeocoding = true
                    )
                }
            }.onError { error ->
                Log.d(TAG, "Test. $error", error)
            }
        }
    }

    private fun showAddressAutofillSuggestion(
        suggestion: AddressAutofillSuggestion,
        fromReverseGeocoding: Boolean
    ) {
        val address = suggestion.result().address
        cityEditText.setText(address.place)
        stateEditText.setText(address.region)
        zipEditText.setText(address.postcode)

        fullAddress.isVisible = true
        fullAddress.text = suggestion.formattedAddress

        pinCorrectionNote.isVisible = true

        if (!fromReverseGeocoding) {
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(suggestion.coordinate)
                    .zoom(16.0)
                    .build()
            )
            ignoreNextMapIdleEvent = true
            mapPin.isVisible = true
            binding.pinBtn.isVisible = true
        }

        ignoreNextQueryTextUpdate = true
        queryEditText.setText(
            listOfNotNull(
                address.houseNumber,
                address.street
            ).joinToString()
        )
        queryEditText.clearFocus()

        searchResultsView.isVisible = false
        searchResultsView.hideKeyboard()
    }
/*
    private companion object {
        const val PERMISSIONS_REQUEST_LOCATION = 0
    }*/

    companion object {
        const val MAP_POINT_REQUEST_CODE = "point_request_code"
        const val MAP_POINT_KEY = "map_point_key"
        const val ROUTE_TYPE_KEY = "map_point_key"
    }
}