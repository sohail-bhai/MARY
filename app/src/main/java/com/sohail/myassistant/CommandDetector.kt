package com.sohail.myassistant

import java.util.Locale

data class MaryCommand(
    val command: String,
    val response: String
)

private fun extractPossiblePersonWords(input: String): List<String> {
    val fillerWords = setOf(
        "read",
        "whatsapp",
        "message",
        "messages",
        "msg",
        "msgs",
        "from",
        "the",
        "my",
        "latest",
        "ka",
        "ki",
        "ke",
        "his",
        "her"
    )

    return input
        .lowercase(Locale.getDefault())
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() && it !in fillerWords }
}

fun detectMaryCommand(text: String, conversationState: String): MaryCommand {
    val input = text.lowercase(Locale.getDefault())

    val userSaidYes =
        input.contains("yes") ||
                input.contains("yeah") ||
                input.contains("yep") ||
                input.contains("sure") ||
                input.contains("okay") ||
                input.contains("ok") ||
                input.contains("tell me")

    val userSaidNo =
        input.contains("no") ||
                input.contains("nope") ||
                input.contains("not now")

    return when {
        input.contains("stop speaking") ||
                input.contains("shut up") ||
                input.contains("kill") ||
                input.contains("emergency stop") ||
                input.contains("stop voice") -> {
            MaryCommand(
                command = "STOP_SPEAKING",
                response = "Okay, stopped speaking."
            )
        }

        MaryReadingSession.isActive() &&
                (
                        input.contains("continue") ||
                                input.contains("next message") ||
                                input == "next" ||
                                input.contains("go on")
                        ) -> {
            MaryCommand(
                command = "CONTINUE_READING",
                response = "Reading next message."
            )
        }

        MaryReadingSession.isActive() &&
                (
                        input.contains("repeat") ||
                                input.contains("say again") ||
                                input.contains("again")
                        ) -> {
            MaryCommand(
                command = "REPEAT_READING",
                response = "Repeating message."
            )
        }

        MaryReadingSession.isActive() &&
                (
                        input.contains("previous message") ||
                                input.contains("previous") ||
                                input.contains("back")
                        ) -> {
            MaryCommand(
                command = "PREVIOUS_READING",
                response = "Reading previous message."
            )
        }

        MaryReadingSession.isActive() &&
                (
                        input == "stop" ||
                                input.contains("stop reading") ||
                                input.contains("cancel reading") ||
                                input.contains("done")
                        ) -> {
            MaryCommand(
                command = "STOP_READING_SESSION",
                response = "Stopping message reading."
            )
        }

        conversationState == "WAITING_FOR_CHAT_SELECTION" &&
                (input.contains("all") || input.contains("everything")) -> {
            MaryCommand(
                command = "SELECT_ALL_PERSON_MESSAGES",
                response = "Reading all messages."
            )
        }

        conversationState == "WAITING_FOR_CHAT_SELECTION" -> {
            val selectedOption = MaryConversationMemory.matchChatSelection(input)

            if (selectedOption != null) {
                MaryConversationMemory.selectedChatName = selectedOption.chatName

                MaryCommand(
                    command = "SELECT_CHAT_OPTION",
                    response = "Reading messages from ${selectedOption.displayName}."
                )
            } else {
                MaryCommand(
                    command = "ASK_CHAT_SELECTION_AGAIN",
                    response = "I could not match that. Say direct message, group name, group one, group two, or all."
                )
            }
        }

        MaryConversationMemory.hasPreviousChatOptions() &&
                (input.contains("all") || input.contains("everything")) -> {
            MaryCommand(
                command = "SELECT_ALL_PERSON_MESSAGES",
                response = "Reading all messages."
            )
        }

        MaryConversationMemory.hasPreviousChatOptions() &&
                MaryConversationMemory.matchChatSelection(input) != null -> {
            val selectedOption = MaryConversationMemory.matchChatSelection(input)!!
            MaryConversationMemory.selectedChatName = selectedOption.chatName

            MaryCommand(
                command = "SELECT_CHAT_OPTION",
                response = "Reading messages from ${selectedOption.displayName}."
            )
        }

        conversationState == "WAITING_FOR_SENDER_CONFIRMATION" && userSaidYes -> {
            MaryCommand(
                command = "LIST_WHATSAPP_SENDERS",
                response = "Checking who sent messages."
            )
        }

        conversationState == "WAITING_FOR_SENDER_CONFIRMATION" && userSaidNo -> {
            MaryCommand(
                command = "CANCEL_CONVERSATION",
                response = "Okay, I will not read the sender list."
            )
        }

        input.contains("hey mary") || input.contains("hello mary") -> {
            MaryCommand(
                command = "WAKE_WORD_DETECTED",
                response = "Yes, I am listening."
            )
        }

        input.contains("notification access") || input.contains("enable notification") -> {
            MaryCommand(
                command = "OPEN_NOTIFICATION_SETTINGS",
                response = "Opening notification access settings."
            )
        }

        input.contains("how many") &&
                (input.contains("whatsapp") || input.contains("message") || input.contains("messages")) -> {
            MaryCommand(
                command = "COUNT_WHATSAPP_MESSAGES",
                response = "Checking WhatsApp messages."
            )
        }

        input.contains("who") &&
                (input.contains("sent") || input.contains("message") || input.contains("messages")) -> {
            MaryCommand(
                command = "LIST_WHATSAPP_SENDERS",
                response = "Checking who sent messages."
            )
        }

        input.contains("read") &&
                (input.contains("message") || input.contains("messages") || input.contains("whatsapp") || input.contains("msg")) -> {

            val sender = MaryNotificationStore.findSenderInText(input)
            val possiblePersonWords = extractPossiblePersonWords(input)

            if (sender != null) {
                MaryCommand(
                    command = "READ_PERSON_WHATSAPP",
                    response = "Reading message from $sender."
                )
            } else if (possiblePersonWords.isNotEmpty()) {
                MaryCommand(
                    command = "PERSON_NOT_FOUND",
                    response = "I could not find messages from ${possiblePersonWords.joinToString(" ")}."
                )
            } else {
                MaryCommand(
                    command = "READ_WHATSAPP",
                    response = "Reading latest WhatsApp notification."
                )
            }
        }

        input.contains("message") && MaryNotificationStore.findSenderInText(input) != null -> {
            MaryCommand(
                command = "READ_PERSON_WHATSAPP",
                response = "Reading the requested message."
            )
        }

        input.contains("pause") || input.contains("stop song") || input.contains("stop music") -> {
            MaryCommand(
                command = "PAUSE_MUSIC",
                response = "Okay, pausing music."
            )
        }

        input.contains("play") || input.contains("resume") -> {
            MaryCommand(
                command = "PLAY_MUSIC",
                response = "Okay, playing music."
            )
        }

        input.contains("next") || input.contains("forward") -> {
            MaryCommand(
                command = "NEXT_SONG",
                response = "Playing next song."
            )
        }

        input.contains("previous") || input.contains("back") -> {
            MaryCommand(
                command = "PREVIOUS_SONG",
                response = "Playing previous song."
            )
        }

        input.contains("open whatsapp") ||
                input.contains("launch whatsapp") ||
                input.contains("start whatsapp") -> {
            MaryCommand(
                command = "OPEN_WHATSAPP",
                response = "Opening WhatsApp."
            )
        }

        input.contains("call") -> {
            MaryCommand(
                command = "CALL_CONTACT",
                response = "Calling feature will be added soon."
            )
        }

        else -> {
            MaryCommand(
                command = "UNKNOWN_COMMAND",
                response = "Sorry, I did not understand that command."
            )
        }
    }
}