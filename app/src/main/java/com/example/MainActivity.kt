package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class ComandoState(
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val identifiedApp: String = "",
    val actionResult: String = "",
    val error: String = ""
)

class ComandoViewModel(application: Application) : AndroidViewModel(application), RecognitionListener {

    private val _uiState = MutableStateFlow(ComandoState())
    val uiState: StateFlow<ComandoState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _uiState.update { it.copy(
                error = "Reconhecimento de voz indisponível",
                isListening = false,
                recognizedText = ""
            ) }
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication())
            speechRecognizer?.setRecognitionListener(this)
        }
        
        _uiState.update { it.copy(
            isListening = true,
            recognizedText = "Ouvindo...",
            identifiedApp = "",
            actionResult = "",
            error = ""
        ) }
        speechRecognizer?.startListening(speechIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _uiState.update { it.copy(isListening = false) }
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio"
            SpeechRecognizer.ERROR_CLIENT -> "Erro no cliente"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão insuficiente"
            SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de rede"
            SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi o que foi dito"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado"
            SpeechRecognizer.ERROR_SERVER -> "Erro no servidor"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala detectada"
            else -> "Erro desconhecido ($error)"
        }
        _uiState.update { it.copy(
            isListening = false,
            error = errorMessage,
            recognizedText = ""
        ) }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            processCommand(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _uiState.update { it.copy(recognizedText = matches[0]) }
        }
    }
    
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun processCommand(text: String) {
        _uiState.update { it.copy(recognizedText = "Processando...") }
        val lowerText = text.trim().lowercase(Locale.ROOT)
        
        // Match variations of "abrir [app]"
        val regex = Regex("^(abrir|abra|iniciar|abra o|abrir o)\\s+(.+)")
        val matchResult = regex.find(lowerText)

        if (matchResult != null) {
            val rawAppName = matchResult.groupValues[2].trim()
            val (packageName, formattedName) = when (rawAppName) {
                "chatgpt", "chat gpt" -> Pair("com.openai.chatgpt", "ChatGPT")
                "youtube", "you tube" -> Pair("com.google.android.youtube", "YouTube")
                "instagram", "insta" -> Pair("com.instagram.android", "Instagram")
                "whatsapp", "zap" -> Pair("com.whatsapp", "WhatsApp")
                "spotify" -> Pair("com.spotify.music", "Spotify")
                else -> Pair(null, rawAppName)
            }
            
            _uiState.update { it.copy(recognizedText = text) } // restore original text

            if (packageName != null) {
                openApp(packageName, formattedName)
            } else {
                _uiState.update { it.copy(
                    error = "Não encontrei esse aplicativo na lista suportada.",
                    identifiedApp = rawAppName
                ) }
            }
        } else {
            _uiState.update { it.copy(
                recognizedText = text,
                error = "Não consegui entender. Diga 'abrir [aplicativo]'."
            ) }
        }
    }

    private fun openApp(packageName: String, formattedName: String) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            _uiState.update { it.copy(
                identifiedApp = formattedName,
                actionResult = "Aplicativo aberto"
            ) }
        } else {
             _uiState.update { it.copy(
                 identifiedApp = formattedName,
                 actionResult = "O aplicativo não está instalado."
             ) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Force Dark Theme for modern look
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF1C1B1F),
                    surface = Color(0xFF2B2930),
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    onBackground = Color(0xFFE6E1E5),
                    onSurface = Color(0xFFE6E1E5),
                    outline = Color(0xFF49454F)
                )
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ComandoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ComandoScreen(
    modifier: Modifier = Modifier,
    viewModel: ComandoViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            if (granted) {
                viewModel.startListening()
            }
        }
    )

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(3.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                    )
                }
                Text(
                    text = "Comando",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Main Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isListening) {
                    // Pulsing border
                    Box(
                        modifier = Modifier
                            .size(192.dp)
                            .scale(pulseScale)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                    )
                    // Blur background
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .blur(24.dp)
                    )
                }

                // Main Mic Button
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !uiState.isListening) {
                            if (hasPermission) {
                                viewModel.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Ouvir comando",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(100.dp)
            ) {
                Text(
                    text = if (uiState.isListening) "OUVINDO" else if (uiState.error.isNotEmpty()) "ERRO" else "COMANDO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = Color(0xFFCAC4D0)
                )
                
                val displayState = if (!hasPermission) {
                    "Permissão de microfone necessária"
                } else if (uiState.error.isNotEmpty()) {
                    uiState.error 
                } else if (uiState.recognizedText.isNotEmpty()) {
                    if (uiState.recognizedText == "Ouvindo..." || uiState.recognizedText == "Processando...") uiState.recognizedText
                    else "\"${uiState.recognizedText}\""
                } else {
                    "\"Toque para falar\""
                }
                
                Text(
                    text = displayState,
                    fontSize = if (displayState.length > 20 && !displayState.startsWith("\"")) 20.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = if (uiState.error.isNotEmpty()) MaterialTheme.colorScheme.error else Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = if (displayState.length > 20) 28.sp else 40.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Footer Card
        if (uiState.identifiedApp.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = Color.Black.copy(alpha = 0.5f)
                        )
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "APLICATIVO IDENTIFICADO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = Color(0xFFCAC4D0)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.identifiedApp,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10A37F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = "App Icon",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.actionResult.contains("sucesso", ignoreCase = true)) {
                             Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            text = uiState.actionResult,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
             Spacer(modifier = Modifier.height(120.dp))
        }
    }
}
