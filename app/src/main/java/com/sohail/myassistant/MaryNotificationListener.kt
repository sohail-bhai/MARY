package com.sohail.myassistant

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale

class MaryNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MaryNotificationStore.listener = this
        refreshWhatsAppNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MaryNotificationStore.listener = null
        MaryNotificationStore.resetMessages()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        MaryNotificationStore.addMessages(parseWhatsAppNotification(sbn))
    }

    fun refreshWhatsAppNotifications() {
        val refreshedMessages = activeNotifications
            .orEmpty()
            .sortedBy { it.postTime }
            .flatMap(::parseWhatsAppNotification)

        MaryNotificationStore.replaceMessages(refreshedMessages)
    }

    private fun parseWhatsAppNotification(
        sbn: StatusBarNotification
    ): List<WhatsAppMessage> {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return emptyList()

        val notification = sbn.notification
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return emptyList()
        }

        val extras = runCatching { notification.extras }.getOrNull() ?: return emptyList()
        val title = readText(extras, Notification.EXTRA_TITLE) ?: WHATSAPP_TITLE
        val conversationTitle = readText(extras, Notification.EXTRA_CONVERSATION_TITLE)
        val subText = readText(extras, Notification.EXTRA_SUB_TEXT)
            ?.takeUnless(::isWhatsAppTitle)
        val groupName = conversationTitle
            ?.takeUnless(::isWhatsAppTitle)
            ?: subText?.takeUnless { it.equals(title, ignoreCase = true) }
        val isGroupConversation = runCatching {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        }.getOrDefault(false)

        val messageBundles = runCatching {
            extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES).orEmpty() +
                    extras.getParcelableArray(Notification.EXTRA_MESSAGES).orEmpty()
        }.getOrNull()

        val styledMessages = messageBundles
            .orEmpty()
            .mapNotNull { item ->
                val bundle = item as? Bundle ?: return@mapNotNull null
                parseMessageBundle(
                    bundle = bundle,
                    sbn = sbn,
                    title = title,
                    groupName = groupName,
                    isGroupConversation = isGroupConversation
                )
            }
            .sortedBy { it.time }

        if (styledMessages.isNotEmpty()) return styledMessages

        val fallbackMessage = readText(extras, Notification.EXTRA_BIG_TEXT)
            ?: readText(extras, Notification.EXTRA_TEXT)
            ?: return emptyList()

        if (isWhatsAppSummary(title, fallbackMessage)) return emptyList()

        val senderAndBody = if (isWhatsAppTitle(title)) {
            parseSenderPrefix(fallbackMessage)
        } else {
            null
        }
        val senderName = senderAndBody?.first ?: title
        val message = senderAndBody?.second ?: fallbackMessage
        val time = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        return listOf(
            createWhatsAppMessage(
                title = title,
                groupName = groupName,
                senderName = senderName,
                message = message,
                time = time,
                isGroupConversation = isGroupConversation
            )
        )
    }

    private fun parseMessageBundle(
        bundle: Bundle,
        sbn: StatusBarNotification,
        title: String,
        groupName: String?,
        isGroupConversation: Boolean
    ): WhatsAppMessage? {
        return try {
            val message = readText(bundle, MESSAGE_TEXT_KEY) ?: return null
            if (isWhatsAppSummary(title, message)) return null

            val senderName = readText(bundle, MESSAGE_SENDER_KEY) ?: title
            val messageTime = bundle.getLong(MESSAGE_TIME_KEY, sbn.postTime)
                .takeIf { it > 0L }
                ?: sbn.postTime.takeIf { it > 0L }
                ?: System.currentTimeMillis()

            createWhatsAppMessage(
                title = title,
                groupName = groupName,
                senderName = senderName,
                message = message,
                time = messageTime,
                isGroupConversation = isGroupConversation
            )
        } catch (_: RuntimeException) {
            // One malformed parcelable should not prevent other notifications from being read.
            null
        }
    }

    private fun createWhatsAppMessage(
        title: String,
        groupName: String?,
        senderName: String,
        message: String,
        time: Long,
        isGroupConversation: Boolean
    ): WhatsAppMessage {
        val isGroup = isGroupConversation ||
                (!groupName.isNullOrBlank() && !groupName.equals(senderName, ignoreCase = true))

        val chatName = if (isGroup) {
            groupName ?: title
        } else {
            title.takeUnless(::isWhatsAppTitle) ?: senderName
        }

        return WhatsAppMessage(
            chatName = chatName,
            senderName = senderName,
            message = message,
            time = time,
            isGroup = isGroup
        )
    }

    private fun readText(bundle: Bundle, key: String): String? = runCatching {
        bundle.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun parseSenderPrefix(message: String): Pair<String, String>? {
        val colonIndex = message.indexOf(':')
        if (colonIndex !in 1..MAX_SENDER_PREFIX_LENGTH) return null

        val sender = message.substring(0, colonIndex).trim()
        val body = message.substring(colonIndex + 1).trim()

        if (sender.isEmpty() || body.isEmpty() || !sender.first().isLetter()) return null
        if (sender.any { !it.isLetter() && !it.isWhitespace() && it !in "'.-" }) return null

        return sender to body
    }

    private fun isWhatsAppSummary(title: String, message: String): Boolean {
        if (!isWhatsAppTitle(title)) return false

        val normalizedMessage = message.lowercase(Locale.ROOT)
        return SUMMARY_PHRASES.any { phrase -> normalizedMessage.contains(phrase) }
    }

    private fun isWhatsAppTitle(title: String): Boolean =
        title.trim().lowercase(Locale.ROOT) in WHATSAPP_APP_TITLES

    private companion object {
        const val WHATSAPP_TITLE = "WhatsApp"
        const val MESSAGE_TEXT_KEY = "text"
        const val MESSAGE_SENDER_KEY = "sender"
        const val MESSAGE_TIME_KEY = "time"
        const val MAX_SENDER_PREFIX_LENGTH = 80

        val WHATSAPP_APP_TITLES = setOf("whatsapp", "whatsapp business")
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val SUMMARY_PHRASES = listOf(
            "messages from",
            "new messages",
            "chats",
            "checking for new messages"
        )
    }
}
