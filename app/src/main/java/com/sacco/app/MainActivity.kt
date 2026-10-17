package com.sacco.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sacco.app.ui.navigation.AppNav
import com.sacco.app.ui.theme.SaccoTheme

class MainActivity : ComponentActivity() {
    private val session get() = (application as SaccoApp).session
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { SaccoTheme { AppNav() } }
    }

    override fun onResume() {
        super.onResume()
        session.expireIfIdle()   // sign out after 15 minutes of inactivity
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        session.touch()
    }
}
