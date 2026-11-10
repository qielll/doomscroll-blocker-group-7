package com.qiell.doomscrollblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.qiell.doomscrollblocker.ui.MainRoot
import com.qiell.doomscrollblocker.ui.MainViewModel
import com.qiell.doomscrollblocker.ui.theme.DoomscrollTheme

/** Activity utama: status, pemilih aplikasi, dan pengaturan. */
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoomscrollTheme {
                MainRoot(viewModel)
            }
        }
    }
}
