package com.abdulaziz.mytaxi.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.databinding.ItemTripChildBinding

class TripChildAdapter(val list: List<Trip>, val listener:OnItemClickListener) :RecyclerView.Adapter<TripChildAdapter.TChVH>(){

    inner class TChVH(val itemBinding:ItemTripChildBinding):RecyclerView.ViewHolder(itemBinding.root){

        private val TAG = "TripChildAdapter"

        fun onBind(trip: Trip){
            itemBinding.apply {
                originNameTv.text = trip.originName
                destinationNameTv.text = trip.destinationName
                detailsTv.text = "${trip.details} • 12 900 som"
                root.setOnClickListener {
                    listener.onItemClicked(trip)
                }
                Log.d(TAG, "onBind: ")
            }

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TChVH {
        return TChVH(ItemTripChildBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: TChVH, position: Int) {
        holder.onBind(list[position])
    }

    override fun getItemCount(): Int = list.size

    interface OnItemClickListener{
        fun onItemClicked(trip: Trip)
    }
}