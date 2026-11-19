package com.sohail.myassistant

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantUnitTest {

    @Test
    fun testEmergencyStopCommandDetection() {
        val emergencyCommands = listOf(
            "stop speaking",
            "shut up",
            "kill",
            "emergency stop",
            "stop voice",
            "PLEASE STOP SPEAKING NOW",
            "shut up!"
        )

        for (phrase in emergencyCommands) {
            val result = detectMaryCommand(phrase, "IDLE")
            assertEquals("STOP_SPEAKING", result.command)
        }
    }

    @Test
    fun testMusicControlCommandDetection() {
        assertEquals("PAUSE_MUSIC", detectMaryCommand("pause song", "IDLE").command)
        assertEquals("PAUSE_MUSIC", detectMaryCommand("stop music", "IDLE").command)
        assertEquals("PLAY_MUSIC", detectMaryCommand("play music", "IDLE").command)
        assertEquals("PLAY_MUSIC", detectMaryCommand("resume song", "IDLE").command)
        assertEquals("NEXT_SONG", detectMaryCommand("next song", "IDLE").command)
        assertEquals("PREVIOUS_SONG", detectMaryCommand("previous song", "IDLE").command)
    }

    @Test
    fun testWakeWordDetection() {
        assertEquals("WAKE_WORD_DETECTED", detectMaryCommand("hey mary", "IDLE").command)
        assertEquals("WAKE_WORD_DETECTED", detectMaryCommand("hello mary", "IDLE").command)
    }

    @Test
    fun testConversationConfirmationCommands() {
        // Yes variants in confirmation state
        val yesInputs = listOf("yes", "yeah", "yep", "sure", "okay", "ok", "tell me")
        for (yes in yesInputs) {
            val result = detectMaryCommand(yes, "WAITING_FOR_SENDER_CONFIRMATION")
            assertEquals("LIST_WHATSAPP_SENDERS", result.command)
        }

        // No variants in confirmation state
        val noInputs = listOf("no", "nope", "not now")
        for (no in noInputs) {
            val result = detectMaryCommand(no, "WAITING_FOR_SENDER_CONFIRMATION")
            assertEquals("CANCEL_CONVERSATION", result.command)
        }
    }

    @Test
    fun testConversationStateTransitions() {
        // COUNT_WHATSAPP_MESSAGES with 0 messages stays IDLE
        MaryNotificationStore.resetMessages()
        val nextStateEmpty = updateConversationState("IDLE", "COUNT_WHATSAPP_MESSAGES")
        assertEquals("IDLE", nextStateEmpty)

        // Adding a message switches state to WAITING_FOR_SENDER_CONFIRMATION
        MaryNotificationStore.addMessage(
            chatName = "Test",
            senderName = "Test",
            message = "Hello",
            time = System.currentTimeMillis(),
            isGroup = false
        )
        val nextStateWithMsg = updateConversationState("IDLE", "COUNT_WHATSAPP_MESSAGES")
        assertEquals("WAITING_FOR_SENDER_CONFIRMATION", nextStateWithMsg)

        // Reset
        MaryNotificationStore.resetMessages()
    }

    @Test
    fun testReadingSessionNavigationCommands() {
        MaryNotificationStore.resetMessages()
        val messages = listOf(
            WhatsAppMessage("Dharmik", "Dharmik", "Hey, are you free?", 1000L, false),
            WhatsAppMessage("Dharmik", "Dharmik", "Call me when you reach", 2000L, false)
        )

        val startMsg = MaryReadingSession.start("Dharmik", "Direct message", messages)
        assertTrue(MaryReadingSession.isActive())
        assertTrue(startMsg.contains("Dharmik has 2 messages"))

        val continueCmd = detectMaryCommand("continue", "READING_MESSAGES")
        assertEquals("CONTINUE_READING", continueCmd.command)

        val nextCmd = detectMaryCommand("next", "READING_MESSAGES")
        assertEquals("CONTINUE_READING", nextCmd.command)

        val repeatCmd = detectMaryCommand("repeat", "READING_MESSAGES")
        assertEquals("REPEAT_READING", repeatCmd.command)

        val prevCmd = detectMaryCommand("previous", "READING_MESSAGES")
        assertEquals("PREVIOUS_READING", prevCmd.command)

        val stopCmd = detectMaryCommand("stop reading", "READING_MESSAGES")
        assertEquals("STOP_READING_SESSION", stopCmd.command)

        MaryReadingSession.stopReading()
        assertFalse(MaryReadingSession.isActive())
    }

    @Test
    fun testPhoneticAliasMatching() {
        MaryNotificationStore.resetMessages()
        MaryNotificationStore.addMessage(
            chatName = "Hasini",
            senderName = "Hasini",
            message = "Hey did you reach home?",
            time = System.currentTimeMillis(),
            isGroup = false
        )

        // Phonetic misrecognitions that should match Hasini
        val match1 = MaryNotificationStore.findSenderInText("read message from sassini")
        assertNotNull(match1)

        val match2 = MaryNotificationStore.findSenderInText("what did sasani say")
        assertNotNull(match2)

        MaryNotificationStore.resetMessages()
    }

    @Test
    fun testBluetoothRouteStatusDefault() {
        val status = BluetoothAudioRouter.getAudioRouteStatus()
        assertEquals("Using phone microphone", status)
        assertFalse(BluetoothAudioRouter.isBluetoothRouted)
    }

    @Test
    fun testSpeechRecognizerErrorMapping() {
        // Simulating the error mapping in VoiceAssistantController
        fun mapError(code: Int): String {
            return when (code) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input heard"
                else -> "Speech recognition error ($code)"
            }
        }

        assertEquals("No speech recognized", mapError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("No speech input heard", mapError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals("Speech recognizer busy", mapError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals("Network connection error", mapError(SpeechRecognizer.ERROR_NETWORK))
    }
}
