package com.example.ancs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter2(
    private var notifications: List<NotificationData>,
    private val itemClickListener: (NotificationData) -> Unit
) : ListAdapter<NotificationData, NotificationAdapter2.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(view, itemClickListener)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    class NotificationViewHolder(
        itemView: View,
        private val itemClickListener: (NotificationData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView? = itemView.findViewById(R.id.titleTextView)
        private val appNameTextView: TextView? = itemView.findViewById(R.id.appNameTextView)
        private val messageTextView: TextView? = itemView.findViewById(R.id.messageTextView)

        fun bind(notification: NotificationData) {
            titleTextView?.text = notification.attributeID.notificationAttributeIDTitle
            appNameTextView?.text = notification.displayName
            messageTextView?.text = notification.attributeID.notificationAttributeIDMessage
            itemView.setOnClickListener { itemClickListener(notification) }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationData>() {
        override fun areItemsTheSame(oldItem: NotificationData, newItem: NotificationData): Boolean {
            return oldItem.notificationUID.contentEquals(newItem.notificationUID)
        }

        override fun areContentsTheSame(oldItem: NotificationData, newItem: NotificationData): Boolean {
            return oldItem == newItem
        }
    }
}