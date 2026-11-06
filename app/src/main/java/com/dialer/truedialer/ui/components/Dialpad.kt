package com.dialer.truedialer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.ui.theme.EmeraldGreen

data class DialpadKey(val digit: String, val letters: String)

@Composable
fun Dialpad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBackspaceLongClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf(DialpadKey("1", ""), DialpadKey("2", "ABC"), DialpadKey("3", "DEF")),
        listOf(DialpadKey("4", "GHI"), DialpadKey("5", "JKL"), DialpadKey("6", "MNO")),
        listOf(DialpadKey("7", "PQRS"), DialpadKey("8", "TUV"), DialpadKey("9", "WXYZ")),
        listOf(DialpadKey("*", ""), DialpadKey("0", "+"), DialpadKey("#", ""))
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    DialpadButton(
                        key = key,
                        onClick = { onDigitClick(key.digit) },
                        onLongClick = {
                            if (key.digit == "0") {
                                onDigitClick("+")
                            }
                        }
                    )
                }
            }
        }

        // Bottom action row: Empty placeholder / Call button / Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(68.dp))

            // Call Button
            FilledIconButton(
                onClick = onCallClick,
                modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = EmeraldGreen
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Backspace Button
            BackspaceButton(
                onClick = onBackspaceClick,
                onLongClick = onBackspaceLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadButton(
    key: DialpadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (key.letters.isNotBlank()) {
                Text(
                    text = key.letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BackspaceButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(26.dp)
        )
    }
}
