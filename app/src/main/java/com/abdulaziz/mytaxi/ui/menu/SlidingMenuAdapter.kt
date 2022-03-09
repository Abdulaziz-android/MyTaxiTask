package com.abdulaziz.mytaxi.ui.menu


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abdulaziz.mytaxi.databinding.MenuElementBinding

class SlideMenuAdapter(
    val menuElements: MutableList<SlideMenuItem>,
    val listener: OnMenuItemClickListener
) :
    RecyclerView.Adapter<SlideMenuAdapter.SVH>() {

    inner class SVH(val itemBinding: MenuElementBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun onBind(item: SlideMenuItem, position: Int) {
            itemBinding.apply {
                titleTv.text = item.title
                iconIv.setImageResource(item.image)

                when (position) {
                    0 -> {
                        root.setOnClickListener {
                            listener.onItemClicked()
                        }
                    }
                    2 -> divider.visibility = View.GONE
                }
            }
        }

        /*  private fun getFragmentByPosition(position: Int): Fragment {
              return when (position) {
                  0 -> MainFragment()
                  1 -> TripFragment()
                  2 -> MainFragment()
                  else -> MainFragment()
              }
          }*/
    }

    override fun getItemCount() = menuElements.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SVH {
        return SVH(MenuElementBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SVH, position: Int) {
        holder.onBind(menuElements[position], position)
    }

    interface OnMenuItemClickListener {
        fun onItemClicked()
    }

}

