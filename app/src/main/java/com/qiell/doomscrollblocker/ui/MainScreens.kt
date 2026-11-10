package com.qiell.doomscrollblocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qiell.doomscrollblocker.engine.AndroidClock
import com.qiell.doomscrollblocker.engine.RulesEngine
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

enum class MainScreen { HOME, PICKER, SETTINGS }

/** Akar UI utama: berpindah antar layar dengan state sederhana (tanpa library navigasi). */
@Composable
fun MainRoot(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf(MainScreen.HOME) }
    when (screen) {
        MainScreen.HOME -> HomeScreen(
            viewModel = viewModel,
            onOpenPicker = { screen = MainScreen.PICKER },
            onOpenSettings = { screen = MainScreen.SETTINGS }
        )
        MainScreen.PICKER -> AppPickerScreen(
            viewModel = viewModel,
            onBack = { screen = MainScreen.HOME }
        )
        MainScreen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = MainScreen.HOME }
        )
    }
}

// ----------------------------------------------------------------------- Home

@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenPicker: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.engineState.collectAsState()

    // Status dihitung ulang tiap detik lewat engine, supaya chip dan progress
    // selalu mengikuti jam perangkat (termasuk setelah reboot).
    var remainingBlockMs by remember { mutableStateOf(0L) }
    var usedMs by remember { mutableStateOf(0L) }
    var graceActive by remember { mutableStateOf(false) }
    LaunchedEffect(settings, state) {
        while (true) {
            val engine = RulesEngine(
                AndroidClock(context),
                state,
                settings.copy(whitelistedPackages = viewModel.whitelistedPackages)
            )
            remainingBlockMs = engine.blockedRemainingMs()
            usedMs = engine.currentUsedMs()
            graceActive = engine.isGraceActive()
            delay(1_000L)
        }
    }

    val accessibilityEnabled = rememberAccessibilityEnabled(context)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Doomscroll Blocker", style = MaterialTheme.typography.headlineMedium)

        if (!accessibilityEnabled) {
            DisclosureCard(context)
        } else if (!isIgnoringBatteryOptimizations(context)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pengecualian baterai belum aktif", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Beberapa merek ponsel mematikan layanan di latar belakang. " +
                            "Izinkan aplikasi ini dikecualikan dari penghemat baterai supaya blokir tetap jalan."
                    )
                    Button(onClick = { requestBatteryExemption(context) }) {
                        Text("Minta pengecualian baterai")
                    }
                }
            }
        }

        // Chip status + progress pemakaian.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    graceActive -> Text(
                        "Status: KELONGGARAN BREAK-GLASS aktif",
                        style = MaterialTheme.typography.titleMedium
                    )
                    remainingBlockMs > 0L -> Text(
                        "Status: TERBLOKIR — sisa ${formatDuration(remainingBlockMs)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    else -> Text("Status: DIIZINKAN", style = MaterialTheme.typography.titleMedium)
                }
                val fraction = if (settings.allowanceMs > 0) {
                    (usedMs.toFloat() / settings.allowanceMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Terpakai ${formatDuration(usedMs)} dari jatah ${formatDuration(settings.allowanceMs)} " +
                        "• Cooldown ${formatDuration(settings.cooldownMs)} " +
                        "• ${settings.blockedPackages.size} aplikasi dibatasi"
                )
            }
        }

        Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
            Text("Pilih aplikasi yang dibatasi")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Pengaturan waktu")
        }

        // Ringkasan break-glass (statistik minimal v1).
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Break-glass", style = MaterialTheme.typography.titleMedium)
                Text("Sudah dipakai ${state.breakGlassLog.size} kali.")
                state.breakGlassLog.takeLast(5).reversed().forEach { epoch ->
                    Text("• " + DateFormat.getDateTimeInstance().format(Date(epoch)))
                }
            }
        }
    }
}

/**
 * Pengungkapan menonjol WAJIB sebelum mengarahkan pengguna ke pengaturan
 * aksesibilitas (persyaratan Play Store, dan memang hal yang jujur untuk dilakukan):
 * menjelaskan persis data apa yang dilihat aplikasi dan apa yang tidak.
 */
@Composable
private fun DisclosureCard(context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Izin aksesibilitas diperlukan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Doomscroll Blocker mendeteksi aplikasi mana yang sedang dibuka untuk " +
                    "menegakkan batas waktu yang kamu atur sendiri. Tidak ada isi layar, " +
                    "ketikan, pesan, atau kata sandi yang dibaca atau disimpan — aplikasi " +
                    "hanya menerima nama paket aplikasi di depan dan tidak meminta izin " +
                    "membaca isi jendela sama sekali."
            )
            Text(
                "Tanpa izin ini aplikasi tidak bisa bekerja. Kamu bisa mencabutnya kapan " +
                    "saja dari Pengaturan Android, dan mencopot aplikasi selalu bisa dilakukan."
            )
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("Buka pengaturan aksesibilitas")
            }
        }
    }
}

@Composable
private fun rememberAccessibilityEnabled(context: Context): Boolean {
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val services = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabled = services.contains(context.packageName)
            delay(2_000L)
        }
    }
    return enabled
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

// --------------------------------------------------------------------- Picker

@Composable
fun AppPickerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    val apps = remember { viewModel.loadInstalledApps() }
    val visible = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pilih aplikasi yang dibatasi", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Semua aplikasi di sini berbagi SATU jatah waktu yang sama. " +
                "Aplikasi telepon, pesan, dan pengaturan tidak ditampilkan karena tidak pernah bisa diblokir."
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cari aplikasi") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = app.packageName in settings.blockedPackages,
                        onCheckedChange = { checked ->
                            viewModel.setPackageBlocked(app.packageName, checked)
                        }
                    )
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Kembali") }
    }
}

// ------------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var customAllowance by remember { mutableStateOf("") }
    var customCooldown by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pengaturan waktu", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Perubahan berlaku mulai siklus berikutnya. Cooldown yang sedang berjalan " +
                "tidak akan dipendekkan dan tidak akan dipanjangkan oleh perubahan di sini."
        )

        Text("Jatah waktu (allowance)", style = MaterialTheme.typography.titleMedium)
        PresetChips(
            optionsMinutes = listOf(1, 5, 10, 15, 30),
            selectedMs = settings.allowanceMs,
            onSelect = { viewModel.setAllowanceMs(it) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customAllowance,
                onValueChange = { customAllowance = it.filter { c -> c.isDigit() } },
                label = { Text("Menit khusus") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                customAllowance.toLongOrNull()?.let { viewModel.setAllowanceMs(it * 60_000L) }
            }) { Text("Terapkan") }
        }

        Text("Cooldown / waktu reset", style = MaterialTheme.typography.titleMedium)
        PresetChips(
            optionsMinutes = listOf(15, 30, 60, 120),
            selectedMs = settings.cooldownMs,
            onSelect = { viewModel.setCooldownMs(it) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customCooldown,
                onValueChange = { customCooldown = it.filter { c -> c.isDigit() } },
                label = { Text("Menit khusus") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                customCooldown.toLongOrNull()?.let { viewModel.setCooldownMs(it * 60_000L) }
            }) { Text("Terapkan") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Kembali") }
    }
}

@Composable
private fun PresetChips(optionsMinutes: List<Int>, selectedMs: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        optionsMinutes.forEach { minutes ->
            val ms = minutes * 60_000L
            FilterChip(
                selected = selectedMs == ms,
                onClick = { onSelect(ms) },
                label = { Text("$minutes mnt") }
            )
        }
    }
}
