package com.abdulaziz.mytaxi.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdulaziz.mytaxi.data.local.TripOfDay
import com.abdulaziz.mytaxi.databinding.ItemTripParentBinding

class TripHistoryAdapter(val listener:TripChildAdapter.OnItemClickListener) :RecyclerView.Adapter<TripHistoryAdapter.THVH>(){

    private val TAG = "TripHistoryAdapter"
    var list: List<TripOfDay> = arrayListOf()

    inner class THVH(val itemBinding:ItemTripParentBinding):RecyclerView.ViewHolder(itemBinding.root){

        fun onBind(trip:TripOfDay){
            itemBinding.apply {
                dateTv.text = trip.date
                trip.list?.reverse()
                val adapter = TripChildAdapter(trip.list?: emptyList(), listener)
                rv.adapter = adapter
            }
        }

    }

    fun submitList(list: List<TripOfDay>){
        this.list = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): THVH {
        return THVH(ItemTripParentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: THVH, position: Int) {
        holder.onBind(list[position])
    }

    override fun getItemCount(): Int = list.size
}