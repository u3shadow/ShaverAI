package com.u3coding.shaver.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.u3coding.shaver.R
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.model.UiMessage

class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder>() {

    private val items = mutableListOf<UiMessage>()

    fun submitList(newItems: List<UiMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatMessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatMessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatMessageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ChatMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val row: LinearLayout = itemView.findViewById(R.id.messageRow)
        private val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)

        fun bind(message: UiMessage) {
            tvRole.text = message.role.name

            val content = if (message.role == Role.USER) {
                "${message.content} [env: ${message.wifiSsid ?: "unknown"}]"
            } else {
                message.content
            }
            tvContent.text = content

            if (message.role == Role.USER) {
                row.gravity = Gravity.END
                tvContent.setBackgroundResource(R.drawable.bg_bubble_user)
            } else {
                row.gravity = Gravity.START
                tvContent.setBackgroundResource(R.drawable.bg_bubble_assistant)
            }
        }
    }
}

