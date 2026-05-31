package com.hsukqi.aospperfecticons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

sealed class IconListRow {
    data class Header(val title: String) : IconListRow()
    data class Item(
        val entry: IconEntry,
        val isMapped: Boolean,
        val isInstalled: Boolean
    ) : IconListRow()
}

class IconAdapter(
    private val rows: MutableList<IconListRow>,
    private val resolveDrawableId: (String) -> Int,
    private val onItemClicked: (IconEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is IconListRow.Header -> VIEW_TYPE_HEADER
            is IconListRow.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_icon_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_icon, parent, false)
                IconViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is IconListRow.Header -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.titleView.text = row.title
            }
            is IconListRow.Item -> {
                val iconHolder = holder as IconViewHolder
                val item = row.entry
                val context = iconHolder.itemView.context
                iconHolder.nameView.text = item.name

                val drawableId = resolveDrawableId(item.drawable)
                if (drawableId != 0) {
                    iconHolder.iconView.setImageResource(drawableId)
                } else {
                    iconHolder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                }

                if (row.isMapped && row.isInstalled) {
                    val bg = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0)
                    iconHolder.cardView.setCardBackgroundColor(bg)
                    iconHolder.itemView.alpha = 1f
                    iconHolder.itemView.isClickable = true
                    iconHolder.itemView.isFocusable = true
                    iconHolder.itemView.setOnClickListener { onItemClicked(item) }
                } else if (row.isMapped) {
                    val bg = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0)
                    iconHolder.cardView.setCardBackgroundColor(bg)
                    iconHolder.itemView.alpha = 0.62f
                    iconHolder.itemView.isClickable = true
                    iconHolder.itemView.isFocusable = true
                    iconHolder.itemView.setOnClickListener { onItemClicked(item) }
                } else {
                    val bg = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0)
                    iconHolder.cardView.setCardBackgroundColor(bg)
                    iconHolder.itemView.alpha = 0.62f
                    iconHolder.itemView.isClickable = false
                    iconHolder.itemView.isFocusable = false
                    iconHolder.itemView.setOnClickListener(null)
                }
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    fun isHeader(position: Int): Boolean = rows[position] is IconListRow.Header

    fun submitRows(newRows: List<IconListRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.iconCard)
        val iconView: ImageView = itemView.findViewById(R.id.iconImage)
        val nameView: TextView = itemView.findViewById(R.id.iconName)
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.sectionTitle)
    }
}
