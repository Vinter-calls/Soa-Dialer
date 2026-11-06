package com.dialer.truedialer.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dialer.truedialer.ui.components.PostCallDialog
import com.dialer.truedialer.ui.theme.TrueEnquiryDialerTheme

class PostCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_DURATION = "extra_call_duration"
        const val EXTRA_CALL_TYPE = "extra_call_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val duration = intent.getLongExtra(EXTRA_CALL_DURATION, 0L)
        val type = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "INCOMING"

        if (phone.isBlank()) {
            finish()
            return
        }

        setContent {
            TrueEnquiryDialerTheme {
                PostCallDialog(
                    phoneNumber = phone,
                    callDurationSeconds = duration,
                    callType = type,
                    onDismiss = { finish() }
                )
            }
        }
    }
}
