package com.dialer.truedialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue

@Composable
fun EnquiryBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        "New Lead" -> Pair(Color(0xFFDBEAFE), TrueBlue)
        "Interested" -> Pair(Color(0xFFE0F2FE), Color(0xFF0284C7))
        "Follow-up" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        "Enrolled", "Converted" -> Pair(Color(0xFFDCFCE7), EmeraldGreen)
        "Not Interested", "Lost" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
        "VIP" -> Pair(Color(0xFFF3E8FF), Color(0xFF7E22CE))
        "Spam" -> Pair(Color(0xFFFEE2E2), CoralRed)
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
