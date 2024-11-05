package com.codersanx.busview

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.widget.TextView
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView

class ShowTimeBuses : BottomSheetDialogFragment() {

    var item: MutableList<String> = mutableListOf()  // Use MutableList to modify the list
    var name: String = "Stop"
    private lateinit var listView: ListView
    private lateinit var stopNameTextView: TextView
    private var adapter: ArrayAdapter<String>? = null  // Initialize adapter as nullable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.show_time_buses, container, false)
        
        stopNameTextView = view.findViewById(R.id.stop_name_text_view)
        // Initialize ListView
        listView = view.findViewById(R.id.my_list_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the adapter with the current item list and set it to the ListView
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, item)
        listView.adapter = adapter
        stopNameTextView.text = name
    }

    // Function to update the ListView's data
    fun updateView(newItems: List<String>, stopName: String) {
        name = stopName
        
        if (adapter != null) {
            item.clear()
            item.addAll(newItems)
            adapter?.notifyDataSetChanged()
        }
        if (!::stopNameTextView.isInitialized) {
           return
        }
        if(stopNameTextView != null) {
            stopNameTextView.text = stopName
        } // Update the stop name
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val mainAct = activity as MainActivity
        mainAct.selectedStopMarker?.icon = 
        ContextCompat.getDrawable(requireContext(), R.drawable.bus_stop)
    
        // Clear the selected marker
        mainAct.selectedStopMarker = null
    }
}
