package com.aura.weather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.weather.handtracking.DetectedHand
import com.aura.weather.handtracking.HandTracker
import com.aura.weather.input.AccessibilityInputController
import com.aura.weather.input.HandDriveAccessibilityService
import com.aura.weather.input.HandDriveTrackingService
import com.aura.weather.overlay.HandDriveCameraOverlayService
import android.provider.Settings
import android.net.Uri
import android.os.Build
import com.aura.weather.input.HandXSteeringMapper
import com.aura.weather.input.InputController
import com.aura.weather.input.SteeringInputState
import com.aura.weather.steering.HandPoint
import com.aura.weather.steering.SteeringCalculator
import com.aura.weather.steering.SteeringOutput
import com.aura.weather.steering.SteeringSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------
// HandDrive design tokens - dark "telemetry HUD" theme.
// ---------------------------------------------------------------------------
private val HudBackground = Color(0xFF05070A)
private val HudPanel = Color(0xFF10141C)
private val HudPanelBorder = Color(0xFF232B38)
private val HudCyan = Color(0xFF00E5C7)
private val HudAmber = Color(0xFFFF7A29)
private val HudDanger = Color(0xFFFF5C5C)
private val HudTextPrimary = Color(0xFFE7ECEF)
private val HudTextSecondary = Color(0xFF7C8798)

/** Standard MediaPipe hand skeleton connections (21 landmarks, index 0 = wrist). */
private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,           // thumb
    0 to 5, 5 to 6, 6 to 7, 7 to 8,           // index
    5 to 9, 9 to 10, 10 to 11, 11 to 12,      // middle
    9 to 13, 13 to 14, 14 to 15, 15 to 16,    // ring
    13 to 17, 17 to 18, 18 to 19, 19 to 20,   // pinky
    0 to 17                                    // palm base
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandDriveTheme {
                HandDriveScreen()
            }
        }
    }
}

@Composable
private fun HandDriveTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = HudBackground,
        surface = HudPanel,
        primary = HudCyan,
        secondary = HudAmber,
        onBackground = HudTextPrimary,
        onSurface = HudTextPrimary,
        error = HudDanger
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun HandDriveScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var isTracking by remember { mutableStateOf(false) }
    var trackerReady by remember { mutableStateOf(false) }
    var handsDetected by remember { mutableStateOf(0) }
    var currentHands by remember { mutableStateOf<List<DetectedHand>>(emptyList()) }
    var steeringOutput by remember { mutableStateOf(SteeringOutput.CENTER) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Temporary diagnostic: AccessibilityService connection for gesture test mode.
    var a11yConnected by remember { mutableStateOf<Boolean>(HandDriveAccessibilityService.isConnected()) }
    var a11yHint by remember { mutableStateOf<String?>(null) }
    var virtualSteeringEnabled by remember { mutableStateOf(false) }
    var debugHandX by remember { mutableStateOf(Float.NaN) }
    var debugRawSteering by remember { mutableStateOf(0f) }
    var debugFinalSteering by remember { mutableStateOf(0f) }


    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val steeringCalculator = remember { SteeringCalculator() }
    val steeringSmoother = remember { SteeringSmoother() }
    val handXSteeringMapper = remember { HandXSteeringMapper() }
    // Input layer: maps smoothed steering to optional Accessibility gestures.
    // Requires the user to enable HandDriveAccessibilityService in system settings.
    // If the service is not connected, updateSteering fails soft (no crash).
    val inputController: InputController = remember {
        AccessibilityInputController(context.applicationContext)
    }

    val handTracker = remember {
        HandTracker(
            context = context,
            maxHands = 2,
            onResult = { result ->
                currentHands = result.hands
                handsDetected = result.hands.size

                // Sort by x so "left"/"right" reflect actual left/right hand
                // position on screen, not MediaPipe's handedness label
                // (which flips depending on mirroring) - this is what the
                // steering angle is meant to be based on.
                val sortedByX = result.hands.sortedBy { it.wrist.x }
                val left = sortedByX.getOrNull(0)?.let { HandPoint(it.wrist.x, it.wrist.y) }
                val right = sortedByX.getOrNull(1)?.let { HandPoint(it.wrist.x, it.wrist.y) }

                // Existing two-hand wheel path — unchanged for HUD.
                val smoothed = steeringSmoother.smooth(steeringCalculator.calculate(left, right))
                steeringOutput = smoothed

                // Hand-X virtual steering (MediaPipe wrist x already in [0,1], mirrored).
                val handX: Float? = when {
                    sortedByX.isEmpty() -> null
                    sortedByX.size == 1 -> sortedByX[0].wrist.x
                    else -> (sortedByX[0].wrist.x + sortedByX[1].wrist.x) * 0.5f
                }
                val mapped = handXSteeringMapper.process(handX)
                debugHandX = mapped.handX
                debugRawSteering = mapped.rawSteering
                debugFinalSteering = mapped.steering

                // Prefer HandDriveTrackingService for gestures so they continue when
                // this Activity is paused/minimized. Local path is fallback only.
                if (virtualSteeringEnabled && !HandDriveTrackingService.isServiceRunning) {
                    if (handX == null) {
                        HandDriveAccessibilityService.instance?.endSteeringDrag()
                    } else {
                        HandDriveAccessibilityService.instance?.updateSteeringDrag(mapped.steering)
                    }
                }
                if (HandDriveTrackingService.isServiceRunning) {
                    // Mirror service debug into UI when available.
                    if (!HandDriveTrackingService.lastHandX.isNaN()) {
                        debugHandX = HandDriveTrackingService.lastHandX
                        debugFinalSteering = HandDriveTrackingService.lastSteering
                    }
                }

                // Keep InputController informed (legacy path; gestures only when controller active).
                inputController.updateSteering(
                    SteeringInputState.from(
                        output = smoothed,
                        handsDetected = result.hands.size,
                        isTracking = true
                    )
                )
            },
            onError = { message -> errorMessage = message }
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        val listener = Runnable { cameraProvider = cameraProviderFuture.get() }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { }
    }

    fun bindCamera(): Boolean {
        val provider = cameraProvider
        if (provider == null) {
            errorMessage = "Camera is still initializing — try again in a moment."
            return false
        }
        return try {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase ->
                    analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (handTracker.isReady) {
                            handTracker.detect(imageProxy, isFrontCamera = true)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
            errorMessage = null
            true
        } catch (e: Exception) {
            errorMessage = "Couldn't start the front camera: ${e.message}"
            false
        }
    }

    fun stopTracking() {
        cameraProvider?.unbindAll()
        isTracking = false
        currentHands = emptyList()
        handsDetected = 0
        steeringSmoother.reset()
        steeringOutput = SteeringOutput.CENTER
        handXSteeringMapper.reset()
        debugHandX = Float.NaN
        debugRawSteering = 0f
        debugFinalSteering = 0f
        virtualSteeringEnabled = false
        HandDriveTrackingService.disableSteering(context.applicationContext)
        HandDriveAccessibilityService.instance?.endSteeringDrag()
        inputController.stop()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            val started = bindCamera()
            isTracking = started
            if (started) inputController.start()
        }
    }

    fun startTracking() {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val started = bindCamera()
        isTracking = started
        if (started) inputController.start()
    }

    // Poll AccessibilityService connection for the temporary gesture test panel.
    LaunchedEffect(Unit) {
        while (true) {
            a11yConnected = HandDriveAccessibilityService.isConnected()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Load the MediaPipe model off the UI thread once, on first composition.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            handTracker.setup()
        }
        trackerReady = handTracker.isReady
    }

    // Release the camera and the native MediaPipe task when this screen
    // leaves composition, so nothing keeps running/leaking in the background.
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            handTracker.close()
            cameraExecutor.shutdown()
            inputController.stop()
            // Do NOT auto-stop HandDriveTrackingService here — user may have
            // minimized the activity while START STEERING is active.
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = HudBackground) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            HandDriveHeader()
            Spacer(Modifier.height(12.dp))

            // Always visible at top of HandDriveScreen (launch UI) — not below camera.
            Text(
                text = if (a11yConnected) "Accessibility: Connected" else "Accessibility: Not connected",
                color = if (a11yConnected) HudCyan else HudDanger,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            a11yHint?.let { hint ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    color = HudDanger,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (HandDriveAccessibilityService.isConnected()) {
                        a11yHint = null
                        HandDriveAccessibilityService.performTestSwipe()
                    } else {
                        a11yHint = "Enable HandDrive Accessibility Service first."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudAmber,
                    contentColor = HudBackground
                )
            ) {
                Text(
                    text = "TEST REAL SWIPE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!virtualSteeringEnabled) {
                        if (!HandDriveAccessibilityService.isConnected()) {
                            a11yHint = "Enable HandDrive Accessibility Service first."
                            return@Button
                        }
                        a11yHint = null
                        virtualSteeringEnabled = true
                        handXSteeringMapper.reset()
                        // Release Activity-bound CameraX so the foreground service can own it.
                        // Activity lifecycle otherwise unbinds analysis on ON_STOP (minimize).
                        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                        isTracking = false
                        // Background-capable pipeline: camera+hands live in foreground service
                        // so steering continues when MainActivity is minimized.
                        HandDriveTrackingService.enableSteering(context.applicationContext)
                        // Optional live bubble while steering (requires overlay permission).
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                            Settings.canDrawOverlays(context)
                        ) {
                            HandDriveTrackingService.showCameraBubble(context.applicationContext)
                        }
                    } else {
                        virtualSteeringEnabled = false
                        handXSteeringMapper.reset()
                        HandDriveTrackingService.disableSteering(context.applicationContext)
                        HandDriveAccessibilityService.instance?.endSteeringDrag()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (virtualSteeringEnabled) HudDanger else HudCyan,
                    contentColor = HudBackground
                )
            ) {
                Text(
                    text = if (virtualSteeringEnabled) "STOP STEERING" else "START STEERING",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Hand X: " + (if (debugHandX.isNaN()) "—" else "%.3f".format(debugHandX)),
                color = HudTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Raw steering: ${"%.1f".format(debugRawSteering)}",
                color = HudTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "Final steering: ${"%.1f".format(debugFinalSteering)}",
                color = HudAmber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(context)
                        ) {
                            a11yHint = "Grant display-over-other-apps permission for the camera bubble."
                            try {
                                val intent = android.content.Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                            return@Button
                        }
                        a11yHint = null
                        // Ensure tracking service is publishing frames, then show bubble.
                        HandDriveTrackingService.start(context.applicationContext)
                        HandDriveTrackingService.showCameraBubble(context.applicationContext)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudPanelBorder,
                        contentColor = HudTextPrimary
                    )
                ) {
                    Text("SHOW CAMERA BUBBLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        HandDriveTrackingService.hideCameraBubble(context.applicationContext)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudPanelBorder,
                        contentColor = HudTextPrimary
                    )
                ) {
                    Text("HIDE CAMERA BUBBLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            errorMessage?.let { message ->
                ErrorBanner(message)
                Spacer(Modifier.height(12.dp))
            }

            if (!hasCameraPermission) {
                PermissionRequestPanel(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            } else {
                CameraPreviewWithOverlay(
                    previewView = previewView,
                    hands = currentHands,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
            }

            Spacer(Modifier.height(14.dp))
            StatusRow(handsDetected = handsDetected, isTracking = isTracking, trackerReady = trackerReady)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VirtualWheel(angleDegrees = steeringOutput.angleDegrees)
                Column(horizontalAlignment = Alignment.End) {
                    ReadoutText(label = "STEERING", value = "${steeringOutput.steeringPercent.roundToInt()}%")
                    Spacer(Modifier.height(10.dp))
                    ReadoutText(label = "ANGLE", value = "${steeringOutput.angleDegrees.roundToInt()}°")
                }
            }

            Spacer(Modifier.height(14.dp))
            SteeringMeterBar(percent = steeringOutput.steeringPercent, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
            TrackingButton(
                isTracking = isTracking,
                enabled = trackerReady,
                onClick = { if (isTracking) stopTracking() else startTracking() },
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}

@Composable
private fun HandDriveHeader() {
    Column {
        Text(
            text = "PHASE 1 · PROTOTYPE",
            color = HudCyan,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "HAND DRIVE",
            color = HudTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HudDanger.copy(alpha = 0.12f))
            .border(1.dp, HudDanger.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = message, color = HudDanger, fontSize = 12.sp)
    }
}

@Composable
private fun PermissionRequestPanel(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HudPanel)
            .border(1.dp, HudPanelBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "CAMERA ACCESS REQUIRED",
            color = HudTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "HandDrive needs the front camera to track your hands and calculate the virtual steering wheel.",
            color = HudTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = HudCyan, contentColor = HudBackground)
        ) {
            Text("GRANT CAMERA ACCESS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    previewView: PreviewView,
    hands: List<DetectedHand>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HudPanel)
            .border(1.dp, HudPanelBorder, RoundedCornerShape(16.dp))
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCornerBrackets()
            hands.forEach { hand ->
                val points = hand.landmarks.map { Offset(it.x * size.width, it.y * size.height) }
                HAND_CONNECTIONS.forEach { (a, b) ->
                    if (a < points.size && b < points.size) {
                        drawLine(
                            color = HudCyan.copy(alpha = 0.85f),
                            start = points[a],
                            end = points[b],
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                points.forEach { p ->
                    drawCircle(color = HudCyan, radius = 5f, center = p)
                }
            }
        }
    }
}

/** Viewfinder-style corner brackets framing the camera preview. */
private fun DrawScope.drawCornerBrackets(
    color: Color = HudCyan,
    length: Float = 28f,
    inset: Float = 12f
) {
    val w = size.width
    val h = size.height
    val strokeWidth = 3f
    // top-left
    drawLine(color, Offset(inset, inset), Offset(inset + length, inset), strokeWidth)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + length), strokeWidth)
    // top-right
    drawLine(color, Offset(w - inset, inset), Offset(w - inset - length, inset), strokeWidth)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + length), strokeWidth)
    // bottom-left
    drawLine(color, Offset(inset, h - inset), Offset(inset + length, h - inset), strokeWidth)
    drawLine(color, Offset(inset, h - inset), Offset(inset, h - inset - length), strokeWidth)
    // bottom-right
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset - length, h - inset), strokeWidth)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - inset - length), strokeWidth)
}

@Composable
private fun StatusRow(handsDetected: Int, isTracking: Boolean, trackerReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "HANDS DETECTED: $handsDetected",
            color = HudTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            letterSpacing = 1.sp
        )

        val (dotColor, label) = when {
            !trackerReady -> HudTextSecondary to "MODEL LOADING…"
            isTracking -> HudCyan to "TRACKING ACTIVE"
            else -> HudTextSecondary to "TRACKING OFF"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = dotColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun VirtualWheel(angleDegrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(160.dp)) {
        val radius = size.minDimension / 2f * 0.82f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = HudPanelBorder,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )

        var tickDeg = -60
        while (tickDeg <= 60) {
            val rad = Math.toRadians((tickDeg - 90).toDouble())
            val inner = radius - 10f
            val start = Offset(
                center.x + (inner * cos(rad)).toFloat(),
                center.y + (inner * sin(rad)).toFloat()
            )
            val end = Offset(
                center.x + (radius * cos(rad)).toFloat(),
                center.y + (radius * sin(rad)).toFloat()
            )
            drawLine(
                color = if (tickDeg == 0) HudCyan else HudTextSecondary,
                start = start,
                end = end,
                strokeWidth = if (tickDeg == 0) 4f else 2f
            )
            tickDeg += 15
        }

        rotate(degrees = angleDegrees, pivot = center) {
            drawLine(
                color = HudAmber,
                start = center,
                end = Offset(center.x, center.y - radius + 6f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = HudAmber, radius = 8f, center = center)
        drawCircle(color = HudBackground, radius = 4f, center = center)
    }
}

@Composable
private fun ReadoutText(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = label, color = HudTextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(
            text = value,
            color = HudAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp
        )
    }
}

@Composable
private fun SteeringMeterBar(percent: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(10.dp)) {
        val trackY = size.height / 2f
        drawLine(
            color = HudPanelBorder,
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = HudTextSecondary,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2f
        )
        val clamped = percent.coerceIn(-100f, 100f)
        val x = size.width / 2f + (clamped / 100f) * (size.width / 2f)
        drawCircle(color = HudAmber, radius = size.height / 2f, center = Offset(x, trackY))
    }
}

@Composable
private fun TrackingButton(
    isTracking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTracking) HudDanger else HudCyan,
            contentColor = HudBackground,
            disabledContainerColor = HudPanelBorder,
            disabledContentColor = HudTextSecondary
        )
    ) {
        Text(
            text = when {
                !enabled -> "LOADING MODEL…"
                isTracking -> "STOP TRACKING"
                else -> "START TRACKING"
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * TEMPORARY diagnostic panel to verify that [HandDriveAccessibilityService]
 * injects real system gestures via [android.accessibilityservice.AccessibilityService.dispatchGesture].
 * Independent of MediaPipe / tracking. Remove once injection is confirmed on device.
 */
@Composable
private fun AccessibilityGestureTestPanel(a11yConnected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HudPanelBorder, RoundedCornerShape(12.dp))
            .background(HudPanel, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "A11Y GESTURE TEST (TEMP)",
            color = HudCyan,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (a11yConnected) "Accessibility: Connected" else "Accessibility: Not connected",
            color = if (a11yConnected) HudCyan else HudDanger,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        if (!a11yConnected) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enable HandDrive Accessibility Service first.\nSettings → Accessibility → HandDrive",
                color = HudDanger,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (HandDriveAccessibilityService.isConnected()) {
                    HandDriveAccessibilityService.performTestSwipe()
                }
            },
            enabled = true,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HudAmber,
                contentColor = HudBackground,
                disabledContainerColor = HudPanelBorder,
                disabledContentColor = HudTextSecondary
            )
        ) {
            Text(
                "TEST REAL SWIPE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { HandDriveAccessibilityService.testLeftSwipe() },
                enabled = a11yConnected,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudPanelBorder,
                    contentColor = HudTextPrimary,
                    disabledContainerColor = HudPanelBorder,
                    disabledContentColor = HudTextSecondary
                )
            ) {
                Text("LEFT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { HandDriveAccessibilityService.testRightSwipe() },
                enabled = a11yConnected,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudPanelBorder,
                    contentColor = HudTextPrimary,
                    disabledContainerColor = HudPanelBorder,
                    disabledContentColor = HudTextSecondary
                )
            ) {
                Text("RIGHT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { HandDriveAccessibilityService.testCenterTap() },
                enabled = a11yConnected,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudPanelBorder,
                    contentColor = HudTextPrimary,
                    disabledContainerColor = HudPanelBorder,
                    disabledContentColor = HudTextSecondary
                )
            ) {
                Text("TAP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Real swipe uses dispatchGesture on the system display (not in-app). logcat: HandDriveInput",
            color = HudTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
