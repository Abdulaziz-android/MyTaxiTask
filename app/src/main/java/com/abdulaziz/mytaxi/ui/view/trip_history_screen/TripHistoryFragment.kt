package com.abdulaziz.mytaxi.ui.view.trip_history_screen

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.abdulaziz.mytaxi.ui.view.main.MainView
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.data.local.TripDao
import com.abdulaziz.mytaxi.data.local.TripOfDay
import com.abdulaziz.mytaxi.databinding.FragmentTripHistoryBinding
import com.abdulaziz.mytaxi.ui.adapters.TripChildAdapter
import com.abdulaziz.mytaxi.ui.adapters.TripHistoryAdapter
import com.abdulaziz.mytaxi.ui.view.trip_details_screen.TripDetailsFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TripHistoryFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = TripHistoryAdapter(object : TripChildAdapter.OnItemClickListener {
            override fun onItemClicked(trip: Trip) {
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        TripDetailsFragment::class.java,
                        bundleOf(Pair("trip", trip))
                    ).addToBackStack("history").commit()
            }
        })
    }

    private var _binding: FragmentTripHistoryBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var tripDao: TripDao
    private lateinit var adapter: TripHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripHistoryBinding.inflate(layoutInflater, container, false)
        (activity as MainView?)?.hideToolbar()

        setUI()
        binding.backIv.setOnClickListener { (activity as MainView?)?.backPressed() }

        return binding.root
    }

    private fun setUI() {
        val allTrips = tripDao.getAllTrips()
        val list = allTrips as ArrayList<TripOfDay>
        list.reverse()
        adapter.submitList(list)
        binding.rv.adapter = adapter
    }

}