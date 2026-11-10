package com.qiell.doomscrollblocker.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qiell.doomscrollblocker.DoomscrollApplication
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.RulesEngine
import com.qiell.doomscrollblocker.ui.theme.DoomscrollTheme
import com.qiell.doomscrollblocker.whitelist.WhitelistResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar blokir v1 (Activity, bukan overlay — keputusan 2026-11-10).
 *
 * Sengaja tanpa tombol keluar dan tanpa jalan pintas yang terlihat:
 * - Back ditekan -> tidak terjadi apa-apa (BackHandler kosong).
 * - Satu-satunya jalan keluar adalah break-glass yang tersembunyi:
 *   tahan hitung mundur selama 5 detik -> tunggu wajib 60 detik ->
 *   ketik frasa persis -> kelonggaran 5 menit, sekali per 24 jam,
 *   dan cooldown TIDAK bergeser.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRemaining = intent.getLongExtra(EXTRA_REMAINING_MS, 0L)
        setContent {
            DoomscrollTheme {
                BlockScreen(
                    initialRemainingMs = initialRemaining,
                    onCooldownFinished = { finish() },
                    onGraceGranted = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_REMAINING_MS = "extra_remaining_ms"
    }
}

private enum class BreakGlassPhase { LOCKED, WAITING, PHRASE }

@Composable
private fun BlockScreen(
    initialRemainingMs: Long,
    onCooldownFinished: () -> Unit,
    onGraceGranted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as DoomscrollApplication
    val scope = rememberCoroutineScope()

    var remainingMs by remember { mutableLongStateOf(initialRemainingMs) }
    var phase by remember { mutableStateOf(BreakGlassPhase.LOCKED) }
    var waitRemainingMs by remember { mutableLongStateOf(RulesEngine.BREAK_GLASS_WAIT_MS) }
    var phrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // Tombol Back tidak melakukan apa pun di layar ini.
    BackHandler { }

    // Hitung mundur diperbarui dari state tersimpan, bukan dari hitungan lokal,
    // supaya tetap benar walaupun Activity dibuat ulang.
    LaunchedEffect(Unit) {
        while (true) {
            val settings = app.repository.loadSettings().copy(
                whitelistedPackages = WhitelistResolver.resolve(context)
            )
            val engine = RulesEngine(AndroidClock(context), app.repository.loadState(), settings)
            remainingMs = engine.blockedRemainingMs()
            if (engine.isGraceActive()) {
                onGraceGranted()
                return@LaunchedEffect
            }
            if (remainingMs <= 0L) {
                onCooldownFinished()
                return@LaunchedEffect
            }
            delay(1_000L)
        }
    }

    // Masa tunggu wajib break-glass: 60 detik, tidak bisa dipercepat.
    LaunchedEffect(phase) {
        if (phase == BreakGlassPhase.WAITING) {
            waitRemainingMs = RulesEngine.BREAK_GLASS_WAIT_MS
            while (waitRemainingMs > 0L) {
                delay(1_000L)
                waitRemainingMs -= 1_000L
            }
            phase = BreakGlassPhase.PHRASE
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10222E))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Waktunya berhenti sebentar.",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Aplikasi ini masih terkunci. Tidak ada yang perlu dilakukan — " +
                "istirahat dulu, cooldown akan selesai dengan sendirinya.",
            color = Color(0xFFC7D5DE),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // Area hitung mundur SEKALIGUS pegangan break-glass tersembunyi:
        // menahannya 5 detik memulai alur break-glass (tidak ada petunjuk visual).
        HoldableCountdown(
            remainingMs = remainingMs,
            onHeldFiveSeconds = {
                if (phase == BreakGlassPhase.LOCKED) phase = BreakGlassPhase.WAITING
            }
        )

        Spacer(Modifier.height(24.dp))
        message?.let {
            Text(it, color = Color(0xFFFFC7C2), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }

        when (phase) {
            BreakGlassPhase.LOCKED -> Unit
            BreakGlassPhase.WAITING -> Text(
                text = "Break-glass: tunggu ${formatDuration(waitRemainingMs)} sebelum melanjutkan.",
                color = Color(0xFFC7D5DE),
                textAlign = TextAlign.Center
            )
            BreakGlassPhase.PHRASE -> {
                Text(
                    text = "Ketik frasa berikut persis sama untuk membuka kelonggaran 5 menit " +
                        "(sekali per 24 jam, cooldown tetap berjalan):",
                    color = Color(0xFFC7D5DE),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“${RulesEngine.BREAK_GLASS_PHRASE}”",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        val settings = app.repository.loadSettings().copy(
                            whitelistedPackages = WhitelistResolver.resolve(context)
                        )
                        val engine = RulesEngine(
                            AndroidClock(context), app.repository.loadState(), settings
                        )
                        val ok = engine.requestBreakGlass(
                            phrase, RulesEngine.BREAK_GLASS_HOLD_MS
                        )
                        app.repository.saveState(engine.state)
                        if (ok) {
                            onGraceGranted()
                        } else {
                            message = "Tidak bisa dibuka: frasa tidak cocok, atau break-glass " +
                                "sudah pernah dipakai dalam 24 jam terakhir."
                            phase = BreakGlassPhase.LOCKED
                        }
                    }
                }) {
                    Text("Buka kelonggaran 5 menit")
                }
            }
        }
    }
}

/**
 * Teks hitung mundur yang bisa ditahan. Sentuhan ditahan 5 detik penuh tanpa
 * lepas memanggil [onHeldFiveSeconds]; lepas lebih awal membatalkannya.
 * Diimplementasikan lewat MotionEvent supaya logikanya eksplisit dan teruji manual.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HoldableCountdown(remainingMs: Long, onHeldFiveSeconds: () -> Unit) {
    val handler = remember { Handler(Looper.getMainLooper()) }
    Text(
        text = "Terkunci ${formatDuration(remainingMs)} lagi",
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.pointerInteropFilter { event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed({ onHeldFiveSeconds() }, RulesEngine.BREAK_GLASS_HOLD_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    true
                }
                else -> false
            }
        }
    )
}

/** Format sisa waktu menjadi MM:SS (atau JJ:MM:SS apabila lewat satu jam). */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
