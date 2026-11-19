package com.sohail.myassistant

import java.util.Locale

object MaryNotificationStore {
    private const val MAX_MESSAGES = 100
    private const val EMPTY_LATEST_MESSAGE = "No WhatsApp notification found yet."
    private val whitespace = Regex("\\s+")

    @Volatile
    var latestWhatsAppMessage: String = EMPTY_LATEST_MESSAGE

    @Volatile
    var listener: MaryNotificationListener? = null

    private val messages = mutableListOf<WhatsAppMessage>()

    private val contactAliases = mapOf(
        "sassini" to listOf(
            "sassini",
            "hasini",
            "sasani",
            "sashini",
            "sasini",
            "sasi",
            "sasin"
        ),

        "dharmik" to listOf(
            "dharmik",
            "darmik",
            "dharmic",
            "dhar mik",
            "dharam"
        )
    )

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    @Synchronized
    fun resetMessages() {
        messages.clear()
        latestWhatsAppMessage = EMPTY_LATEST_MESSAGE
    }

    @Synchronized
    fun addMessage(
        chatName: String,
        senderName: String,
        message: String,
        time: Long,
        isGroup: Boolean
    ) {
        storeMessages(
            incoming = listOf(
                WhatsAppMessage(
                    chatName = chatName,
                    senderName = senderName,
                    message = message,
                    time = time,
                    isGroup = isGroup
                )
            ),
            replace = false
        )
    }

    @Synchronized
    fun addMessages(incoming: Iterable<WhatsAppMessage>) {
        storeMessages(incoming, replace = false)
    }

    @Synchronized
    fun replaceMessages(incoming: Iterable<WhatsAppMessage>) {
        storeMessages(incoming, replace = true)
    }

    private fun storeMessages(incoming: Iterable<WhatsAppMessage>, replace: Boolean) {
        if (replace) messages.clear()

        for (message in incoming) {
            val cleanMessage = message.copy(
                chatName = cleanWhitespace(message.chatName),
                senderName = cleanWhitespace(message.senderName),
                message = cleanWhitespace(message.message)
            )

            if (
                cleanMessage.chatName.isBlank() ||
                cleanMessage.senderName.isBlank() ||
                cleanMessage.message.isBlank()
            ) {
                continue
            }

            // Include the timestamp: a person may legitimately send the same text twice.
            if (cleanMessage !in messages) {
                messages.add(cleanMessage)
            }
        }

        messages.sortBy { it.time }
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
        }

        latestWhatsAppMessage = messages.lastOrNull()?.toLatestMessageText()
            ?: EMPTY_LATEST_MESSAGE
    }

    private fun cleanWhitespace(text: String): String =
        text.trim().replace(whitespace, " ")

    private fun WhatsAppMessage.toLatestMessageText(): String = if (isGroup) {
        "$senderName in $chatName: $message"
    } else {
        "$senderName: $message"
    }

    @Synchronized
    fun getMessageCount(): Int = messages.size

    @Synchronized
    fun getSenders(): List<String> = messages
        .map { it.senderName }
        .distinct()

    @Synchronized
    fun countResponse(): String {
        val count = messages.size

        return if (count == 0) {
            "No WhatsApp messages found in notifications."
        } else {
            "You have $count WhatsApp message${if (count > 1) "s" else ""}. Do you want to know who sent them?"
        }
    }

    @Synchronized
    fun senderListResponse(): String {
        val senders = getSenders()

        return if (senders.isEmpty()) {
            "No WhatsApp senders found yet."
        } else {
            "Messages from ${senders.joinToString(", ")}."
        }
    }

    private fun nameMatchesPerson(name: String, person: String): Boolean {
        val nameNorm = normalize(name)
        val personNorm = normalize(person)

        if (nameNorm == personNorm) return true
        if (nameNorm.contains(personNorm)) return true
        if (personNorm.contains(nameNorm)) return true

        return false
    }

    @Synchronized
    fun findSenderInText(text: String): String? {
        val input = normalize(text)

        val possibleNames = messages
            .flatMap { message ->
                if (message.isGroup) {
                    listOf(message.senderName)
                } else {
                    listOf(message.senderName, message.chatName)
                }
            }
            .distinct()

        val directMatch = possibleNames.firstOrNull { name ->
            val fullName = normalize(name)
            val firstName = fullName.split(" ").firstOrNull() ?: fullName

            input.contains(fullName) || input.contains(firstName)
        }

        if (directMatch != null) {
            return directMatch
        }

        for ((realName, aliases) in contactAliases) {
            val aliasMatched = aliases.any { alias ->
                input.contains(normalize(alias))
            }

            if (aliasMatched) {
                val matchedSender = possibleNames.firstOrNull { name ->
                    nameMatchesPerson(name, realName)
                }

                return matchedSender ?: realName
            }
        }

        return null
    }

    @Synchronized
    fun getMessagesForPerson(person: String): List<WhatsAppMessage> {
        return messages.filter { message ->
            nameMatchesPerson(message.senderName, person) ||
                    (!message.isGroup && nameMatchesPerson(message.chatName, person))
        }.sortedBy { it.time }
    }

    @Synchronized
    fun getChatOptionsForPerson(person: String): List<PendingChatOption> {
        val personMessages = getMessagesForPerson(person)

        val grouped = personMessages.groupBy { it.chatName }

        return grouped.map { entry ->
            val chatName = entry.key
            val chatMessages = entry.value

            val isDirectMessage = chatMessages.all { !it.isGroup } &&
                    chatMessages.any { nameMatchesPerson(it.chatName, person) }

            PendingChatOption(
                chatName = chatName,
                displayName = if (isDirectMessage) "Direct message" else chatName
            )
        }.sortedWith(
            compareBy<PendingChatOption> {
                if (it.displayName == "Direct message") 0 else 1
            }.thenBy {
                it.displayName
            }
        )
    }

    @Synchronized
    fun readPersonMessagesFromChat(person: String, chatName: String): String {
        val personMessages = getMessagesForPerson(person)
            .filter { it.chatName == chatName }

        if (personMessages.isEmpty()) {
            return "No messages found from $person in $chatName."
        }

        val option = getChatOptionsForPerson(person)
            .firstOrNull { it.chatName == chatName }

        val displayName = option?.displayName ?: chatName

        val messageText = personMessages.joinToString(". ") {
            it.message
        }

        return if (displayName == "Direct message") {
            "Direct message from $person: $messageText"
        } else {
            "In $displayName, $person said: $messageText"
        }
    }

    @Synchronized
    fun readAllMessagesForPerson(person: String): String {
        val options = getChatOptionsForPerson(person)

        if (options.isEmpty()) {
            return "No messages found from $person."
        }

        return options.joinToString(". ") { option ->
            readPersonMessagesFromChat(person, option.chatName)
        }
    }

    @Synchronized
    fun getPersonMessagesFromChat(person: String, chatName: String): List<WhatsAppMessage> {
        return getMessagesForPerson(person)
            .filter { it.chatName == chatName }
            .sortedBy { it.time }
    }

    @Synchronized
    fun getAllMessagesForPerson(person: String): List<WhatsAppMessage> {
        return getMessagesForPerson(person)
            .sortedBy { it.time }
    }

    @Synchronized
    fun getDisplayNameForChat(person: String, chatName: String): String {
        val option = getChatOptionsForPerson(person)
            .firstOrNull { it.chatName == chatName }

        return option?.displayName ?: chatName
    }
}
