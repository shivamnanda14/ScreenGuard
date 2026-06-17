package com.example.screenguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import android.accessibilityservice.AccessibilityService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions on load
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        setContent {
            MainDashboardScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    // Check accessibility status when returning to app
    DisposableEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context, ScreenGuardService::class.java)
        onDispose {}
    }

    // Refresh status state on app resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context, ScreenGuardService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val themeBackground = Color(0xFF0F172A) // Sleek slate-900 background
    val cardBackground = Color(0xFF1E293B) // Slate-800
    val primaryCrimson = Color(0xFFF43F5E) // Vibrant rose-500
    val emeraldActive = Color(0xFF10B981) // Emerald green-500

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = themeBackground,
            surface = cardBackground,
            primary = primaryCrimson
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1B4B), themeBackground) // Deep indigo-950 to slate-900
                        )
                    )
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Section with pulsing status shield
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        PulsingShieldIcon(isActive = isAccessibilityEnabled)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "ScreenGuard Pro",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Smart Eyesight Protection",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Middle status card dashboard
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Active Protection",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isAccessibilityEnabled) "Guard active in background" else "Awaiting system activation",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                val activeBadgeColor by animateColorAsState(
                                    targetValue = if (isAccessibilityEnabled) emeraldActive else Color(0xFFEF4444),
                                    label = "badgeColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(activeBadgeColor.copy(alpha = 0.15f))
                                        .border(1.dp, activeBadgeColor, CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isAccessibilityEnabled) "RUNNING" else "INACTIVE",
                                        color = activeBadgeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 16.dp))

                            // Interactive steps info
                            OnboardingStep(
                                icon = Icons.Default.CheckCircle,
                                text = "Camera permission granted",
                                completed = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OnboardingStep(
                                icon = Icons.Default.Warning,
                                text = "Overlay drawing permission allowed",
                                completed = Settings.canDrawOverlays(context)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OnboardingStep(
                                icon = Icons.Default.Settings,
                                text = "Accessibility service toggled on",
                                completed = isAccessibilityEnabled
                            )
                        }
                    }

                    // Action buttons area
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAccessibilityEnabled) Color(0xFF475569) else primaryCrimson
                            )
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccessibilityEnabled) "Configure Accessibility" else "Enable Accessibility Shield",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "To turn off the shield temporary, click the button above and toggle the ScreenGuard switch off.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingShieldIcon(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val activeColor = if (isActive) Color(0xFF10B981) else Color(0xFFF43F5E)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .size(100.dp)
            .clip(CircleShape)
            .background(activeColor.copy(alpha = 0.08f))
            .border(2.dp, activeColor.copy(alpha = 0.3f), CircleShape)
            .padding(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.radialGradient(colors = listOf(activeColor.copy(alpha = 0.2f), Color.Transparent)))
        ) {
            Icon(
                imageVector = Icons.Default.Info, // Placeholder for shield
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
fun OnboardingStep(icon: ImageVector, text: String, completed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (completed) Color(0xFF10B981) else Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (completed) Color.White else Color(0xFF94A3B8),
            fontWeight = if (completed) FontWeight.Normal else FontWeight.Light
        )
    }
}

// Utility function to scan running system accessibility service nodes
fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val expectedComponentName = android.content.ComponentName(context, service)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)

    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}