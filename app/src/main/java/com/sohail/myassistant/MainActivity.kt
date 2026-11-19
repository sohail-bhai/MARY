package com.sohail.myassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sohail.myassistant.ui.theme.MaryTheme

class MainActivity : ComponentActivity() {

    private lateinit var voiceController: VoiceAssistantController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        voiceController = VoiceAssistantController(this)

        setContent {
            MaryTheme {
                MaryHomeScreen(
                    speak = { text, onDone ->
                        voiceController.speak(text, onDone)
                    },
                    stopSpeaking = {
                        voiceController.stopSpeaking()
                    },
                    voiceController = voiceController
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::voiceController.isInitialized) {
            voiceController.onPause()
        }
    }

    override fun onDestroy() {
        if (::voiceController.isInitialized) {
            voiceController.destroy()
        }
        super.onDestroy()
    }
}

@Composable
fun MaryHomeScreen(
    speak: (String, (() -> Unit)?) -> Unit,
    stopSpeaking: () -> Unit,
    voiceController: VoiceAssistantController? = null
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var isActive by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var spokenText by remember { mutableStateOf("Nothing heard yet") }
    var detectedCommand by remember { mutableStateOf("No command yet") }
    var maryResponse by remember { mutableStateOf("Mary is waiting...") }
    var statusText by remember { mutableStateOf("Tap Start Mary") }
    var conversationState by remember { mutableStateOf("IDLE") }

    var pendingAutoListenRunnable by remember { mutableStateOf<Runnable?>(null) }

    fun cancelPendingAutoListen() {
        pendingAutoListenRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingAutoListenRunnable = null
        }
    }

    fun shouldAutoListen(command: String): Boolean {
        return when (command) {
            "COUNT_WHATSAPP_MESSAGES",
            "READ_PERSON_WHATSAPP",
            "ASK_CHAT_SELECTION_AGAIN",
            "SELECT_CHAT_OPTION",
            "SELECT_ALL_PERSON_MESSAGES",
            "CONTINUE_READING",
            "REPEAT_READING",
            "PREVIOUS_READING" -> true

            else -> false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        statusText = if (isGranted) {
            "Microphone permission granted. Tap Listen."
        } else {
            "Microphone permission denied."
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        statusText = if (isGranted) {
            "Bluetooth permission granted. Tap Listen again."
        } else {
            "Bluetooth permission denied. AirPods mic may not work."
        }
    }

    fun startVoiceListening(
        autoStarted: Boolean = false,
        retryCount: Int = 0
    ) {
        if (!isActive) {
            statusText = "Start Mary first."
            return
        }

        if (isListening) {
            return
        }

        cancelPendingAutoListen()

        if (autoStarted) {
            statusText = "Auto listening..."
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !BluetoothAudioRouter.hasBluetoothPermission(context)
        ) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        val controller = voiceController ?: VoiceAssistantController(context)

        controller.startListeningSession(
            callbacks = VoiceAssistantController.RecognitionCallbacks(
                onRouteStatus = { routeMsg ->
                    statusText = routeMsg
                },
                onReady = { isBt ->
                    isListening = true
                    statusText = if (isBt) "Bluetooth mic ready (listening)..." else "Listening..."
                },
                onBeginningOfSpeech = {
                    statusText = "Speak now..."
                },
                onEndOfSpeech = {
                    isListening = false
                    statusText = "Processing..."
                },
                onPartialResult = { partial ->
                    spokenText = partial
                },
                onResult = { text ->
                    isListening = false
                    spokenText = if (text.isNotBlank()) text else "No speech detected"

                    if (spokenText == "No speech detected") {
                        if (retryCount < 1) {
                            statusText = "No speech detected. Listening again..."
                            val retryRunnable = Runnable {
                                pendingAutoListenRunnable = null
                                if (isActive) {
                                    startVoiceListening(autoStarted = autoStarted, retryCount = retryCount + 1)
                                }
                            }
                            pendingAutoListenRunnable = retryRunnable
                            mainHandler.postDelayed(retryRunnable, 1200)
                            return@RecognitionCallbacks
                        }

                        statusText = "Could not understand. Try again."
                        maryResponse = "I could not hear anything clearly."
                        speak(maryResponse, null)
                        return@RecognitionCallbacks
                    }

                    val result = detectMaryCommand(spokenText, conversationState)
                    detectedCommand = result.command
                    maryResponse = result.response
                    statusText = "Command received"

                    val actionResponse = executeMaryAction(
                        context = context,
                        command = result.command,
                        spokenText = spokenText
                    )

                    if (actionResponse != null) {
                        maryResponse = actionResponse
                    }

                    val nextConversationState = updateConversationState(
                        currentState = conversationState,
                        command = result.command
                    )

                    conversationState = nextConversationState

                    if (result.command == "STOP_SPEAKING") {
                        cancelPendingAutoListen()
                        stopSpeaking()
                    } else {
                        speak(maryResponse) {
                            if (
                                isActive &&
                                shouldAutoListen(result.command) &&
                                nextConversationState != "IDLE"
                            ) {
                                val autoRunnable = Runnable {
                                    pendingAutoListenRunnable = null
                                    if (isActive) {
                                        startVoiceListening(autoStarted = true, retryCount = 0)
                                    }
                                }
                                pendingAutoListenRunnable = autoRunnable
                                mainHandler.postDelayed(autoRunnable, 1200)
                            }
                        }
                    }
                },
                onError = { errorMsg, isRecoverable, _ ->
                    isListening = false

                    if (autoStarted && retryCount < 1) {
                        statusText = "I did not catch that. Listening again..."
                        val retryRunnable = Runnable {
                            pendingAutoListenRunnable = null
                            if (isActive) {
                                startVoiceListening(autoStarted = true, retryCount = retryCount + 1)
                            }
                        }
                        pendingAutoListenRunnable = retryRunnable
                        mainHandler.postDelayed(retryRunnable, 1200)
                        return@RecognitionCallbacks
                    }

                    if (autoStarted) {
                        val timeoutResponse = handleAutoListenTimeout()
                        maryResponse = timeoutResponse
                        detectedCommand = "AUTO_TIMEOUT"
                        spokenText = "No response"
                        statusText = "Auto-listen timeout"
                        conversationState = "IDLE"
                        speak(timeoutResponse, null)
                    } else {
                        statusText = "Could not understand. Try again."
                        if (isActive && isRecoverable && retryCount < 1) {
                            val retryRunnable = Runnable {
                                pendingAutoListenRunnable = null
                                if (isActive) {
                                    startVoiceListening(autoStarted = false, retryCount = retryCount + 1)
                                }
                            }
                            pendingAutoListenRunnable = retryRunnable
                            mainHandler.postDelayed(retryRunnable, 1200)
                        }
                    }
                }
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "MARY",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Your Driving Voice Assistant",
            fontSize = 18.sp,
            color = Color(0xFFCBD5E1),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = if (isActive) "Assistant Active" else "Assistant Not Active",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF22C55E) else Color(0xFFF87171)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        isActive = !isActive
                        cancelPendingAutoListen()
                        if (isActive) {
                            statusText = "Mary is active. Tap Listen."
                        } else {
                            voiceController?.cancelListening()
                            voiceController?.stopSpeaking()
                            isListening = false
                            statusText = "Tap Start Mary"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isActive) "Stop Mary" else "Start Mary",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        cancelPendingAutoListen()
                        stopSpeaking()
                        maryResponse = "Stopped speaking."
                        statusText = "Speech stopped"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stop Speaking",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        cancelPendingAutoListen()
                        if (isActive) {
                            startVoiceListening(false)
                        } else {
                            statusText = "Start Mary first."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isListening) "Listening..." else "Listen",
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF020617)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "You said:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = spokenText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Detected command:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = detectedCommand,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Mary response:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = maryResponse,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFACC15)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Next: DM/group message upgrade",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8)
        )
    }
}
