package com.sohail.myassistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
 * Robust voice assistant controller encapsulating:
 * - Text-to-Speech with utterance tracking
 * - SpeechRecognizer with lifecycle-safe creation & destruction
 * - Audio Focus management (ducks background music while speaking or listening)
 * - Bluetooth SCO / BLE headset routing
 * - Driving-oriented silence/timeout error recovery
 */
class VoiceAssistantController(
    private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistantController"
        private const val AUDIO_SWITCH_SETTLE_MS = 600L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Audio Focus tracking
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    // Text-To-Speech
    private var textToSpeech: TextToSpeech? = null
    var isTtsReady: Boolean = false
        private set

    @Volatile
    private var currentSpeakingUtteranceId: String? = null

    // SpeechRecognizer
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    // Pending listener startup runnable
    private var pendingSessionRunnable: Runnable? = null

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

    // =========================================================================
    // AUDIO FOCUS MANAGEMENT (Ducks background music while driving)
    // =========================================================================

    private fun requestTransientAudioFocus(): Boolean {
        if (audioManager == null || hasAudioFocus) return hasAudioFocus

        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* No-op for transient */ }
                    .build()

                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { /* No-op */ },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "requestTransientAudioFocus granted=$hasAudioFocus")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
            hasAudioFocus = false
        }
        return hasAudioFocus
    }

    private fun abandonTransientAudioFocus() {
        if (audioManager == null || !hasAudioFocus) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            hasAudioFocus = false
            Log.d(TAG, "abandonTransientAudioFocus executed")
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}", e)
        }
    }

    // =========================================================================
    // TEXT TO SPEECH
    // =========================================================================

    /**
     * Speaks text with utterance completion callback, automatically ducking media.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, invoking onDone immediately")
            onDone?.invoke()
            return
        }

        requestTransientAudioFocus()

        val utteranceId = "MARY_TTS_${System.currentTimeMillis()}"
        currentSpeakingUtteranceId = utteranceId

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId && id == currentSpeakingUtteranceId) {
                    mainHandler.post {
                        if (!isListening) {
                            abandonTransientAudioFocus()
                        }
                        onDone?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && id == currentSpeakingUtteranceId) {
                    Log.e(TAG, "TTS error on utterance: $id")
                    mainHandler.post {
                        if (!isListening) {
                            abandonTransientAudioFocus()
                        }
                        onDone?.invoke()
                    }
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
     * Stops ongoing TTS speech and abandons focus if not listening.
     */
    fun stopSpeaking() {
        currentSpeakingUtteranceId = null
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}", e)
        } finally {
            if (!isListening) {
                abandonTransientAudioFocus()
            }
        }
    }

    // =========================================================================
    // SPEECH RECOGNITION
    // =========================================================================

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
     * Cancels any pending delayed startup before scheduling a new one.
     */
    fun startListeningSession(
        callbacks: RecognitionCallbacks
    ) {
        cancelPendingStartup()

        if (isListening) {
            Log.w(TAG, "SpeechRecognizer is already listening")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onError("Speech recognition is not available on this device.", false, -1)
            return
        }

        // 1. Duck background media while listening
        requestTransientAudioFocus()

        // 2. Route audio to Bluetooth headset if available
        val routedToBluetooth = BluetoothAudioRouter.routeToBluetoothMic(context)
        callbacks.onRouteStatus(BluetoothAudioRouter.getAudioRouteStatus())

        // 3. Settle audio hardware switch before opening input buffer
        val runnable = Runnable {
            pendingSessionRunnable = null
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
                            abandonTransientAudioFocus()

                            val msg = getErrorMessage(error)
                            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                            destroySpeechRecognizer()
                            callbacks.onError(msg, recoverable, error)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            cleanupAudioRoute()
                            abandonTransientAudioFocus()

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
                abandonTransientAudioFocus()
                callbacks.onError("Could not start microphone: ${e.message}", false, -1)
            }
        }

        pendingSessionRunnable = runnable
        mainHandler.postDelayed(runnable, AUDIO_SWITCH_SETTLE_MS)
    }

    private fun cancelPendingStartup() {
        pendingSessionRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingSessionRunnable = null
            Log.d(TAG, "Cancelled pending listening startup runnable")
        }
    }

    fun stopListening() {
        cancelPendingStartup()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening: ${e.message}", e)
        }
    }

    fun cancelListening() {
        cancelPendingStartup()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling listening: ${e.message}", e)
        } finally {
            isListening = false
            cleanupAudioRoute()
            destroySpeechRecognizer()
            abandonTransientAudioFocus()
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

    fun getErrorMessage(errorCode: Int): String {
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
        cancelPendingStartup()
        stopSpeaking()
        cancelListening()
    }

    fun destroy() {
        cancelPendingStartup()
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
