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
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize UI Elements and Layout Arrays
        setupOverlayLayout()

        // Boot CameraX Analysis Core
        startCameraTracking()
    }

    private fun setupOverlayLayout() {
        // The root container now uses a deep, dark slate with 90% opacity
        // to create a heavy overlay mask if the device doesn't support hardware blur.
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E60F172A")) // Slate-900 with high alpha (90% dark)

            // Block all touches from leaking to the apps underneath.
            // The child CANNOT click anything until they step back.
            isClickable = true
            isFocusable = true
        }

        // Beautiful central warning shield card
        animatedCardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Crisp Slate-800
                cornerRadius = 48f
                setStroke(4, Color.parseColor("#E11D48")) // Rose-600 premium alert accent border
            }
            background = backgroundDrawable
        }

        warningTitle = TextView(this).apply {
            text = "⚠️ Screen Shield Active"
            setTextColor(Color.parseColor("#F43F5E")) // Rose-500
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        countdownSubtitle = TextView(this).apply {
            text = "Too close! Please look away or step back 25 cm."
            setTextColor(Color.parseColor("#CBD5E1")) // Soft slate grey
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }

        progressBarStrip = View(this).apply {
            val stripDecoration = GradientDrawable().apply {
                setColor(Color.parseColor("#F43F5E"))
                cornerRadius = 12f
            }
            background = stripDecoration
        }

        // Nest UI hierarchies
        animatedCardView.addView(warningTitle)
        animatedCardView.addView(countdownSubtitle)
        animatedCardView.addView(progressBarStrip, LinearLayout.LayoutParams(450, 14))

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootContainer.addView(animatedCardView, cardParams)
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCameraTracking() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && powerManager.isInteractive) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            // Read chosen mode from shared preferences configuration
                            val isChildProfileEnabled = PreferencesManager.isChildMode(applicationContext)

                            // 📐 CALIBRATION COEFFICIENTS AT EXACTLY 25 CM BOUNDARY
                            val ADULT_PIXEL_WIDTH_THRESHOLD = 280
                            val CHILD_PIXEL_WIDTH_THRESHOLD = 210

                            val targetThreshold = if (isChildProfileEnabled) {
                                CHILD_PIXEL_WIDTH_THRESHOLD
                            } else {
                                ADULT_PIXEL_WIDTH_THRESHOLD
                            }

                            if (faces.isNotEmpty()) {
                                var breachDetected = false
                                for (face in faces) {
                                    val actualFaceWidth = face.boundingBox.width()
                                    if (actualFaceWidth >= targetThreshold) {
                                        breachDetected = true
                                        break
                                    }
                                }

                                if (breachDetected) {
                                    lastTimeSeenTooClose = System.currentTimeMillis()
                                    if (tooCloseStartTime == 0L) {
                                        tooCloseStartTime = lastTimeSeenTooClose
                                    }

                                    // Trigger non-blocking UI layout drawing
                                    mainHandler.post { showSafetyOverlay() }
                                } else {
                                    evaluateGracePeriod()
                                }
                            } else {
                                evaluateGracePeriod()
                            }
                        }
                        .addOnFailureListener { it.printStackTrace() }
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

    private fun evaluateGracePeriod() {
        val currentTime = System.currentTimeMillis()
        if (tooCloseStartTime != 0L && (currentTime - lastTimeSeenTooClose > gracePeriodDuration)) {
            tooCloseStartTime = 0L
            mainHandler.post { hideSafetyOverlay() }
        }
    }

    private fun showSafetyOverlay() {
        if (isBannerShowing) return

        // Configure layout params to intercept the entire viewport surface
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND, // ⬅️ Enables deep dimming behavior
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.85f // ⬅️ Dims the background apps by 85% for intense focus
        }

        // 🌟 NATIVE HARDWARE BLUR (For Android 12 / API 31 and above)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            params.blurBehindRadius = 35 // Creates a gorgeous frosted-glass blur over whatever the child was watching
        }

        try {
            windowManager.addView(rootContainer, params)
            isBannerShowing = true

            // Clean overshoot spring animation to pop the card forward smoothly
            animatedCardView.scaleX = 0.5f
            animatedCardView.scaleY = 0.5f
            animatedCardView.alpha = 0f
            animatedCardView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun hideSafetyOverlay() {
        if (!isBannerShowing) return
        try {
            windowManager.removeView(rootContainer)
            isBannerShowing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        hideSafetyOverlay()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
    }
}