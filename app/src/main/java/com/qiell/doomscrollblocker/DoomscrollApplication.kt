package com.qiell.doomscrollblocker

import android.app.Application
import com.qiell.doomscrollblocker.data.DataStoreRepository

/** Wadah dependensi manual (tanpa DI framework, sesuai spesifikasi). */
class DoomscrollApplication : Application() {
    lateinit var repository: DataStoreRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DataStoreRepository(this)
    }
}
