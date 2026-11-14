package com.system.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.SharedPreferences
import java.io.File

private val PaleP = Color(0xFFB9D9FF)
private val PanelP = Color(0xFA050C1A)

object ProfileStore {
    private fun file(c: Context) = File(c.filesDir, "profile.jpg")

    fun load(c: Context): ImageBitmap? {
        val f = file(c)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.asImageBitmap()
    }

    // Copies the picked photo into the app (square crop, 400px).
    fun saveFrom(c: Context, uri: Uri): Boolean {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            val orient = c.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val deg = when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val rotated = if (deg != 0f) {
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
            } else {
                bmp
            }
            val side = minOf(rotated.width, rotated.height)
            val square = Bitmap.createBitmap(
                rotated, (rotated.width - side) / 2, (rotated.height - side) / 2, side, side
            )
            val out = Bitmap.createScaledBitmap(square, 400, 400, true)
            file(c).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

@Composable
fun ProfileCircle(size: Dp, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).border(2.dp, PaleP, CircleShape).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(Modifier.size(size * 0.22f).background(Ice, CircleShape))
                Box(
                    Modifier.size(size * 0.45f, size * 0.2f)
                        .background(Ice, RoundedCornerShape(topStart = size * 0.2f, topEnd = size * 0.2f))
                )
            }
        }
    }
}

@Composable
fun TopBar(photoVersion: Int, onMenu: () -> Unit, onStatus: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(PlayerStore.read(ctx)) }
    val photo = remember(photoVersion) { ProfileStore.load(ctx) }
    DisposableEffect(Unit) {
        val prefs = ctx.getSharedPreferences("system_player", Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> p = PlayerStore.read(ctx) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    Row(
        Modifier.fillMaxWidth().border(1.dp, SysBlue.copy(alpha = 0.6f)).background(PanelP).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).border(1.dp, PaleP).clickable { onMenu() },
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Box(Modifier.width(20.dp).height(2.dp).background(Ice)) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { onStatus() }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("LV ${p.level}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = GlowStyle)
                Spacer(Modifier.width(10.dp))
                Text("RANK ${p.rank}", color = Ice, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${p.xp} XP", color = Color(0xFF7FE8FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, SysBlue)) {
                Box(
                    Modifier.fillMaxWidth((p.xpInLevel / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(SysBlue, Color(0xFF7FE8FF))))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        ProfileCircle(44.dp, photo, Modifier.clickable { onProfile() })
    }
}

@Composable
fun ProfileContent(photoVersion: Int, onChange: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val photo = remember(photoVersion) { ProfileStore.load(ctx) }
    val p = PlayerStore.read(ctx)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileCircle(120.dp, photo)
        Spacer(Modifier.height(12.dp))
        Text("PLAYER", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, style = GlowStyle)
        Spacer(Modifier.height(4.dp))
        Text("LV ${p.level}  •  RANK ${p.rank}  •  ${p.xp} XP", color = Ice, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(16.dp))
        SysBtn("[ CHANGE PHOTO ]", modifier = Modifier.fillMaxWidth()) { onChange() }
        Spacer(Modifier.height(10.dp))
        SysBtn("[ CLOSE ]", Color(0xFF5E7FA8), Modifier.fillMaxWidth()) { onClose() }
    }
}