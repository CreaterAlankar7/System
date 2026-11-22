package com.system.app

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

// Set while the camera screen is open: volume / Bluetooth shutter keys call this.
object CameraKeys {
    @Volatile var onKey: (() -> Unit)? = null
}

@Composable
fun CameraProof(questId: Long, onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val previewView = remember { PreviewView(ctx) }
    val capture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            )
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var front by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var isRec by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cancelled = remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onCaptured)

    LaunchedEffect(front, video) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                if (video) {
                    provider.bindToLifecycle(owner, selector, preview, videoCapture)
                } else {
                    provider.bindToLifecycle(owner, selector, preview, capture)
                }
            } catch (e: Exception) {
                error = "Camera failed to start."
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun newFile(ext: String): File {
        val dir = File(ctx.filesDir, "proofs").apply { mkdirs() }
        return File(dir, "q${questId}_${System.currentTimeMillis()}$ext")
    }

    fun takePhoto() {
        if (busy || isRec) return
        val file = newFile(".jpg")
        busy = true
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    busy = false
                    done(file)
                }

                override fun onError(e: ImageCaptureException) {
                    busy = false
                    error = "Capture failed. Try again."
                }
            }
        )
    }

    fun startRec() {
        if (isRec) return
        val file = newFile(".mp4")
        val opts = FileOutputOptions.Builder(file).build()
        recording = recorder.prepareRecording(ctx, opts)
            .start(ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    isRec = false
                    recording = null
                    if (cancelled.value) {
                        file.delete()
                    } else if (ev.hasError() && file.length() < 2000) {
                        error = "Recording failed. Try again."
                        file.delete()
                    } else {
                        done(file)
                    }
                }
            }
        isRec = true
        secs = 0
        error = null
    }

    fun stopRec() {
        if (isRec && secs >= 3) recording?.stop()
    }

    // Auto-stop at 5 seconds.
    LaunchedEffect(isRec) {
        if (isRec) {
            secs = 0
            while (secs < 5) {
                delay(1000)
                secs++
            }
            recording?.stop()
        }
    }

    val keyAction by rememberUpdatedState<() -> Unit>({
        if (video) {
            if (!isRec) startRec() else stopRec()
        } else {
            takePhoto()
        }
    })
    DisposableEffect(Unit) {
        CameraKeys.onKey = { keyAction() }
        onDispose {
            CameraKeys.onKey = null
            runCatching { recording?.stop() }
            runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xB3050B18)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "[ PROOF REQUIRED ]", color = SysBlue, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(
                when {
                    error != null -> error ?: ""
                    isRec -> "● REC  0:0$secs / 0:05" + if (secs >= 3) "   (STOP is ready)" else ""
                    video -> "Video: 3 to 5 seconds. Volume / Bluetooth button starts and stops."
                    else -> "Photo: tap CAPTURE or press Volume / Bluetooth button."
                },
                color = if (error != null || isRec) SysRed else Color.White, fontSize = 12.sp
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xB3050B18)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("PHOTO" to false, "VIDEO" to true).forEach { (label, isVid) ->
                    val on = video == isVid
                    Box(
                        Modifier.border(2.dp, if (on) SysBlue else Color(0xFF5E7FA8))
                            .background(if (on) SysBlue.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { if (!isRec && !busy) video = isVid }
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label, color = if (on) Color.White else Color(0xFF5E7FA8),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SysBtn("[ CANCEL ]", Color(0xFF5E7FA8)) {
                    cancelled.value = true
                    runCatching { recording?.stop() }
                    onCancel()
                }
                if (!video) {
                    SysBtn(if (busy) "[ ... ]" else "[ CAPTURE ]") { takePhoto() }
                } else if (!isRec) {
                    SysBtn("[ ● RECORD ]", SysRed) { startRec() }
                } else {
                    SysBtn(
                        if (secs >= 3) "[ STOP ]" else "[ REC ${secs}s ]",
                        if (secs >= 3) SysRed else Color(0xFF5E7FA8)
                    ) { stopRec() }
                }
                SysBtn("[ FLIP ]") { if (!isRec) front = !front }
            }
        }
    }
}
