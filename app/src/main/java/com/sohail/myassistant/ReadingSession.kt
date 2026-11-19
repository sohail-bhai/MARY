package com.sohail.myassistant

object MaryReadingSession {
    private var messages: List<WhatsAppMessage> = emptyList()
    private var currentIndex: Int = 0
    private var personName: String = ""
    private var sourceName: String = ""
    private var active: Boolean = false

    fun isActive(): Boolean {
        return active && messages.isNotEmpty()
    }

    fun start(
        person: String,
        source: String,
        messageList: List<WhatsAppMessage>
    ): String {
        messages = messageList.sortedBy { it.time }
        currentIndex = 0
        personName = person
        sourceName = source
        active = messages.isNotEmpty()

        if (!active) {
            return "No messages found from $person."
        }

        val count = messages.size
        val messageWord = if (count == 1) "message" else "messages"

        return "$person has $count $messageWord in $sourceName. ${formatCurrentMessage()} Say continue, repeat, previous, or stop."
    }

    fun continueReading(): String {
        if (!isActive()) {
            return "No active message reading session."
        }

        if (currentIndex >= messages.lastIndex) {
            active = false
            return "That was the last message from $personName."
        }

        currentIndex++
        return "${formatCurrentMessage()} Say continue, repeat, previous, or stop."
    }

    fun repeatCurrent(): String {
        if (!isActive()) {
            return "No active message reading session."
        }

        return "${formatCurrentMessage()} Say continue, repeat, previous, or stop."
    }

    fun previousMessage(): String {
        if (!isActive()) {
            return "No active message reading session."
        }

        if (currentIndex == 0) {
            return "This is already the first message. ${formatCurrentMessage()} Say continue, repeat, or stop."
        }

        currentIndex--
        return "${formatCurrentMessage()} Say continue, repeat, previous, or stop."
    }

    fun stopReading(): String {
        active = false
        messages = emptyList()
        currentIndex = 0
        return "Okay, stopped reading messages."
    }

    private fun formatCurrentMessage(): String {
        val message = messages[currentIndex]
        val position = getPositionWord(currentIndex)

        return if (message.isGroup) {
            "$position message. In ${message.chatName}, ${message.senderName} said: ${message.message}."
        } else {
            "$position message. Direct message from ${message.senderName}: ${message.message}."
        }
    }

    private fun getPositionWord(index: Int): String {
        return when (index) {
            0 -> "First"
            1 -> "Second"
            2 -> "Third"
            3 -> "Fourth"
            4 -> "Fifth"
            else -> "Next"
        }
    }
}