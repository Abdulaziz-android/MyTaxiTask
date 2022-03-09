package com.abdulaziz.mytaxi.ui.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.text.HtmlCompat
import com.abdulaziz.mytaxi.R
import com.abdulaziz.mytaxi.data.local.Trip
import com.abdulaziz.mytaxi.databinding.ItemBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso


class TripDetailsDialogFragment : BottomSheetDialogFragment() {

    var listener: OnBottomSheetListener? = null
    private var trip: Trip? = null
    private lateinit var itemBinding: ItemBottomsheetBinding
    private var saveButtonEnabled = false
    private var saveItem = false
    private var removeItem = false


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        itemBinding = ItemBottomsheetBinding.inflate(LayoutInflater.from(context))

        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.setContentView(itemBinding.root)

        itemBinding.apply {
            if (saveButtonEnabled) {
                prepareSaveButton()
            }else{
                materialButton.setOnClickListener { removeItem = true }
            }
            backCard.setOnClickListener {
                dismiss()
                listener?.onClosed(saveItem, removeItem)
            }
        }
        bottomSheetDialog.setOnShowListener {
            val dialog = it as BottomSheetDialog
            val parentLayout =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { it ->
                val behaviour = from(it)
                setupFullHeight(it)
                behaviour.state = STATE_EXPANDED
            }
            //this disables outside touch
            bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.touch_outside)
                ?.setOnClickListener(null)
            //this prevents dragging behavior
            (bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.layoutParams as CoordinatorLayout.LayoutParams).behavior =
                null
        }

        loadData()
    }

    private fun prepareSaveButton() {
        itemBinding.apply {
            materialButton.setBackgroundColor(Color.parseColor("#ECF2FD"))
            materialButton.setTextColor(Color.parseColor("#3F7BEB"))
            materialButton.setIconTintResource(R.color.blue_normal)
            materialButton.setIconResource(R.drawable.ic_outline_add_box_24)
            materialButton.setText(R.string.save)
            materialButton.setOnClickListener {
                saveItem = true
            }
        }
    }

    fun setSaveButton() {
        saveButtonEnabled = true
    }

    fun setTrip(tripEntity: Trip) {
        this.trip = tripEntity
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        listener?.onClosed(saveItem, removeItem)
    }

    private fun loadData() {
        itemBinding.apply {
            originNameTv.text = trip?.originName
            destinationNameTv.text = trip?.destinationName

            val details: String = getDriverDetails()
            driverDetailsTv.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(details, HtmlCompat.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(details)
            }

            Picasso.get()
                .load("https://freepngdownload.com/image/thumb/business-man-png-free-image-download-33.png")
                .into(driverAvatarIv)
        }
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }

    private fun getDriverDetails(): String {

        val rating = resources.getString(R.string.rating_driver)
        val travel_count = resources.getString(R.string.driver_travel_count)

        return "Рейтинг: $rating ⭐   Поездки: $travel_count"
    }

    override fun getTheme() = R.style.CustomBottomSheetDialogTheme

    fun setOnChooseReasonListener(listener: OnBottomSheetListener) {
        this.listener = listener
    }

    interface OnBottomSheetListener {
        fun onClosed(isSaved: Boolean, isRemoved: Boolean)
    }
}