package com.u3coding.shaver.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.u3coding.shaver.R
import com.u3coding.shaver.ui.model.EnvConfigItem

class EnvConfigAdapter : RecyclerView.Adapter<EnvConfigAdapter.EnvConfigViewHolder>() {

    private val items = mutableListOf<EnvConfigItem>()

    fun submitList(newItems: List<EnvConfigItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnvConfigViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_env_config, parent, false)
        return EnvConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: EnvConfigViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class EnvConfigViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val row: LinearLayout = itemView.findViewById(R.id.envConfigRow)
        private val tvKey: TextView = itemView.findViewById(R.id.tvConfigKey)
        private val tvValue: TextView = itemView.findViewById(R.id.tvConfigValue)

        fun bind(item: EnvConfigItem) {
            tvKey.text = item.key
            tvValue.text = item.value
            row.setBackgroundResource(
                if (item.highlighted) R.drawable.bg_env_item_highlight else android.R.color.transparent
            )
        }
    }
}
