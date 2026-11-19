package com.sohail.myassistant

data class WhatsAppMessage(
    val chatName: String,      // DM name or group name
    val senderName: String,    // actual sender
    val message: String,
    val time: Long,
    val isGroup: Boolean
)