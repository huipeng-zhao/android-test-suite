package com.example.ancs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.internal.app.AbstractMultiProfilePagerAdapter.EmptyState.ClickListener

class NotificationAdapter(
    private val notifications: List<NotificationData>,
    private val itemClickListener: (NotificationData) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(view, itemClickListener)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    override fun getItemCount(): Int = notifications.size

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
}
