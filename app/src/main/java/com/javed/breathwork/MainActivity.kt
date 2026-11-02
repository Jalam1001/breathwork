package com.javed.breathwork

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.javed.breathwork.ui.App
import com.javed.breathwork.ui.BreathTheme

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BreathTheme { App(container, onBeforeStart = ::askNotifications) } }
    }

    /** Notifications only show session controls; sessions work without them. Asked once, on first start. */
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && container.serviceEnabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
