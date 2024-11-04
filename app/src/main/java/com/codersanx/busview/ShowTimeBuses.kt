package com.codersanx.busview

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView

class ShowTimeBuses : BottomSheetDialogFragment() {

    var item: MutableList<String> = mutableListOf()  // Use MutableList to modify the list
    private lateinit var listView: ListView
    private var adapter: ArrayAdapter<String>? = null  // Initialize adapter as nullable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.show_time_buses, container, false)

        // Initialize ListView
        listView = view.findViewById(R.id.my_list_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the adapter with the current item list and set it to the ListView
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, item)
        listView.adapter = adapter
    }

    // Function to update the ListView's data
    fun updateView(newItems: List<String>) {
        if (adapter != null) {  // Check if adapter is initialized
            item.clear()                 // Clear existing items
            item.addAll(newItems)        // Add new items
            adapter?.notifyDataSetChanged() // Notify adapter of data change
        }
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
