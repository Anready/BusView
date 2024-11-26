package com.codersanx.busview.adapters

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import com.codersanx.busview.R

class SelectRouteAdapter(
    private val context: Context,
    names: List<String>?,
    private val sharedPreferences: SharedPreferences
) : BaseAdapter(), Filterable {

    private var sortedNames: MutableList<String> = names!!.toMutableList()


    override fun getCount(): Int = sortedNames.size

    override fun getItem(position: Int): Any = sortedNames[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.route_item, parent, false)

        val routeName = sortedNames[position]
        var isStarred = sharedPreferences.getBoolean("route$routeName", false)

        val textView = view.findViewById<TextView>(R.id.textView)
        textView.text = routeName

        val star = view.findViewById<ImageView>(R.id.star)
        star.setImageResource(if (isStarred) R.drawable.star_on else R.drawable.star)

        star.setOnClickListener {
            isStarred = !isStarred
            sharedPreferences.edit().putBoolean("route$routeName", isStarred).apply()

            sortByStarred()
            notifyDataSetChanged()
        }

        return view
    }

    fun sortByStarred() {
        if (sortedNames.isEmpty()) return
        sortedNames.sortWith { name1, name2 ->
            val star1 = sharedPreferences.getBoolean("route$name1", false)
            val star2 = sharedPreferences.getBoolean("route$name2", false)
            when {
                star1 && !star2 -> -1
                !star1 && star2 -> 1
                else -> name1.compareTo(name2)
            }
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()

                if (constraint.isNullOrEmpty()) {
                    filterResults.values = sortedNames
                    filterResults.count = sortedNames.size
                } else {
                    val filteredList = sortedNames.filter { it.contains(constraint, ignoreCase = true) }
                    filterResults.values = filteredList
                    filterResults.count = filteredList.size
                }

                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                @Suppress("UNCHECKED_CAST")
                sortedNames = results?.values as? MutableList<String> ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

}

