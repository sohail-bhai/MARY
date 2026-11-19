package com.sohail.myassistant

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MaryNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MaryNotificationStore.listener = this
        refreshWhatsAppNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MaryNotificationStore.listener = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        saveWhatsAppNotification(sbn)
    }

    fun refreshWhatsAppNotifications() {
        MaryNotificationStore.resetMessages()

        val notifications = activeNotifications ?: return

        for (notification in notifications) {
            saveWhatsAppNotification(notification)
        }
    }

    private fun isWhatsAppSummary(title: String, message: String): Boolean {
        val cleanTitle = title.lowercase()
        val cleanMessage = message.lowercase()

        return cleanTitle == "whatsapp" &&
                (
                        cleanMessage.contains("messages from") ||
                                cleanMessage.contains("new messages") ||
                                cleanMessage.contains("chats") ||
                                cleanMessage.contains("checking for new messages")
                        )
    }

    private fun saveWhatsAppNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        // Skip WhatsApp grouped summary notification
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val extras = sbn.notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            ?: "WhatsApp"

        val conversationTitle = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()

        val subText = extras
            .getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?.toString()
            ?.trim()

        val groupNameCandidate = when {
            !conversationTitle.isNullOrBlank() -> conversationTitle
            !subText.isNullOrBlank() && subText != "WhatsApp" -> subText
            else -> null
        }

        val messageArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        var foundMessages = false

        if (messageArray != null) {
            for (item in messageArray) {
                val bundle = item as? Bundle ?: continue

                val senderFromBundle = bundle
                    .getCharSequence("sender")
                    ?.toString()
                    ?.trim()

                val text = bundle
                    .getCharSequence("text")
                    ?.toString()
                    ?.trim()

                if (!text.isNullOrBlank()) {
                    if (isWhatsAppSummary(title, text)) return

                    val senderName = senderFromBundle ?: title
                    val chatName = groupNameCandidate ?: title

                    val isGroup = groupNameCandidate != null &&
                            !groupNameCandidate.equals(senderName, ignoreCase = true)

                    MaryNotificationStore.addMessage(
                        chatName = chatName,
                        senderName = senderName,
                        message = text,
                        time = sbn.postTime,
                        isGroup = isGroup
                    )

                    foundMessages = true
                }
            }
        }

        if (!foundMessages) {
            val text = extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.trim()

            val bigText = extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()
                ?.trim()

            var message = bigText ?: text ?: "New WhatsApp message"

            if (isWhatsAppSummary(title, message)) {
                return
            }

            var chatName = title
            var senderName = title
            var isGroup = false

            // Example group notification text:
            // Dharmik: send notes
            if (message.contains(":") && title != "WhatsApp") {
                val parts = message.split(":", limit = 2)

                if (parts.size == 2) {
                    senderName = parts[0].trim()
                    message = parts[1].trim()
                    chatName = groupNameCandidate ?: title
                    isGroup = true
                }
            } else if (groupNameCandidate != null) {
                chatName = groupNameCandidate
                senderName = title
                isGroup = true
            }

            MaryNotificationStore.addMessage(
                chatName = chatName,
                senderName = senderName,
                message = message,
                time = sbn.postTime,
                isGroup = isGroup
            )
        }
    }
}