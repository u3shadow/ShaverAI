package com.u3coding.shaver.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.u3coding.shaver.R
import com.u3coding.shaver.ui.model.RuleDisplayItem

class RuleListAdapter : RecyclerView.Adapter<RuleListAdapter.RuleViewHolder>() {

    private val items = mutableListOf<RuleDisplayItem>()

    fun submitList(newItems: List<RuleDisplayItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return RuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class RuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOperation: TextView = itemView.findViewById(R.id.tvRuleOperation)
        private val tvValue: TextView = itemView.findViewById(R.id.tvRuleValue)

        fun bind(item: RuleDisplayItem) {
            tvOperation.text = item.operation
            tvValue.text = item.value
        }
    }
}
