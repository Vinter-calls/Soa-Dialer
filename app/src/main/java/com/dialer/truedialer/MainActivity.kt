package com.dialer.truedialer

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.dialer.truedialer.ui.screens.MainScreen
import com.dialer.truedialer.ui.theme.TrueEnquiryDialerTheme
import com.dialer.truedialer.util.PermissionUtils

class MainActivity : FragmentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions evaluated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request core permissions if not yet granted
        if (!PermissionUtils.hasAllRequiredPermissions(this)) {
            requestPermissionsLauncher.launch(PermissionUtils.REQUIRED_PERMISSIONS)
        }

        setContent {
            TrueEnquiryDialerTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
