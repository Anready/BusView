package com.codersanx.busview

import android.content.Intent
import android.net.Uri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.widget.TextView
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import com.codersanx.busview.models.Bus
import com.codersanx.busview.adapters.BusAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ShowTimeBuses : BottomSheetDialogFragment() {

    private var item: MutableList<Bus> = mutableListOf()  // Use MutableList to modify the list
    private var name: String = "Stop"
    private var url: Uri = Uri.parse("geo:0,0")
    private lateinit var listView: ListView
    private lateinit var stopNameTextView: TextView
    private lateinit var openStopOnMap: ImageView
    private lateinit var centerOnStop: FloatingActionButton
    private var adapter: BusAdapter? = null  // Initialize adapter as nullable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.show_time_buses, container, false)
        
        stopNameTextView = view.findViewById(R.id.stop_name_text_view)
        openStopOnMap = view.findViewById(R.id.imageView)
        centerOnStop = view.findViewById(R.id.fab)
        // Initialize ListView
        listView = view.findViewById(R.id.my_list_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val mainAct = activity as MainActivity
            val maxHeight = (resources.displayMetrics.heightPixels * mainAct.sharedPreferences.getInt("percent", 45)/100).toInt()
            it.layoutParams.height = maxHeight
            behavior.peekHeight = maxHeight
            it.requestLayout()
        }

        // Initialize the adapter with the current item list and set it to the ListView
        adapter = BusAdapter(activity as MainActivity, item)
        listView.adapter = adapter

        stopNameTextView.text = name
        openStopOnMap.setOnClickListener {
            val mapIntent = Intent(Intent.ACTION_VIEW, url)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }

        centerOnStop.setOnClickListener {
            val act = activity as MainActivity
            if (act.mapControl.selectedStopMarker != null) {
                act.mapControl.centerMapOnLocation(act.mapControl.selectedStopMarker!!.position)
            }
        }
    }

    // Function to update the ListView's data
    fun updateView(newItems: List<Bus>, stopName: String, url: Uri) {
        name = stopName
        this.url = url
        
        if (adapter != null) {
            item.clear()
            item.addAll(newItems)
            adapter?.notifyDataSetChanged()
        } else {
            item = newItems.toMutableList()
        }

        if (!::stopNameTextView.isInitialized) {
           return
        }

        stopNameTextView.text = stopName
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val mainAct = activity as MainActivity
        mainAct.mapControl.selectedStopMarker?.icon =
        ContextCompat.getDrawable(requireContext(), R.drawable.bus_stop)
    
        // Clear the selected marker
        mainAct.mapControl.selectedStopMarker = null
    }
}
