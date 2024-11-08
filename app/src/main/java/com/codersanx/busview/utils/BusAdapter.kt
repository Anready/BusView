package com.codersanx.busview.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.codersanx.busview.MainActivity
import com.codersanx.busview.R

class BusAdapter(activity: MainActivity, private val items: List<Bus>) :
    ArrayAdapter<Bus>(activity, 0, items) {

    private val mainActivity: MainActivity = activity

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view =
            convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)

        val item = items[position]

        val itemText = view.findViewById<TextView>(R.id.item_text)
        view.setOnClickListener {
            mainActivity.centerMapOnLocation(item.location)
            mainActivity.bottomSheetFragment.dismiss()
        }
        itemText.text = item.name

        return view
    }
}
