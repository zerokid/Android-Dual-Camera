package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.HardwareDualCameraInfo
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant

@Composable
fun SpecsDialog(
    hardwareInfo: HardwareDualCameraInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("specs_dialog"),
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dual Camera Hardware",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hardwareInfo.isConcurrentSupported) Color(0x2200E5FF) else Color(0x22FFB703),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (hardwareInfo.isConcurrentSupported) CyberCyan.copy(alpha = 0.6f) else AccentAmber.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (hardwareInfo.isConcurrentSupported) CyberCyan else AccentAmber,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hardwareInfo.isConcurrentSupported) "Hardware Dual-ISP Active" else "Director Split-Screen Active",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (hardwareInfo.isConcurrentSupported) {
                                    "Your device chipset supports simultaneous concurrent camera streaming for front and back sensors."
                                } else {
                                    "Chipset/emulator has single ISP pipeline. DuoCam provides simultaneous split-screen view with active 60fps recording and 1-tap lens switching."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass)

                // Detailed Specs table
                Text(
                    text = "HARDWARE TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                SpecRow(label = "Back Camera ID", value = hardwareInfo.backCameraId ?: "Camera 0")
                SpecRow(label = "Front Camera ID", value = hardwareInfo.frontCameraId ?: "Camera 1")
                SpecRow(label = "Total Physical Sensors", value = "${hardwareInfo.cameraCount.coerceAtLeast(2)} Cameras")
                SpecRow(label = "Hardware Profile", value = hardwareInfo.hardwareLevel)
                SpecRow(label = "Recording Pipeline", value = "CameraX VideoCapture FHD")
                SpecRow(label = "Audio Sampling", value = "48kHz Stereo AAC")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
