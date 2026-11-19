package com.sohail.myassistant

import java.util.Locale

object MaryNotificationStore {
    var latestWhatsAppMessage: String = "No WhatsApp notification found yet."
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

    fun resetMessages() {
        messages.clear()
        latestWhatsAppMessage = "No WhatsApp notification found yet."
    }

    fun addMessage(
        chatName: String,
        senderName: String,
        message: String,
        time: Long,
        isGroup: Boolean
    ) {
        val cleanChatName = chatName.trim()
        val cleanSenderName = senderName.trim()
        val cleanMessage = message.trim()

        if (cleanChatName.isBlank() || cleanSenderName.isBlank() || cleanMessage.isBlank()) {
            return
        }

        val alreadyExists = messages.any {
            it.chatName == cleanChatName &&
                    it.senderName == cleanSenderName &&
                    it.message == cleanMessage
        }

        if (!alreadyExists) {
            messages.add(
                WhatsAppMessage(
                    chatName = cleanChatName,
                    senderName = cleanSenderName,
                    message = cleanMessage,
                    time = time,
                    isGroup = isGroup
                )
            )
        }

        latestWhatsAppMessage = if (isGroup) {
            "$cleanSenderName in $cleanChatName: $cleanMessage"
        } else {
            "$cleanSenderName: $cleanMessage"
        }

        if (messages.size > 100) {
            messages.removeAt(0)
        }
    }

    fun getMessageCount(): Int {
        return messages.size
    }

    fun getSenders(): List<String> {
        return messages
            .map { it.senderName }
            .distinct()
    }

    fun countResponse(): String {
        val count = getMessageCount()

        return if (count == 0) {
            "No WhatsApp messages found in notifications."
        } else {
            "You have $count WhatsApp message${if (count > 1) "s" else ""}. Do you want to know who sent them?"
        }
    }

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

    fun getMessagesForPerson(person: String): List<WhatsAppMessage> {
        return messages.filter { message ->
            nameMatchesPerson(message.senderName, person) ||
                    (!message.isGroup && nameMatchesPerson(message.chatName, person))
        }.sortedBy { it.time }
    }

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

    fun readAllMessagesForPerson(person: String): String {
        val options = getChatOptionsForPerson(person)

        if (options.isEmpty()) {
            return "No messages found from $person."
        }

        return options.joinToString(". ") { option ->
            readPersonMessagesFromChat(person, option.chatName)
        }
    }
    fun getPersonMessagesFromChat(person: String, chatName: String): List<WhatsAppMessage> {
        return getMessagesForPerson(person)
            .filter { it.chatName == chatName }
            .sortedBy { it.time }
    }

    fun getAllMessagesForPerson(person: String): List<WhatsAppMessage> {
        return getMessagesForPerson(person)
            .sortedBy { it.time }
    }

    fun getDisplayNameForChat(person: String, chatName: String): String {
        val option = getChatOptionsForPerson(person)
            .firstOrNull { it.chatName == chatName }

        return option?.displayName ?: chatName
    }
}