package com.system.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

data class ProofResult(val ok: Boolean, val message: String)

fun Quest.effectiveProof(): String = if (proofType == "AUTO") ProofRules.guess(title) else proofType

// Picks the proof type from keywords in the quest name (English / Hinglish / Hindi).
object ProofRules {
    private val POSE_STARTS = listOf(
        "squat", "plank", "crunch", "lunge", "burpee", "stretch", "workout",
        "exercis", "kasrat", "vyayam", "baithak", "cardio", "calisthenic"
    )
    private val POSE_EXACT = listOf("gym", "abs", "dips", "yoga", "dand", "dands")
    private val POSE_CONTAINS = listOf(
        "pushup", "situp", "pullup", "jumpingjack", "पुशअप", "दंड", "बैठक", "कसरत", "व्यायाम", "योग"
    )
    private val TEXT_STARTS = listOf(
        "study", "studi", "read", "book", "notes", "homework", "assignment",
        "padhai", "padhna", "padh", "revis", "learn", "chapter", "syllabus", "lecture"
    )
    private val TEXT_EXACT = listOf("note", "dsa", "math", "maths", "physics", "chemistry")
    private val TEXT_CONTAINS = listOf("पढ", "किताब", "नोट्स")

    private fun tokens(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }

    private fun joined(title: String): String =
        title.lowercase().replace(Regex("[^\\p{L}\\p{M}]"), "")

    private fun hit(title: String, starts: List<String>, exact: List<String>, contains: List<String>): Boolean {
        val t = tokens(title)
        val j = joined(title)
        return t.any { w -> starts.any { w.startsWith(it) } || w in exact } ||
            contains.any { j.contains(it) }
    }

    fun guess(title: String): String = when {
        hit(title, listOf("scan", "barcode"), listOf("qr"), emptyList()) -> "QR"
        hit(title, POSE_STARTS, POSE_EXACT, POSE_CONTAINS) -> "POSE"
        hit(title, TEXT_STARTS, TEXT_EXACT, TEXT_CONTAINS) -> "TEXT"
        else -> "ANY"
    }

    // Which body-position rule to apply for POSE proofs.
    fun exercise(title: String): String = when {
        hit(title, listOf("plank"), listOf("dips"), listOf("pushup", "पुशअप")) -> "PUSH"
        hit(title, listOf("squat", "lunge"), emptyList(), listOf("बैठक")) -> "SQUAT"
        else -> "GENERAL"
    }
}

private suspend fun <T> Task<T>.awaitIt(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

object ProofCheck {
    suspend fun verify(file: File, q: Quest): ProofResult = withContext(Dispatchers.Default) {
        val type = q.effectiveProof()
        if (type == "ANY") return@withContext ProofResult(true, "Photo accepted.")
        val bmp = loadBitmap(file)
            ?: return@withContext ProofResult(false, "Could not read the photo. Try again.")
        try {
            when (type) {
                "POSE" -> checkPose(bmp, ProofRules.exercise(q.title))
                "TEXT" -> checkText(bmp)
                "QR" -> checkQr(bmp)
                else -> ProofResult(true, "Photo accepted.")
            }
        } catch (e: Exception) {
            // AI model not available: don't block the quest.
            ProofResult(true, "AI check unavailable. Photo accepted.")
        }
    }

    private fun loadBitmap(file: File, maxSide: Int = 1280): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var s = 1
        while (bounds.outWidth / s > maxSide || bounds.outHeight / s > maxSide) s *= 2
        val bmp = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = s }
        ) ?: return null
        val orient = try {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val deg = when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (deg != 0f) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        } else {
            bmp
        }
    }

    private fun mid(a: PointF?, b: PointF?): PointF? = when {
        a != null && b != null -> PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        a != null -> a
        else -> b
    }

    private fun angleDeg(a: PointF, b: PointF, c: PointF): Double {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()
        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()
        val n = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (n == 0.0) return 180.0
        return Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / n).coerceIn(-1.0, 1.0)))
    }

    private suspend fun checkPose(bmp: Bitmap, exercise: String): ProofResult {
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        val pose = try {
            detector.process(InputImage.fromBitmap(bmp, 0)).awaitIt()
        } finally {
            detector.close()
        }
        fun lm(type: Int): PointF? {
            val l = pose.getPoseLandmark(type) ?: return null
            return if (l.inFrameLikelihood >= 0.5f) l.position else null
        }
        val ls = lm(PoseLandmark.LEFT_SHOULDER)
        val rs = lm(PoseLandmark.RIGHT_SHOULDER)
        val le = lm(PoseLandmark.LEFT_ELBOW)
        val re = lm(PoseLandmark.RIGHT_ELBOW)
        val lh = lm(PoseLandmark.LEFT_HIP)
        val rh = lm(PoseLandmark.RIGHT_HIP)
        val lk = lm(PoseLandmark.LEFT_KNEE)
        val rk = lm(PoseLandmark.RIGHT_KNEE)
        val la = lm(PoseLandmark.LEFT_ANKLE)
        val ra = lm(PoseLandmark.RIGHT_ANKLE)

        val visible = listOfNotNull(ls, rs, le, re, lh, rh, lk, rk, la, ra).size
        if (visible < 6) {
            return ProofResult(false, "Body not clear. Step back so your whole body is in the frame.")
        }
        return when (exercise) {
            "PUSH" -> {
                val sh = mid(ls, rs)
                val hp = mid(lh, rh)
                if (sh == null || hp == null) {
                    ProofResult(false, "Shoulders and hips not visible. Show your body from the side.")
                } else {
                    val ang = Math.toDegrees(
                        atan2(abs(sh.y - hp.y).toDouble(), abs(sh.x - hp.x).toDouble())
                    )
                    if (ang < 45.0) {
                        ProofResult(true, "Pose verified: body is horizontal.")
                    } else {
                        ProofResult(false, "Push-up position not detected. Keep your body horizontal.")
                    }
                }
            }
            "SQUAT" -> {
                val angs = listOfNotNull(
                    if (lh != null && lk != null && la != null) angleDeg(lh, lk, la) else null,
                    if (rh != null && rk != null && ra != null) angleDeg(rh, rk, ra) else null
                )
                if (angs.isNotEmpty() && angs.min() < 150.0) {
                    ProofResult(true, "Pose verified: knees bent.")
                } else {
                    ProofResult(false, "Squat depth not detected. Bend your knees more.")
                }
            }
            else -> ProofResult(true, "Pose verified: body detected.")
        }
    }

    private suspend fun checkText(bmp: Bitmap): ProofResult {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val res = try {
            client.process(InputImage.fromBitmap(bmp, 0)).awaitIt()
        } finally {
            client.close()
        }
        val words = res.text.split(Regex("\\s+")).count { w -> w.count { it.isLetterOrDigit() } >= 2 }
        return if (words >= 8) {
            ProofResult(true, "Text verified: $words words found.")
        } else {
            ProofResult(false, "Not enough text found ($words words). Show your notes or book page clearly.")
        }
    }

    private suspend fun checkQr(bmp: Bitmap): ProofResult {
        val scanner = BarcodeScanning.getClient()
        val codes = try {
            scanner.process(InputImage.fromBitmap(bmp, 0)).awaitIt()
        } finally {
            scanner.close()
        }
        return if (codes.isNotEmpty()) {
            ProofResult(true, "Code scanned.")
        } else {
            ProofResult(false, "No QR or barcode found. Point the camera at the code.")
        }
    }
}
