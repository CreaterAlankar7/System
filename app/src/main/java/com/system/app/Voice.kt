package com.system.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.Locale

// The quest being registered (shared by the form and by voice).
class QuestDraft {
    var title by mutableStateOf("")
    var stat by mutableStateOf("STR")
    var rtype by mutableStateOf("DAILY")
    var mask by mutableIntStateOf(0)
    var date by mutableStateOf(LocalDate.now())
    var sh by mutableIntStateOf(7)
    var sm by mutableIntStateOf(0)
    var eh by mutableIntStateOf(7)
    var em by mutableIntStateOf(30)

    fun reset() {
        title = ""
        stat = "STR"
        rtype = "DAILY"
        mask = 0
        date = LocalDate.now()
        sh = 7
        sm = 0
        eh = 7
        em = 30
    }
}

enum class VoiceEvent { OPEN_NEW, OPEN_STATUS, OPEN_PROFILE, REGISTER, CANCEL_FORM }

enum class Cmd { NEW_QUEST, STATUS, PROFILE, CANCEL, UNKNOWN }

class ParsedTime(val h: Int, val m: Int, val period: Int) // period: 0 unknown, 1 AM, 2 PM, 3 24h

// Understands English + Hindi (Devanagari and Roman) spoken text.
object VoiceParse {
    private val devDigits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val numWords: Map<String, Int> = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "chhah" to 6, "chah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12, "pandrah" to 15, "bees" to 20, "tees" to 30,
        "chalis" to 40, "paintalis" to 45, "pachas" to 50,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6,
        "छः" to 6, "छे" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11,
        "बारह" to 12, "पंद्रह" to 15, "बीस" to 20, "तीस" to 30, "चालीस" to 40, "पैंतालीस" to 45, "पचास" to 50
    )

    private val dayKeys = listOf(
        listOf("monday", "mon", "somvar", "somwar", "सोमवार", "सोम"),
        listOf("tuesday", "tue", "tues", "mangalvar", "mangalwar", "मंगलवार", "मंगल"),
        listOf("wednesday", "wed", "budhvar", "budhwar", "बुधवार", "बुध"),
        listOf("thursday", "thu", "thur", "thurs", "guruvar", "guruwar", "veervar", "गुरुवार", "बृहस्पतिवार", "वीरवार", "गुरु"),
        listOf("friday", "fri", "shukravar", "shukrawar", "शुक्रवार", "शुक्र"),
        listOf("saturday", "sat", "shanivar", "shaniwar", "शनिवार", "शनि"),
        listOf("sunday", "sun", "ravivar", "itvar", "itwar", "रविवार", "इतवार", "रवि")
    )

    private val monthKeys = listOf(
        listOf("january", "jan", "जनवरी"), listOf("february", "feb", "फरवरी"),
        listOf("march", "मार्च"), listOf("april", "अप्रैल"), listOf("may", "मई"),
        listOf("june", "जून"), listOf("july", "जुलाई"), listOf("august", "aug", "अगस्त"),
        listOf("september", "sep", "सितंबर", "सितम्बर"), listOf("october", "oct", "अक्टूबर"),
        listOf("november", "nov", "नवंबर", "नवम्बर"), listOf("december", "dec", "दिसंबर", "दिसम्बर")
    )

    fun norm(s: String): String {
        var t = s.lowercase(Locale.ROOT)
        t = t.map { devDigits[it] ?: it }.joinToString("")
        t = t.replace(Regex("[,.!?।;]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun sub(n: String, vararg ks: String) = ks.any { n.contains(it) }
    private fun words(n: String) = n.split(" ").filter { it.isNotEmpty() }

    fun hasWake(raw: String): Boolean =
        sub(norm(raw), "system", "सिस्टम", "सिस्टेम", "sistam", "sistem")

    fun command(raw: String): Cmd {
        val n = norm(raw)
        val quest = sub(n, "quest", "क्वेस्ट", "क्वैस्ट", "क्वेश्चन", "कवेस्ट")
        return when {
            sub(n, "cancel", "कैंसल", "रद्द", "band karo", "बंद करो", "stop", "रुको", "ruko") -> Cmd.CANCEL
            quest && sub(n, "new", "naya", "nayi", "naye", "नया", "नई", "नये", "बना", "bana", "add", "जोड") -> Cmd.NEW_QUEST
            sub(n, "status", "स्टेटस", "स्थिति", "level", "लेवल") -> Cmd.STATUS
            sub(n, "profile", "प्रोफाइल", "प्रोफ़ाइल") -> Cmd.PROFILE
            else -> Cmd.UNKNOWN
        }
    }

    fun yesNo(raw: String): Boolean? {
        val n = norm(raw)
        val ws = words(n).toSet()
        val no = ws.any { it in setOf("no", "nahi", "nahin", "na", "mat", "नहीं", "नही", "ना") } || n.contains("cancel")
        val yes = ws.any {
            it in setOf("yes", "yeah", "yep", "haan", "han", "ha", "ok", "okay", "register", "kardo", "हाँ", "हां", "हा", "ठीक", "रजिस्टर")
        } || n.contains("kar do") || n.contains("कर दो")
        return when {
            no -> false
            yes -> true
            else -> null
        }
    }

    fun stat(raw: String): String? {
        val n = norm(raw)
        val ws = words(n).toSet()
        return when {
            ws.any { it in setOf("str", "strength", "fitness", "exercise", "workout", "gym", "body") } ||
                sub(n, "स्ट्रेंथ", "स्ट्रैंथ", "ताकत", "कसरत", "व्यायाम", "फिटनेस", "शरीर") -> "STR"
            ws.any { it in setOf("int", "intelligence", "study", "padhai", "padhna", "mind", "brain") } ||
                sub(n, "इंटेलिजेंस", "पढ़ाई", "पढाई", "पढ़ना", "दिमाग", "इंट") -> "INT"
            ws.any { it in setOf("vit", "vitality", "health", "habit", "habits", "aadat") } ||
                sub(n, "वाइटैलिटी", "वाइटेलिटी", "हेल्थ", "स्वास्थ्य", "आदत") -> "VIT"
            else -> null
        }
    }

    fun repeatType(raw: String): String? {
        val n = norm(raw)
        val ws = words(n).toSet()
        return when {
            sub(n, "रोज", "हर दिन", "everyday", "every day") || ws.any { it in setOf("daily", "roz", "rozana") } -> "DAILY"
            sub(n, "तारीख", "तारिख", "tarikh", "date") -> "DATE"
            sub(n, "दिनों", "कुछ दिन", "weekday") || ws.any { it in setOf("days", "din") } -> "DAYS"
            else -> null
        }
    }

    fun mask(raw: String): Int {
        val n = norm(raw)
        val ws = words(n).toSet()
        if (sub(n, "weekday")) return 31
        if (sub(n, "weekend", "वीकेंड")) return 96
        if (ws.any { it in setOf("all", "everyday", "daily", "roz", "sab", "sabhi") } || sub(n, "सभी", "रोज", "हर दिन")) return 127
        var m = 0
        dayKeys.forEachIndexed { i, keys ->
            val hit = keys.any { k ->
                if (k.length <= 4 && k.all { it.code < 128 }) k in ws else n.contains(k)
            }
            if (hit) m = m or (1 shl i)
        }
        return m
    }

    fun date(raw: String, today: LocalDate): LocalDate? {
        val n = norm(raw)
        val toks = words(n)
        if (toks.any { it == "today" || it == "aaj" } || n.contains("आज")) return today
        if (toks.any { it == "parso" } || n.contains("परसों") || n.contains("परसो")) return today.plusDays(2)
        if (toks.any { it == "tomorrow" || it == "kal" } || n.contains("कल")) return today.plusDays(1)
        val day = toks.firstNotNullOfOrNull { t ->
            t.takeIf { it.length in 1..2 && it.all(Char::isDigit) }?.toInt() ?: numWords[t]
        } ?: return null
        if (day !in 1..31) return null
        val mi = monthKeys.indexOfFirst { ks -> ks.any { n.contains(it) } }
        return try {
            var d = if (mi >= 0) LocalDate.of(today.year, mi + 1, day) else LocalDate.of(today.year, today.month, day)
            if (d.isBefore(today)) d = if (mi >= 0) d.plusYears(1) else d.plusMonths(1)
            d
        } catch (e: Exception) {
            null
        }
    }

    fun time(raw: String): ParsedTime? {
        var n = norm(raw).replace(":", " ")
        n = n.replace(Regex("\\bp m\\b"), "pm").replace(Regex("\\ba m\\b"), "am")
        val toks = words(n)
        val nums = ArrayList<Int>()
        var mode = 0 // 1 = +30, 2 = +15, 3 = -15
        if (n.contains("quarter to")) mode = 3
        for (t in toks) {
            when {
                t == "saade" || t == "sade" || t == "साढ़े" || t == "साढे" || t == "साडे" || t == "half" -> mode = 1
                t == "sawa" || t == "सवा" || (t == "quarter" && mode != 3) -> mode = 2
                t == "paune" || t == "पौने" -> mode = 3
                t == "dedh" || t == "डेढ़" || t == "डेढ" -> {
                    nums.add(1)
                    mode = 1
                }
                t == "dhai" || t == "ढाई" -> {
                    nums.add(2)
                    mode = 1
                }
                t.length <= 4 && t.all { it.isDigit() } -> {
                    if (t.length >= 3) {
                        nums.add(t.dropLast(2).toInt())
                        nums.add(t.takeLast(2).toInt())
                    } else {
                        nums.add(t.toInt())
                    }
                }
                else -> numWords[t]?.let { nums.add(it) }
            }
        }
        if (nums.isEmpty()) return null
        var h = nums[0]
        var m = 0
        when (mode) {
            1 -> m = 30
            2 -> m = 15
            3 -> {
                m = 45
                h -= 1
                if (h <= 0) h = 12
            }
            else -> if (nums.size >= 2 && nums[1] < 60) {
                m = nums[1]
                if (nums.size >= 3 && m >= 20 && m % 10 == 0 && nums[2] in 1..9) m += nums[2]
            }
        }
        if (h > 24 || h < 0) return null
        val am = toks.any { it == "am" || it == "morning" || it == "subah" || it == "subha" } ||
            sub(n, "सुबह", "सवेरे")
        val pm = toks.any {
            it in setOf("pm", "evening", "night", "afternoon", "sham", "shaam", "raat", "dopahar")
        } || sub(n, "शाम", "रात", "दोपहर")
        return when {
            h >= 13 -> ParsedTime(h % 24, m, 3)
            h == 0 -> ParsedTime(0, m, 3)
            am -> ParsedTime(h, m, 1)
            pm -> ParsedTime(h, m, 2)
            else -> ParsedTime(h, m, 0)
        }
    }

    fun period(raw: String): Int {
        val n = norm(raw)
        val toks = words(n)
        val am = toks.any { it == "am" || it == "morning" || it == "subah" || it == "subha" } || sub(n, "सुबह", "सवेरे")
        val pm = toks.any { it in setOf("pm", "evening", "night", "afternoon", "sham", "shaam", "raat", "dopahar") } ||
            sub(n, "शाम", "रात", "दोपहर")
        return when {
            am -> 1
            pm -> 2
            else -> 0
        }
    }

    fun to24(h: Int, period: Int): Int = when (period) {
        3 -> h
        1 -> if (h == 12) 0 else h
        2 -> if (h == 12) 12 else h + 12
        else -> h
    }
}

// Wake word "System" + voice dialog. Listens only while the app is open.
class VoiceController(private val ctx: Context, private val draft: QuestDraft) {
    var onEvent: (VoiceEvent) -> Unit = {}
    var enabled by mutableStateOf(false)
    var listening by mutableStateOf(false)

    private enum class Mode { WAKE, COMMAND, DIALOG }
    private enum class Step { NAME, TYPE, REPEAT, DAYS, DATE, START, START_AP, END, CONFIRM }

    private val handler = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var useOnDevice = true
    private var mode = Mode.WAKE
    private var step = Step.NAME
    private var fails = 0
    private var pendH = 0
    private var pendM = 0
    private var uid = 0
    private val after = HashMap<String, () -> Unit>()

    fun start() {
        if (enabled) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        enabled = true
        mode = Mode.WAKE
        ensureTts()
        listen()
    }

    fun stop() {
        enabled = false
        listening = false
        handler.removeCallbacksAndMessages(null)
        resetRecognizer()
        runCatching { tts?.stop() }
        speaking = false
        after.clear()
    }

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    // Short voice reply without listening afterwards (used when toggling voice mode).
    fun say(text: String) {
        ensureTts()
        speak(text) { if (enabled) listen() }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            handler.post {
                val t = tts
                if (status == TextToSpeech.SUCCESS && t != null) {
                    t.language = Locale("hi", "IN")
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            handler.post { finishSpeak(utteranceId) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            handler.post { finishSpeak(utteranceId) }
                        }
                    })
                    ttsReady = true
                }
            }
        }
    }

    private fun speak(text: String, tries: Int = 0, then: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) {
            if (tries < 6) {
                handler.postDelayed({ speak(text, tries + 1, then) }, 400)
            } else {
                then()
            }
            return
        }
        runCatching { sr?.cancel() }
        listening = false
        speaking = true
        val id = "u" + (++uid)
        after[id] = then
        val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (r != TextToSpeech.SUCCESS) {
            after.remove(id)
            speaking = false
            then()
        }
    }

    private fun finishSpeak(id: String?) {
        speaking = false
        val f = after.remove(id ?: return)
        f?.invoke()
    }

    private fun createRecognizer(): SpeechRecognizer {
        val r = if (Build.VERSION.SDK_INT >= 31 && useOnDevice &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        r.setRecognitionListener(listener)
        return r
    }

    private fun resetRecognizer() {
        runCatching {
            sr?.cancel()
            sr?.destroy()
        }
        sr = null
    }

    private fun listen() {
        if (!enabled || speaking) return
        handler.post {
            if (!enabled || speaking) return@post
            val r = sr ?: createRecognizer().also { sr = it }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            listening = true
            runCatching { r.startListening(i) }.onFailure {
                listening = false
                resetRecognizer()
                retryLater(1500)
            }
        }
    }

    private fun retryLater(ms: Long) {
        handler.postDelayed({ listen() }, ms)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> noSpeech()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    resetRecognizer()
                    retryLater(700)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stop()
                12, 13 -> { // language not supported / unavailable on-device
                    useOnDevice = false
                    resetRecognizer()
                    retryLater(300)
                }
                else -> {
                    resetRecognizer()
                    retryLater(2000)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            if (list.isEmpty()) noSpeech() else handleResults(list)
        }
    }

    private fun noSpeech() {
        when (mode) {
            Mode.DIALOG -> fail()
            Mode.COMMAND -> {
                mode = Mode.WAKE
                retryLater(150)
            }
            Mode.WAKE -> retryLater(150)
        }
    }

    private fun handleResults(list: List<String>) {
        when (mode) {
            Mode.WAKE -> {
                val hit = list.firstOrNull { VoiceParse.hasWake(it) }
                if (hit == null) {
                    retryLater(100)
                    return
                }
                val cmd = list.map { VoiceParse.command(it) }.firstOrNull { it != Cmd.UNKNOWN } ?: Cmd.UNKNOWN
                if (cmd != Cmd.UNKNOWN) {
                    run(cmd)
                } else {
                    mode = Mode.COMMAND
                    speak("हाँ, बोलो।") { listen() }
                }
            }
            Mode.COMMAND -> {
                val cmd = list.map { VoiceParse.command(it) }.firstOrNull { it != Cmd.UNKNOWN } ?: Cmd.UNKNOWN
                if (cmd != Cmd.UNKNOWN) {
                    run(cmd)
                } else {
                    mode = Mode.WAKE
                    speak("समझ नहीं आया।") { listen() }
                }
            }
            Mode.DIALOG -> handleDialog(list)
        }
    }

    private fun run(cmd: Cmd) {
        when (cmd) {
            Cmd.NEW_QUEST -> {
                draft.reset()
                onEvent(VoiceEvent.OPEN_NEW)
                mode = Mode.DIALOG
                step = Step.NAME
                fails = 0
                ask()
            }
            Cmd.STATUS -> {
                onEvent(VoiceEvent.OPEN_STATUS)
                mode = Mode.WAKE
                val p = PlayerStore.read(ctx)
                speak("आपका लेवल ${p.level} है और रैंक ${p.rank}।") { listen() }
            }
            Cmd.PROFILE -> {
                onEvent(VoiceEvent.OPEN_PROFILE)
                mode = Mode.WAKE
                speak("यह रहा आपका प्रोफाइल।") { listen() }
            }
            Cmd.CANCEL -> {
                mode = Mode.WAKE
                speak("ठीक है।") { listen() }
            }
            Cmd.UNKNOWN -> {
                mode = Mode.WAKE
                listen()
            }
        }
    }

    private fun ask() {
        val text = when (step) {
            Step.NAME -> "नया क्वेस्ट। क्वेस्ट का नाम बताओ।"
            Step.TYPE -> "टाइप बताओ। स्ट्रेंथ, इंटेलिजेंस, या वाइटैलिटी?"
            Step.REPEAT -> "रोज़ का है, कुछ दिनों का, या किसी एक तारीख का?"
            Step.DAYS -> "कौन से दिन? जैसे सोमवार, बुधवार, शुक्रवार।"
            Step.DATE -> "कौन सी तारीख? जैसे पंद्रह नवंबर, या कल।"
            Step.START -> "शुरू होने का समय बताओ।"
            Step.START_AP -> "सुबह या शाम?"
            Step.END -> "डेडलाइन का समय बताओ।"
            Step.CONFIRM ->
                "क्वेस्ट ${draft.title}। शुरू ${fmt12(draft.sh, draft.sm)}, डेडलाइन ${fmt12(draft.eh, draft.em)}। रजिस्टर करूँ?"
        }
        speak(text) { listen() }
    }

    private fun next(s: Step) {
        step = s
        fails = 0
        ask()
    }

    private fun fail() {
        fails++
        if (fails >= 2) {
            mode = Mode.WAKE
            speak("ठीक है, बाकी आप खुद भर लो।") { listen() }
        } else {
            speak("समझ नहीं आया।") { ask() }
        }
    }

    private fun handleDialog(list: List<String>) {
        val cancelHit = list.any { VoiceParse.command(it) == Cmd.CANCEL }
        if (cancelHit && (step != Step.NAME || VoiceParse.norm(list[0]).split(" ").size <= 2)) {
            onEvent(VoiceEvent.CANCEL_FORM)
            mode = Mode.WAKE
            speak("ठीक है, रद्द कर दिया।") { listen() }
            return
        }
        when (step) {
            Step.NAME -> {
                val name = list[0].trim().take(60)
                if (name.isBlank()) return fail()
                draft.title = name.replaceFirstChar { it.uppercase() }
                next(Step.TYPE)
            }
            Step.TYPE -> {
                val s = list.firstNotNullOfOrNull { VoiceParse.stat(it) } ?: return fail()
                draft.stat = s
                next(Step.REPEAT)
            }
            Step.REPEAT -> {
                val r = list.firstNotNullOfOrNull { VoiceParse.repeatType(it) } ?: return fail()
                draft.rtype = r
                next(
                    when (r) {
                        "DAYS" -> Step.DAYS
                        "DATE" -> Step.DATE
                        else -> Step.START
                    }
                )
            }
            Step.DAYS -> {
                val m = list.map { VoiceParse.mask(it) }.firstOrNull { it != 0 } ?: return fail()
                draft.mask = m
                next(Step.START)
            }
            Step.DATE -> {
                val d = list.firstNotNullOfOrNull { VoiceParse.date(it, LocalDate.now()) } ?: return fail()
                draft.date = d
                next(Step.START)
            }
            Step.START -> {
                val t = list.firstNotNullOfOrNull { VoiceParse.time(it) } ?: return fail()
                if (t.period == 0) {
                    pendH = t.h
                    pendM = t.m
                    next(Step.START_AP)
                } else {
                    draft.sh = VoiceParse.to24(t.h, t.period)
                    draft.sm = t.m
                    next(Step.END)
                }
            }
            Step.START_AP -> {
                val p = list.map { VoiceParse.period(it) }.firstOrNull { it != 0 } ?: return fail()
                draft.sh = VoiceParse.to24(pendH, p)
                draft.sm = pendM
                next(Step.END)
            }
            Step.END -> {
                val t = list.firstNotNullOfOrNull { VoiceParse.time(it) } ?: return fail()
                val start = draft.sh * 60 + draft.sm
                val hours = if (t.period == 0) {
                    listOf(VoiceParse.to24(t.h, 1), VoiceParse.to24(t.h, 2))
                } else {
                    listOf(VoiceParse.to24(t.h, t.period))
                }
                val pick = hours.map { it * 60 + t.m }.filter { it > start }.minOrNull()
                if (pick == null) {
                    speak("डेडलाइन शुरू के बाद होनी चाहिए।") { ask() }
                } else {
                    draft.eh = pick / 60
                    draft.em = pick % 60
                    next(Step.CONFIRM)
                }
            }
            Step.CONFIRM -> {
                when (list.firstNotNullOfOrNull { VoiceParse.yesNo(it) }) {
                    true -> {
                        onEvent(VoiceEvent.REGISTER)
                        mode = Mode.WAKE
                        speak("क्वेस्ट रजिस्टर कर दिया।") { listen() }
                    }
                    false -> {
                        mode = Mode.WAKE
                        speak("ठीक है। आप फॉर्म में बदल सकते हो।") { listen() }
                    }
                    null -> fail()
                }
            }
        }
    }
}

@Composable
fun MicButton(on: Boolean, listening: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "a"
    )
    val tone = if (on) SysBlue else Color(0xFF5E7FA8)
    val a = if (on && listening) pulse else 1f
    Box(
        Modifier.size(60.dp).border(2.dp, tone).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(a)) {
            Box(Modifier.size(12.dp, 20.dp).background(tone, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.size(20.dp, 8.dp)
                    .border(2.dp, tone, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            )
            Box(Modifier.size(2.dp, 4.dp).background(tone))
        }
    }
}
