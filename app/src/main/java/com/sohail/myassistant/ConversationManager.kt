package com.sohail.myassistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import java.util.Locale

data class PendingChatOption(
    val chatName: String,
    val displayName: String
)

object MaryConversationMemory {
    var pendingPerson: String? = null
    var pendingChatOptions: List<PendingChatOption> = emptyList()
    var selectedChatName: String? = null

    fun clearChatSelection() {
        pendingPerson = null
        pendingChatOptions = emptyList()
        selectedChatName = null
    }

    fun setPendingChatSelection(
        person: String,
        options: List<PendingChatOption>
    ) {
        pendingPerson = person
        pendingChatOptions = options
        selectedChatName = null
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun hasPendingChatSelection(): Boolean {
        return pendingPerson != null && pendingChatOptions.size > 1
    }

    fun hasPreviousChatOptions(): Boolean {
        return pendingPerson != null && pendingChatOptions.isNotEmpty()
    }

    fun matchChatSelection(text: String): PendingChatOption? {
        val input = normalize(text)
        val tokens = input.split(" ")

        val groupOptions = pendingChatOptions.filter {
            !it.displayName.equals("Direct message", ignoreCase = true)
        }

        if (
            input.contains("direct") ||
            input.contains("dm") ||
            input.contains("personal") ||
            input.contains("private")
        ) {
            return pendingChatOptions.firstOrNull {
                it.displayName.equals("Direct message", ignoreCase = true)
            }
        }

        if (
            input.contains("group 1") ||
            input.contains("grp 1") ||
            input.contains("group one") ||
            input.contains("grp one") ||
            input.contains("first group")
        ) {
            return groupOptions.getOrNull(0)
        }

        if (
            input.contains("group 2") ||
            input.contains("grp 2") ||
            input.contains("group two") ||
            input.contains("grp two") ||
            input.contains("second group")
        ) {
            return groupOptions.getOrNull(1)
        }

        if (
            input.contains("group 3") ||
            input.contains("grp 3") ||
            input.contains("group three") ||
            input.contains("grp three") ||
            input.contains("third group")
        ) {
            return groupOptions.getOrNull(2)
        }

        val optionWords = listOf(
            listOf("1", "one", "first"),
            listOf("2", "two", "second"),
            listOf("3", "three", "third"),
            listOf("4", "four", "fourth"),
            listOf("5", "five", "fifth")
        )

        for ((index, words) in optionWords.withIndex()) {
            val matched = words.any { word ->
                tokens.contains(word) ||
                        input.contains("option $word") ||
                        input.contains("number $word")
            }

            if (matched) {
                val option = pendingChatOptions.getOrNull(index)
                if (option != null) return option
            }
        }

        return pendingChatOptions.firstOrNull { option ->
            val display = normalize(option.displayName)
            val chat = normalize(option.chatName)

            input.contains(display) ||
                    input.contains(chat) ||
                    display.contains(input) ||
                    chat.contains(input)
        }
    }
}

fun sendMediaButton(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val keyDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    val keyUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)

    audioManager.dispatchMediaKeyEvent(keyDown)
    audioManager.dispatchMediaKeyEvent(keyUp)
}

fun openWhatsApp(context: Context) {
    val whatsappIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
    val whatsappBusinessIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")

    val launchIntent = whatsappIntent ?: whatsappBusinessIntent

    if (launchIntent != null) {
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    }
}

fun openNotificationAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

fun readLatestWhatsAppNotification(): String {
    MaryNotificationStore.listener?.refreshWhatsAppNotifications()
    return MaryNotificationStore.latestWhatsAppMessage
}

fun countWhatsAppMessages(): String {
    MaryNotificationStore.listener?.refreshWhatsAppNotifications()
    return MaryNotificationStore.countResponse()
}

fun listWhatsAppSenders(): String {
    MaryNotificationStore.listener?.refreshWhatsAppNotifications()
    return MaryNotificationStore.senderListResponse()
}

fun readPersonWhatsAppMessage(spokenText: String): String {
    MaryNotificationStore.listener?.refreshWhatsAppNotifications()

    val person = MaryNotificationStore.findSenderInText(spokenText)

    if (person == null) {
        return "Tell me whose message you want to read."
    }

    val options = MaryNotificationStore.getChatOptionsForPerson(person)

    if (options.isEmpty()) {
        MaryConversationMemory.clearChatSelection()
        return "No messages found from $person."
    }

    if (options.size == 1) {
        MaryConversationMemory.setPendingChatSelection(
            person = person,
            options = options
        )

        val onlyOption = options.first()
        val messages = MaryNotificationStore.getPersonMessagesFromChat(
            person = person,
            chatName = onlyOption.chatName
        )

        return MaryReadingSession.start(
            person = person,
            source = onlyOption.displayName,
            messageList = messages
        )
    }

    MaryConversationMemory.setPendingChatSelection(
        person = person,
        options = options
    )

    val optionText = options.mapIndexed { index, option ->
        "${index + 1}. ${option.displayName}"
    }.joinToString(". ")

    return "$person sent messages in ${options.size} places. $optionText. Which one do you want?"
}

fun readSelectedChatMessages(): String {
    val person = MaryConversationMemory.pendingPerson
    val chatName = MaryConversationMemory.selectedChatName

    if (person == null || chatName == null) {
        return "I could not find the selected chat."
    }

    val displayName = MaryNotificationStore.getDisplayNameForChat(
        person = person,
        chatName = chatName
    )

    val messages = MaryNotificationStore.getPersonMessagesFromChat(
        person = person,
        chatName = chatName
    )

    return MaryReadingSession.start(
        person = person,
        source = displayName,
        messageList = messages
    )
}

fun readAllPendingPersonMessages(): String {
    val person = MaryConversationMemory.pendingPerson

    if (person == null) {
        return "Tell me whose messages you want to read."
    }

    val messages = MaryNotificationStore.getAllMessagesForPerson(person)

    return MaryReadingSession.start(
        person = person,
        source = "all places",
        messageList = messages
    )
}

fun executeMaryAction(context: Context, command: String, spokenText: String): String? {
    return when (command) {
        "PAUSE_MUSIC" -> {
            sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
            null
        }

        "PLAY_MUSIC" -> {
            sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY)
            null
        }

        "NEXT_SONG" -> {
            sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            null
        }

        "PREVIOUS_SONG" -> {
            sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            null
        }

        "OPEN_WHATSAPP" -> {
            openWhatsApp(context)
            null
        }

        "READ_WHATSAPP" -> {
            val message = readLatestWhatsAppNotification()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "COUNT_WHATSAPP_MESSAGES" -> {
            val message = countWhatsAppMessages()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "LIST_WHATSAPP_SENDERS" -> {
            val message = listWhatsAppSenders()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "READ_PERSON_WHATSAPP" -> {
            val message = readPersonWhatsAppMessage(spokenText)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "SELECT_CHAT_OPTION" -> {
            val message = readSelectedChatMessages()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "SELECT_ALL_PERSON_MESSAGES" -> {
            val message = readAllPendingPersonMessages()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "CONTINUE_READING" -> {
            val message = MaryReadingSession.continueReading()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "REPEAT_READING" -> {
            val message = MaryReadingSession.repeatCurrent()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "PREVIOUS_READING" -> {
            val message = MaryReadingSession.previousMessage()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "STOP_READING_SESSION" -> {
            val message = MaryReadingSession.stopReading()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            message
        }

        "OPEN_NOTIFICATION_SETTINGS" -> {
            openNotificationAccessSettings(context)
            null
        }

        "PERSON_NOT_FOUND" -> {
            null
        }

        "STOP_SPEAKING" -> {
            null
        }

        "CANCEL_CONVERSATION" -> {
            MaryConversationMemory.clearChatSelection()
            MaryReadingSession.stopReading()
            null
        }

        else -> null
    }
}
fun handleAutoListenTimeout(): String {
    return when {
        MaryReadingSession.isActive() -> {
            MaryReadingSession.stopReading()
        }

        MaryConversationMemory.hasPendingChatSelection() -> {
            MaryConversationMemory.clearChatSelection()
            "No selection heard. I cancelled the message selection."
        }

        else -> {
            "No response heard."
        }
    }
}

fun updateConversationState(currentState: String, command: String): String {
    return when (command) {
        "COUNT_WHATSAPP_MESSAGES" -> {
            if (MaryNotificationStore.getMessageCount() > 0) {
                "WAITING_FOR_SENDER_CONFIRMATION"
            } else {
                "IDLE"
            }
        }

        "READ_PERSON_WHATSAPP" -> {
            when {
                MaryConversationMemory.hasPendingChatSelection() -> "WAITING_FOR_CHAT_SELECTION"
                MaryReadingSession.isActive() -> "READING_MESSAGES"
                else -> "IDLE"
            }
        }

        "ASK_CHAT_SELECTION_AGAIN" -> {
            "WAITING_FOR_CHAT_SELECTION"
        }

        "SELECT_CHAT_OPTION",
        "SELECT_ALL_PERSON_MESSAGES",
        "CONTINUE_READING",
        "REPEAT_READING",
        "PREVIOUS_READING" -> {
            if (MaryReadingSession.isActive()) {
                "READING_MESSAGES"
            } else {
                "IDLE"
            }
        }

        "STOP_READING_SESSION",
        "LIST_WHATSAPP_SENDERS",
        "READ_WHATSAPP",
        "PERSON_NOT_FOUND",
        "STOP_SPEAKING",
        "CANCEL_CONVERSATION" -> {
            "IDLE"
        }

        else -> currentState
    }
}