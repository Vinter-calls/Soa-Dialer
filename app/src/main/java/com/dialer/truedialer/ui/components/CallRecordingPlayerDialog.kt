package com.dialer.truedialer.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.CallRecordingUtils
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CallRecordingPlayerDialog(
    displayName: String,
    recordingFile: File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(0) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    fun setupLoudAudio(speaker: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = speaker
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol < (maxVol * 0.7f).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt(), 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(recordingFile) {
        if (recordingFile != null && recordingFile.exists()) {
            try {
                setupLoudAudio(isSpeakerOn)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val mp = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(recordingFile.absolutePath)
                    setVolume(1.0f, 1.0f)
                    prepare()
                }
                totalDurationMs = mp.duration.coerceAtLeast(0)
                mediaPlayer = mp
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading recording: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                try {
                    currentPositionMs = mp.currentPosition
                } catch (_: Exception) {}
            }
            delay(300L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Play Call Recording", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(displayName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Speaker toggle chip
                FilterChip(
                    selected = isSpeakerOn,
                    onClick = {
                        val newSpeaker = !isSpeakerOn
                        isSpeakerOn = newSpeaker
                        setupLoudAudio(newSpeaker)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.Hearing,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSpeakerOn) EmeraldGreen else Color.Gray
                        )
                    },
                    label = { Text(if (isSpeakerOn) "Speaker" else "Earpiece", fontSize = 11.sp) }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (recordingFile == null || !recordingFile.exists()) {
                    Text(
                        text = "No recorded audio file found for this session yet. Make a call and tap 'Record' during the call to record audio.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = recordingFile.name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = "${(recordingFile.length() / 1024)} KB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TrueBlue
                            )
                        }
                    }

                    // Audio Track Duration Progress Measurements
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f,
                            onValueChange = { fraction ->
                                mediaPlayer?.let { mp ->
                                    val target = (fraction * totalDurationMs).toInt()
                                    mp.seekTo(target)
                                    currentPositionMs = target
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val elapsedSec = (currentPositionMs / 1000).toLong()
                            val totalSec = (totalDurationMs / 1000).toLong()
                            Text(
                                text = CallRecordingUtils.formatStopwatch(elapsedSec),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CallRecordingUtils.formatStopwatch(totalSec),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                val mp = mediaPlayer ?: return@FilledIconButton
                                try {
                                    if (isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        setupLoudAudio(isSpeakerOn)
                                        mp.start()
                                        isPlaying = true
                                        mp.setOnCompletionListener {
                                            isPlaying = false
                                            currentPositionMs = 0
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TrueBlue),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isPlaying) "Playing recorded call via speaker..." else "Tap play to listen loudly",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldGreen
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (_: Exception) {}
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}
