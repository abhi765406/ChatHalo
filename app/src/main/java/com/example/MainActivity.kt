package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.example.ui.theme.MyApplicationTheme
import com.example.webrtc.SignalingClient
import com.example.webrtc.WebRTCManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.firebase.FirebaseApp
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val permissionsState = rememberMultiplePermissionsState(
                        permissions = listOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )

                    if (permissionsState.allPermissionsGranted) {
                        CallScreen()
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Camera and Microphone permissions are required for video calls.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallScreen() {
    var roomCode by remember { mutableStateOf("") }
    var inCall by remember { mutableStateOf(false) }
    var isCaller by remember { mutableStateOf(false) }
    var reconnectTrigger by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf("Disconnected") }
    var showEndCallDialog by remember { mutableStateOf(false) }
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogContent by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    // Check if Firebase is initialized
    LaunchedEffect(Unit) {
        try {
            com.google.firebase.FirebaseApp.getInstance()
        } catch (e: Exception) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApiKey("AIzaSyAqyqSmrzzK4JdEBFepsQBcod8G-ptg9Oc")
                .setApplicationId("1:592994181533:web:dc923aeb6a5eed3b974f74")
                .setDatabaseUrl("https://hologramcall-default-rtdb.firebaseio.com")
                .setProjectId("hologramcall")
                .setStorageBucket("hologramcall.firebasestorage.app")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
    }

    if (!inCall) {
        val activity = LocalContext.current as? ComponentActivity
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = { activity?.finish() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .padding(top = 32.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Exit")
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Hologram Call", 
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter a room code to start or join a meeting", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(48.dp))
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it },
                    label = { Text("Room Code") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { 
                            isCaller = true
                            if (roomCode.isNotEmpty()) inCall = true 
                        },
                        enabled = roomCode.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Create")
                    }
                    Button(
                        onClick = { 
                            isCaller = false
                            if (roomCode.isNotEmpty()) inCall = true 
                        },
                        enabled = roomCode.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Join")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        val file = java.io.File(context.getExternalFilesDir(null), "crash_log.txt")
                        crashLogContent = if (file.exists()) {
                            file.readText()
                        } else {
                            "No crash log found."
                        }
                        showCrashLogDialog = true
                    }
                ) {
                    Text("View Last Crash Log")
                }
            }
        }
        if (showCrashLogDialog) {
            AlertDialog(
                onDismissRequest = { showCrashLogDialog = false },
                title = { Text("Crash Log") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        item {
                            Text(crashLogContent, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCrashLogDialog = false }) { Text("Close") }
                }
            )
        }
    } else {
        if (showEndCallDialog) {
            AlertDialog(
                onDismissRequest = { showEndCallDialog = false },
                title = { Text("End call?") },
                text = { Text("Are you sure you want to end this call?") },
                confirmButton = {
                    TextButton(onClick = {
                        showEndCallDialog = false
                        inCall = false
                    }) { Text("End", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndCallDialog = false }) { Text("Cancel") }
                }
            )
        }

        BackHandler(enabled = inCall) {
            showEndCallDialog = true
        }

        key(reconnectTrigger) {
            var webRTCManager by remember { mutableStateOf<WebRTCManager?>(null) }
            val localView = remember { SurfaceViewRenderer(context) }
            val remoteView = remember { SurfaceViewRenderer(context) }
            var callDurationSeconds by remember { mutableIntStateOf(0) }
            
            LaunchedEffect(connectionState) {
                if (connectionState == "CONNECTED") {
                    while(true) {
                        kotlinx.coroutines.delay(1000)
                        callDurationSeconds++
                    }
                }
            }
            
            DisposableEffect(Unit) {
                val signalingClient = SignalingClient(roomCode, object : SignalingClient.SignalingListener {
                    override fun onOfferReceived(description: SessionDescription) {
                        webRTCManager?.onOfferReceived(description)
                    }
                    override fun onAnswerReceived(description: SessionDescription) {
                        webRTCManager?.onAnswerReceived(description)
                    }
                    override fun onIceCandidateReceived(candidate: IceCandidate) {
                        webRTCManager?.onIceCandidateReceived(candidate)
                    }
                })
                
                val manager = WebRTCManager(
                    context = context,
                    signalingClient = signalingClient,
                    localSurfaceView = localView,
                    remoteSurfaceView = remoteView,
                    onConnectionStateChange = { state ->
                        connectionState = state.name
                    }
                )
                webRTCManager = manager
                
                if (isCaller) {
                    manager.startCall()
                } else {
                    manager.joinCall()
                }
                
                onDispose {
                    manager.destroy()
                    signalingClient.destroy()
                }
            }
    
            var arSceneView by remember { mutableStateOf<io.github.sceneview.SceneView?>(null) }
            var webRtcNode by remember { mutableStateOf<com.example.webrtc.WebRtcNode?>(null) }
            
            DisposableEffect(arSceneView, webRTCManager) {
                val view = arSceneView
                val manager = webRTCManager
                if (view != null && manager != null) {
                    val node = com.example.webrtc.WebRtcNode(
                        engine = view.engine,
                        materialLoader = view.materialLoader,
                        eglContext = manager.getEglContext()
                    )
                    webRtcNode = node
                    manager.setRemoteVideoSink(node.videoSink)
                    
                    view.addChildNode(node)
                    node.position = io.github.sceneview.math.Position(x = 0.0f, y = 0.0f, z = -1.5f)
                    node.isEditable = true
                    node.isPositionEditable = true
                    node.isScaleEditable = true
                    node.isRotationEditable = true
                }
                onDispose {
                    webRtcNode?.destroy()
                }
            }
    
            var pipOffsetX by remember { mutableFloatStateOf(0f) }
            var pipOffsetY by remember { mutableFloatStateOf(0f) }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Remote View - Hologram in AR
                AndroidView(
                    factory = { 
                        io.github.sceneview.SceneView(it).apply {
                            arSceneView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
    
                // Local View (Picture in Picture)
                AndroidView(
                    factory = { localView },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .padding(top = 80.dp)
                        .offset { androidx.compose.ui.unit.IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                        .size(100.dp, 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                pipOffsetX += dragAmount.x
                                pipOffsetY += dragAmount.y
                            }
                        }
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showEndCallDialog = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    // Connection State Badge
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                if (connectionState == "CONNECTING" || connectionState == "NEW") {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (connectionState == "CONNECTED") {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val textToShow = if (connectionState == "CONNECTED") {
                                    val minutes = callDurationSeconds / 60
                                    val seconds = callDurationSeconds % 60
                                    "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                                } else {
                                    connectionState.lowercase().replaceFirstChar { it.uppercase() }
                                }
                                Text(
                                    text = textToShow,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    
                    // Spacer for symmetry
                    Spacer(modifier = Modifier.size(48.dp))
                }
                
                // Reconnect button if disconnected
                if (connectionState == "FAILED" || connectionState == "DISCONNECTED" || connectionState == "CLOSED") {
                    Button(
                        onClick = { reconnectTrigger++ },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("Reconnect")
                    }
                }
    
                // Controls Bottom Bar
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                webRTCManager?.toggleMute(isMuted)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(if (isMuted) Color.White else Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, 
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }
                        
                        IconButton(
                            onClick = { showEndCallDialog = true },
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CallEnd, 
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { webRTCManager?.switchCamera() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Cameraswitch, 
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
