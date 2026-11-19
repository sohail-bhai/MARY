package com.sohail.myassistant

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
            "stop voice"
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
}
