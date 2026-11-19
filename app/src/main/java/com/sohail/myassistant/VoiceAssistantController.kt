package com.sohail.myassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Robust voice assistant controller encapsulating Text-to-Speech, SpeechRecognizer,
 * Bluetooth audio routing, and error recovery policies.
 */
class VoiceAssistantController(
    private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistantController"
        private const val AUDIO_SWITCH_SETTLE_MS = 600L
        private const val AUTO_LISTEN_RETRY_DELAY_MS = 1000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Text-To-Speech
    private var textToSpeech: TextToSpeech? = null
    var isTtsReady: Boolean = false
        private set

    // SpeechRecognizer
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech: ${e.message}", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.ENGLISH)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            Log.i(TAG, "TTS initialized. isTtsReady=$isTtsReady")
        } else {
            isTtsReady = false
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    /**
     * Speaks text with utterance completion callback.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, invoking onDone immediately")
            onDone?.invoke()
            return
        }

        val utteranceId = "MARY_TTS_${System.currentTimeMillis()}"

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post { onDone?.invoke() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    Log.e(TAG, "TTS error on utterance: $id")
                    mainHandler.post { onDone?.invoke() }
                }
            }
        })

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    /**
     * Stops any ongoing TTS speech.
     */
    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}", e)
        }
    }

    data class RecognitionCallbacks(
        val onReady: (isBluetooth: Boolean) -> Unit = {},
        val onBeginningOfSpeech: () -> Unit = {},
        val onEndOfSpeech: () -> Unit = {},
        val onPartialResult: (String) -> Unit = {},
        val onResult: (String) -> Unit = {},
        val onError: (errorMsg: String, isRecoverable: Boolean, errorCode: Int) -> Unit = { _, _, _ -> },
        val onRouteStatus: (String) -> Unit = {}
    )

    /**
     * Starts voice recognition session with Bluetooth routing and structured callbacks.
     */
    fun startListeningSession(
        callbacks: RecognitionCallbacks
    ) {
        if (isListening) {
            Log.w(TAG, "SpeechRecognizer is already listening")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onError("Speech recognition is not available on this device.", false, -1)
            return
        }

        // 1. Route audio to Bluetooth headset if available
        val routedToBluetooth = BluetoothAudioRouter.routeToBluetoothMic(context)
        val routeStatus = if (routedToBluetooth) {
            "Bluetooth mic ready..."
        } else {
            "Using phone microphone..."
        }
        callbacks.onRouteStatus(routeStatus)

        // 2. Allow hardware routing switch to settle, then initialize recognizer
        mainHandler.postDelayed({
            try {
                destroySpeechRecognizer()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            callbacks.onReady(BluetoothAudioRouter.isBluetoothRouted)
                        }

                        override fun onBeginningOfSpeech() {
                            callbacks.onBeginningOfSpeech()
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                            callbacks.onEndOfSpeech()
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            cleanupAudioRoute()
                            val msg = getErrorMessage(error)
                            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            destroySpeechRecognizer()
                            callbacks.onError(msg, recoverable, error)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            cleanupAudioRoute()
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            destroySpeechRecognizer()
                            callbacks.onResult(text)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim()
                            if (!partial.isNullOrBlank()) {
                                callbacks.onPartialResult(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.startListening(speechIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening: ${e.message}", e)
                isListening = false
                cleanupAudioRoute()
                destroySpeechRecognizer()
                callbacks.onError("Could not start microphone: ${e.message}", false, -1)
            }
        }, AUDIO_SWITCH_SETTLE_MS)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening: ${e.message}", e)
        }
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling listening: ${e.message}", e)
        } finally {
            isListening = false
            cleanupAudioRoute()
            destroySpeechRecognizer()
        }
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer: ${e.message}", e)
        } finally {
            speechRecognizer = null
        }
    }

    private fun cleanupAudioRoute() {
        BluetoothAudioRouter.resetAudioRoute(context)
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Speech server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input heard"
            else -> "Speech recognition error ($errorCode)"
        }
    }

    fun onPause() {
        mainHandler.removeCallbacksAndMessages(null)
        stopSpeaking()
        cancelListening()
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        stopSpeaking()
        cancelListening()
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}", e)
        }
        textToSpeech = null
    }
}
