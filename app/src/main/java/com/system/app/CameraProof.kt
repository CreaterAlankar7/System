package com.system.app

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import java.io.File

@Composable
fun CameraProof(questId: Long, onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val previewView = remember { PreviewView(ctx) }
    val capture = remember { ImageCapture.Builder().build() }
    var front by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, capture)
            } catch (e: Exception) {
                error = "Camera failed to start."
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    DisposableEffect(Unit) {
        onDispose {
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
            Text("[ PROOF REQUIRED ]", color = SysBlue, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(error ?: "Show your proof clearly in the frame.",
                color = if (error != null) SysRed else Color.White, fontSize = 12.sp)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xB3050B18)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SysBtn("[ CANCEL ]", Color(0xFF5E7FA8)) { onCancel() }
            SysBtn(if (busy) "[ ... ]" else "[ CAPTURE ]") {
                if (!busy) {
                    val dir = File(ctx.filesDir, "proofs").apply { mkdirs() }
                    val file = File(dir, "q${questId}_${System.currentTimeMillis()}.jpg")
                    busy = true
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                busy = false
                                onCaptured(file)
                            }

                            override fun onError(e: ImageCaptureException) {
                                busy = false
                                error = "Capture failed. Try again."
                            }
                        }
                    )
                }
            }
            SysBtn("[ FLIP ]") { front = !front }
        }
    }
}