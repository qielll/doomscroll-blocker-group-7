package com.qiell.doomscrollblocker.whitelist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * Menyusun daftar aplikasi yang TIDAK PERNAH boleh diblokir.
 *
 * Keputusan terkonfirmasi (2026-11-10): resolusi DINAMIS, bukan hanya hardcoded —
 * dialer dan aplikasi SMS bawaan berbeda di tiap OEM, jadi keduanya dibaca dari
 * perangkat saat runtime, ditambah paket Settings sistem dan aplikasi ini sendiri.
 * Nama paket hardcoded di bawah hanya cadangan apabila resolusi dinamis kosong.
 */
object WhitelistResolver {

    private val fallbackPackages = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.messaging",
        "com.android.contacts",
        "com.android.emergency",
        "com.android.settings",
    )

    fun resolve(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        result += fallbackPackages
        result += context.packageName // aplikasi ini sendiri tidak pernah diblokir

        // Dialer bawaan perangkat.
        runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom?.defaultDialerPackage?.let { result += it }
        }
        runCatching {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:123"))
            context.packageManager.resolveActivity(dialIntent, 0)
                ?.activityInfo?.packageName?.let { result += it }
        }

        // Aplikasi SMS bawaan perangkat.
        runCatching {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123"))
            context.packageManager.resolveActivity(smsIntent, 0)
                ?.activityInfo?.packageName?.let { result += it }
        }

        // Settings sistem.
        runCatching {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            context.packageManager.resolveActivity(settingsIntent, 0)
                ?.activityInfo?.packageName?.let { result += it }
        }
        result += "com.android.settings"

        return result
    }
}
