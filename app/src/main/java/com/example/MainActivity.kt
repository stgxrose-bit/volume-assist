import androidx.lifecycle.compose.LocalLifecycleOwner
package com.example

import com.example.R

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VolumeControlDashboard()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeControlDashboard() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val lifecycleOwner = (androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity)

    var isServiceActive by remember { mutableStateOf(checkAccessibilityServiceEnabled(context)) }

    // Stream volume states
    var callVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL).toFloat()) }
    var mediaVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var ringVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat()) }
    var alarmVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()) }

    val callMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) }
    val mediaMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val ringMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) }
    val alarmMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }

    val isCallActive = remember {
        audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_RINGTONE
    }

    // Refresh state when activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceActive = checkAccessibilityServiceEnabled(context)
                callVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL).toFloat()
                mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat()
                alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0284C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Broken Volume Button Replacement",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isServiceActive = checkAccessibilityServiceEnabled(context)
                            Toast.makeText(context, "Status refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Accessibility Service Status Banner Card
            ServiceStatusCard(
                isServiceActive = isServiceActive,
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                onTestPanel = {
                    if (VolumeAccessibilityService.isServiceRunning()) {
                        VolumeAccessibilityService.triggerPanel()
                    } else {
                        Toast.makeText(
                            context,
                            "Please enable the Accessibility Service first to display overlay!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            // 2. Hardware Profile (OPPO A9 2020 / Snapdragon 665 / API 30)
            DeviceProfileCard()

            // 3. Live Volume Stream Controller Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("volume_streams_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Stream Controllers",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        if (isCallActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0369A1))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "In-Call Priority",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7DD3FC),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Voice Call Stream
                    StreamSliderItem(
                        title = stringResource(R.string.stream_call),
                        icon = Icons.Default.Call,
                        accentColor = Color(0xFF34D399),
                        currentVal = callVolume,
                        maxVal = callMax.toFloat(),
                        isPrioritized = isCallActive,
                        onValueChange = { newVal ->
                            callVolume = newVal
                            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newVal.toInt(), 0)
                        },
                        onMuteToggle = {
                            val target = if (callVolume > 0) 0f else (callMax / 2).toFloat()
                            callVolume = target
                            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target.toInt(), 0)
                        }
                    )

                    // Media Stream
                    StreamSliderItem(
                        title = stringResource(R.string.stream_media),
                        icon = Icons.Default.MusicNote,
                        accentColor = Color(0xFF38BDF8),
                        currentVal = mediaVolume,
                        maxVal = mediaMax.toFloat(),
                        isPrioritized = !isCallActive,
                        onValueChange = { newVal ->
                            mediaVolume = newVal
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVal.toInt(), 0)
                        },
                        onMuteToggle = {
                            val target = if (mediaVolume > 0) 0f else (mediaMax / 2).toFloat()
                            mediaVolume = target
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target.toInt(), 0)
                        }
                    )

                    // Ring / Notification Stream
                    StreamSliderItem(
                        title = stringResource(R.string.stream_ring),
                        icon = Icons.Default.Notifications,
                        accentColor = Color(0xFFA78BFA),
                        currentVal = ringVolume,
                        maxVal = ringMax.toFloat(),
                        isPrioritized = false,
                        onValueChange = { newVal ->
                            ringVolume = newVal
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, newVal.toInt(), 0)
                        },
                        onMuteToggle = {
                            val target = if (ringVolume > 0) 0f else (ringMax / 2).toFloat()
                            ringVolume = target
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, target.toInt(), 0)
                        }
                    )

                    // Alarm Stream
                    StreamSliderItem(
                        title = stringResource(R.string.stream_alarm),
                        icon = Icons.Default.Alarm,
                        accentColor = Color(0xFFFBBF24),
                        currentVal = alarmVolume,
                        maxVal = alarmMax.toFloat(),
                        isPrioritized = false,
                        onValueChange = { newVal ->
                            alarmVolume = newVal
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newVal.toInt(), 0)
                        },
                        onMuteToggle = {
                            val target = if (alarmVolume > 0) 0f else (alarmMax / 2).toFloat()
                            alarmVolume = target
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target.toInt(), 0)
                        }
                    )
                }
            }

            // 4. Quick Action Shortcuts Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shortcuts_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Actions (Replace Hardware Keys)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (VolumeAccessibilityService.isServiceRunning()) {
                                    VolumeAccessibilityService.triggerPanel()
                                } else {
                                    Toast.makeText(context, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_trigger_volume"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Open Panel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (VolumeAccessibilityService.isServiceRunning()) {
                                    // Trigger native screenshot via service
                                    Toast.makeText(context, "Taking screenshot in 1s...", Toast.LENGTH_SHORT).show()
                                    VolumeAccessibilityService.triggerPanel()
                                } else {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_screenshot_action"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Screenshot", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. OPPO ColorOS / Android 11 Setup Guide
            SetupGuideCard(onOpenSettings = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ServiceStatusCard(
    isServiceActive: Boolean,
    onOpenSettings: () -> Unit,
    onTestPanel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceActive) Color(0xFF064E3B) else Color(0xFF451A03)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isServiceActive) Color(0xFF10B981) else Color(0xFFF59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isServiceActive) {
                            stringResource(R.string.service_active_status)
                        } else {
                            stringResource(R.string.service_inactive_status)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (isServiceActive) {
                            stringResource(R.string.service_active_desc)
                        } else {
                            stringResource(R.string.service_inactive_desc)
                        },
                        fontSize = 12.sp,
                        color = if (isServiceActive) Color(0xFFA7F3D0) else Color(0xFFFED7AA)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_accessibility_settings"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceActive) Color(0xFF059669) else Color(0xFFD97706)
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_enable_service),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isServiceActive) {
                    OutlinedButton(
                        onClick = onTestPanel,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(44.dp)
                            .testTag("btn_test_panel"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6EE7B7))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_test_panel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6EE7B7)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamSliderItem(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    currentVal: Float,
    maxVal: Float,
    isPrioritized: Boolean,
    onValueChange: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrioritized) Color(0xFF1E3A5F) else Color(0xFF283548)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${currentVal.toInt()}/${maxVal.toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onMuteToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (currentVal <= 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = if (currentVal <= 0) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Slider(
                value = currentVal.coerceIn(0f, maxVal),
                onValueChange = onValueChange,
                valueRange = 0f..maxVal,
                steps = (maxVal - 1).toInt().coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color(0xFF475569)
                ),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
fun DeviceProfileCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_profile_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.device_specs_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.device_specs_desc),
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun SetupGuideCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_guide_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.setup_guide_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuideStepItem(text = stringResource(R.string.setup_step_1))
                GuideStepItem(text = stringResource(R.string.setup_step_2))
                GuideStepItem(text = stringResource(R.string.setup_step_3))
                GuideStepItem(text = stringResource(R.string.setup_step_4))
            }
        }
    }
}

@Composable
fun GuideStepItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            lineHeight = 16.sp
        )
    }
}

/**
 * Checks if the custom Accessibility Service is enabled in system settings.
 */
fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    if (VolumeAccessibilityService.isServiceRunning()) return true

    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val packageName = context.packageName

    return enabledServices.any { serviceInfo ->
        serviceInfo.resolveInfo.serviceInfo.packageName == packageName
    }
}
