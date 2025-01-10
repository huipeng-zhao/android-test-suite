package com.example.ancs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(private val notifications: List<NotificationData>) :
    RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView? = itemView.findViewById(R.id.titleTextView)
        val appNameTextView: TextView? = itemView.findViewById(R.id.appNameTextView)
        val messageTextView: TextView? = itemView.findViewById(R.id.messageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.titleTextView?.text = notification.attributeID.notificationAttributeIDTitle
        holder.appNameTextView?.text = notification.displayName
        holder.messageTextView?.text = notification.attributeID.notificationAttributeIDMessage
    }

    override fun getItemCount(): Int {
        return notifications.size
    }
}
