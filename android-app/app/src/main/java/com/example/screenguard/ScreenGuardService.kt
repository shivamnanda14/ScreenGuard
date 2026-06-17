package com.example.screenguard

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.cos

class ScreenGuardService : AccessibilityService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle = lifecycleRegistry

    private var tooCloseStartTime: Long = 0
    private var lastTimeSeenTooClose: Long = 0

    private val lockThresholdDuration = 5000 // 5 seconds
    private val gracePeriodDuration = 1200 // 1.2 seconds

    private lateinit var windowManager: WindowManager
    private lateinit var rootContainer: FrameLayout
    private lateinit var animatedCardView: LinearLayout
    private lateinit var warningTitle: TextView
    private lateinit var countdownSubtitle: TextView
    private lateinit var progressBarStrip: View
    private lateinit var powerManager: PowerManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBannerShowing = false

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        FaceDetection.getClient(options)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Root container to manage overall overlay layers
        rootContainer = FrameLayout(this)

        // 🎨 CARD: Elegant visual linear layout (vertical stacking)
        animatedCardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0) // Padding is handled internally to host the progress strip nicely

            // Premium background: Vibrant Cherry-Rose Gradient with physical drop shadow (Elevation)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 45f // Smooth pill-style card edges
                colors = intArrayOf(
                    Color.parseColor("#E11D48"), // Vivid glowing rose-600
                    Color.parseColor("#9F1239")  // Deep rich rose-800
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                setStroke(3, Color.parseColor("#FDA4AF")) // Soft pink borders for a glowing physical edge
            }
            elevation = 24f // True Android dropshadow depth
        }

        // Inner vertical layout to add margins around text layers
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(50, 40, 50, 40)
        }

        // Warning Title Line
        warningTitle = TextView(this).apply {
            text = "⚠️ EYE PROTECTION SHIELD ⚠️"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }

        // Countdown Subtitle Line
        countdownSubtitle = TextView(this).apply {
            text = "Please sit back to continue"
            setTextColor(Color.parseColor("#FFE4E6")) // High contrast light-rose-100 color
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        textContainer.addView(warningTitle)
        textContainer.addView(countdownSubtitle)
        animatedCardView.addView(textContainer)

        // 📈 PROGRESS BAR: Dynamic horizontal status bar strip at the bottom
        progressBarStrip = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#FCA5A5")) // Highlight light red
            }
        }

        // Add the progress bar at the absolute bottom of the card
        val progressParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            12 // 4dp height
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        animatedCardView.addView(progressBarStrip, progressParams)

        // Add our card layout container into the main screen frame
        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(50, 30, 50, 30) // Clean screen margins
        }
        rootContainer.addView(animatedCardView, cardParams)

        // Prepare elements with clean start scales (hidden and shifted upward out of screen bounds)
        rootContainer.visibility = View.GONE
        animatedCardView.translationY = -250f
        animatedCardView.alpha = 0f

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 60 // Clean placement below system notch layout boundaries
        }

        windowManager.addView(rootContainer, windowParams)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                if (!powerManager.isInteractive) {
                    clearTrackingState()
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val image = InputImage.fromMediaImage(mediaImage, rotation)

                    val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                    val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val isAnyoneTooClose = faces.any { face ->
                                    val box = face.boundingBox
                                    val widthRatio = box.width().toFloat() / frameWidth.toFloat()
                                    val heightRatio = box.height().toFloat() / frameHeight.toFloat()

                                    val yawRad = Math.toRadians(face.headEulerAngleY.toDouble())
                                    val pitchRad = Math.toRadians(face.headEulerAngleX.toDouble())

                                    val compressionFactor = cos(yawRad) * cos(pitchRad)
                                    val adaptiveThreshold = maxOf(0.34f, 0.40f * compressionFactor.toFloat())

                                    widthRatio > adaptiveThreshold || heightRatio > adaptiveThreshold
                                }

                                if (isAnyoneTooClose) {
                                    lastTimeSeenTooClose = System.currentTimeMillis()

                                    if (tooCloseStartTime == 0L) {
                                        tooCloseStartTime = System.currentTimeMillis()
                                    }

                                    val elapsed = System.currentTimeMillis() - tooCloseStartTime
                                    val secondsLeft = maxOf(0, 5 - (elapsed / 1000).toInt())

                                    // Safely update the banner countdown and progress strip on UI Thread
                                    mainHandler.post {
                                        showBannerAnimated()
                                        countdownSubtitle.text = "⚠️ LOCKING SCREEN IN $secondsLeft SECONDS ⚠️"

                                        // Update progress bar width dynamically in real-time
                                        val progressPercent = maxOf(0f, minOf(1.0f, elapsed.toFloat() / lockThresholdDuration.toFloat()))
                                        val fullWidth = animatedCardView.width
                                        val dynamicParams = progressBarStrip.layoutParams as LinearLayout.LayoutParams
                                        // Progress bar shrinks as the lock threshold counts down
                                        dynamicParams.width = (fullWidth * (1.0f - progressPercent)).toInt()
                                        progressBarStrip.layoutParams = dynamicParams
                                    }

                                    if (elapsed >= lockThresholdDuration) {
                                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                                        clearTrackingState()
                                    }
                                } else {
                                    handleAbsence()
                                }
                            } else {
                                handleAbsence()
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Smoothly slides card down and fades in with springy bounce physics
    private fun showBannerAnimated() {
        if (isBannerShowing) return
        isBannerShowing = true
        rootContainer.visibility = View.VISIBLE

        animatedCardView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f)) // Soft bounce effect
            .start()
    }

    // Smoothly slides card back up out of view and fades out
    private fun hideBannerAnimated() {
        if (!isBannerShowing) return
        isBannerShowing = false

        animatedCardView.animate()
            .translationY(-250f)
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                rootContainer.visibility = View.GONE
            }
            .start()
    }

    private fun handleAbsence() {
        val currentTime = System.currentTimeMillis()
        if (tooCloseStartTime != 0L && (currentTime - lastTimeSeenTooClose > gracePeriodDuration)) {
            clearTrackingState()
        }
    }

    private fun clearTrackingState() {
        tooCloseStartTime = 0L
        mainHandler.post {
            hideBannerAnimated()
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        if (::rootContainer.isInitialized) {
            windowManager.removeView(rootContainer)
        }
    }
}