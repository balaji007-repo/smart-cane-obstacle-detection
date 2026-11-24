package com.smartcane.prototype

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@TransformExperimental
class MainActivity : ComponentActivity() {
    private enum class OrientationMode(val labelId: Int, val request: Int) {
        AUTO(R.string.orientation_auto, ActivityInfo.SCREEN_ORIENTATION_FULL_USER),
        LANDSCAPE(R.string.orientation_landscape, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
        PORTRAIT(R.string.orientation_portrait, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    }

    private data class CameraLayout(val width: Int, val height: Int, val rotation: Int)
    private data class CameraBinding(val layout: CameraLayout, val preview: Preview, val analysis: ImageAnalysis)

    private lateinit var preview: PreviewView
    private lateinit var obstacleOverlay: ObstacleOverlay
    private lateinit var status: TextView
    private lateinit var orientationButton: ImageButton
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraBinding: CameraBinding? = null
    private var detector: ObstacleDetector? = null // Accessed only on analysisExecutor.
    private var cameraRequested = false
    private var cameraMessage = R.string.starting_camera
    private var speechMessage = 0
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var activeUtterance: String? = null
    private var utteranceCount = 0L
    private var alerts = ObstacleAlerts()
    private var startedAt = Long.MAX_VALUE

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (preview.display?.displayId == displayId) bindCamera()
        }

        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else setCameraMessage(R.string.camera_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bindCamera() }
        }
        obstacleOverlay = ObstacleOverlay(this).apply {
            gridLayout = gridFor(resources.configuration)
        }
        orientationButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_orientation_menu)
            setBackgroundResource(R.drawable.orientation_menu_background)
        }
        val savedMode = getPreferences(MODE_PRIVATE).getString(ORIENTATION_PREFERENCE, null)
        val mode = OrientationMode.entries.firstOrNull { it.name == savedMode } ?: OrientationMode.AUTO
        status = TextView(this).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xEE172025.toInt())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val menuSize = 48.dp
        val menuMargin = 12.dp
        val cameraView = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(obstacleOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
            addView(orientationButton, FrameLayout.LayoutParams(menuSize, menuSize, Gravity.BOTTOM or Gravity.RIGHT).apply {
                rightMargin = menuMargin
                bottomMargin = menuMargin
            })
        }
        ViewCompat.setOnApplyWindowInsetsListener(cameraView) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            orientationButton.updateLayoutParams<FrameLayout.LayoutParams> {
                rightMargin = menuMargin + cutout.right
                bottomMargin = menuMargin + cutout.bottom
            }
            status.updateLayoutParams<FrameLayout.LayoutParams> {
                leftMargin = cutout.left
                rightMargin = cutout.right
                bottomMargin = menuSize + 2 * menuMargin + cutout.bottom
            }
            obstacleOverlay.labelTopInset = cutout.top
            insets
        }
        setContentView(cameraView)
        hideSystemBars()
        setOrientationMode(mode)
        updateStatus()

        speech = TextToSpeech(this) { result -> main.post { configureSpeech(result) } }
        if (!hasCameraPermission() && savedInstanceState == null) {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.elapsedRealtime()
        displayManager.registerDisplayListener(displayListener, main)
        bindCamera()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) startCamera() else setCameraMessage(R.string.camera_permission)
    }

    override fun onStop() {
        displayManager.unregisterDisplayListener(displayListener)
        startedAt = Long.MAX_VALUE
        clearPresentation()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        obstacleOverlay.gridLayout = gridFor(newConfig)
        preview.post { bindCamera() }
    }

    override fun onDestroy() {
        cameraBinding?.analysis?.clearAnalyzer()
        cameraBinding = null
        analysisExecutor.execute { detector?.close() }
        analysisExecutor.shutdown()
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun gridFor(configuration: Configuration): ObstacleGrid =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ObstacleGrid.COLUMNS else ObstacleGrid.ROWS

    private fun setOrientationMode(mode: OrientationMode) {
        orientationButton.contentDescription = getString(R.string.orientation_mode, getString(mode.labelId))
        orientationButton.setOnClickListener {
            PopupMenu(this, orientationButton, Gravity.RIGHT).apply {
                for (option in OrientationMode.entries) {
                    menu.add(0, option.ordinal, option.ordinal, option.labelId)
                }
                menu.setGroupCheckable(0, true, true)
                menu.findItem(mode.ordinal).isChecked = true
                setOnMenuItemClickListener { item ->
                    val choice = OrientationMode.entries[item.itemId]
                    getPreferences(MODE_PRIVATE).edit().putString(ORIENTATION_PREFERENCE, choice.name).apply()
                    setOrientationMode(choice)
                    true
                }
            }.show()
        }
        if (requestedOrientation != mode.request) requestedOrientation = mode.request
    }

    private fun startCamera() {
        if (cameraRequested) return
        cameraRequested = true
        setCameraMessage(R.string.starting_camera)
        val provider = ProcessCameraProvider.getInstance(this)
        provider.addListener({
            if (isDestroyed || isFinishing) return@addListener
            try {
                cameraProvider = provider.get()
                bindCamera()
            } catch (failure: Exception) {
                Log.e(TAG, "Camera provider failed", failure)
                setCameraMessage(R.string.camera_error)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun currentCameraLayout(): CameraLayout? {
        val rotation = preview.display?.rotation ?: return null
        if (preview.width <= 0 || preview.height <= 0) return null
        return CameraLayout(preview.width, preview.height, rotation)
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        if (startedAt == Long.MAX_VALUE || isDestroyed || isFinishing) return
        val layout = currentCameraLayout() ?: return
        if (cameraBinding?.layout == layout) return
        val viewport = preview.getViewPort(layout.rotation) ?: return

        cameraBinding?.let { binding ->
            binding.analysis.clearAnalyzer()
            provider.unbind(binding.preview, binding.analysis)
        }
        cameraBinding = null
        clearPresentation()
        setCameraMessage(R.string.starting_camera)
        try {
            val cameraPreview = Preview.Builder().setTargetRotation(layout.rotation).build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(layout.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            val group = UseCaseGroup.Builder().setViewPort(viewport)
                .addUseCase(cameraPreview).addUseCase(imageAnalysis).build()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
            cameraBinding = CameraBinding(layout, cameraPreview, imageAnalysis)
            imageAnalysis.setAnalyzer(analysisExecutor) { frame -> analyze(imageAnalysis, frame) }
        } catch (failure: Exception) {
            Log.e(TAG, "Camera setup failed", failure)
            setCameraMessage(R.string.camera_error)
        }
    }

    private fun analyze(source: ImageAnalysis, frame: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        try {
            val activeDetector = detector ?: ObstacleDetector(applicationContext).also { detector = it }
            val result = activeDetector.detect(frame)
            main.post { render(source, result, now) }
        } catch (failure: Throwable) {
            if (failure !is RuntimeException && failure !is LinkageError) throw failure
            Log.e(TAG, "On-device inference failed", failure)
            main.post {
                if (!isDestroyed && cameraBinding?.analysis === source) {
                    source.clearAnalyzer()
                    clearPresentation()
                    setCameraMessage(R.string.detector_error)
                }
            }
        } finally {
            frame.close()
        }
    }

    private fun render(source: ImageAnalysis, frame: DetectionFrame, now: Long) {
        if (isDestroyed || now < startedAt) return
        val binding = cameraBinding ?: return
        if (binding.analysis !== source || binding.layout != currentCameraLayout()) return
        if (preview.previewStreamState.value != PreviewView.StreamState.STREAMING) return
        val transform = CoordinateTransform(frame.transform, preview.outputTransform ?: return)
        val width = obstacleOverlay.width
        val height = obstacleOverlay.height
        if (width <= 0 || height <= 0) return

        val obstacles = frame.boxes.mapNotNull { box ->
            val visibleBox = RectF(box)
            transform.mapRect(visibleBox)
            if (visibleBox.intersect(0f, 0f, width.toFloat(), height.toFloat())) {
                val zone = obstacleOverlay.gridLayout.zoneAt(visibleBox.centerX(), visibleBox.centerY(), width, height)
                Obstacle(visibleBox, zone)
            } else {
                null
            }
        }
        obstacleOverlay.obstacles = obstacles
        setCameraMessage(0)
        val occupied = obstacles.mapTo(mutableSetOf()) { it.zone }
        alerts.update(occupied, now, ::speak)
    }

    private fun configureSpeech(result: Int) {
        val engine = speech ?: return
        val voice = if (result == TextToSpeech.SUCCESS) {
            engine.voices.orEmpty().filter {
                it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.minWithOrNull(compareBy<Voice> { it.latency }.thenByDescending { it.quality })
        } else {
            null
        }
        speechReady = voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS
        if (speechReady) {
            engine.setSpeechRate(1.15f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) = finishUtterance(utteranceId)
                override fun onError(utteranceId: String) = finishUtterance(utteranceId)
                override fun onStop(utteranceId: String, interrupted: Boolean) = finishUtterance(utteranceId)
            })
        }
        speechMessage = if (speechReady) 0 else R.string.voice_unavailable
        updateStatus()
    }

    private fun finishUtterance(utteranceId: String) {
        main.post {
            if (activeUtterance == utteranceId) activeUtterance = null
        }
    }

    private fun clearPresentation() {
        speech?.stop()
        activeUtterance = null
        alerts = ObstacleAlerts()
        obstacleOverlay.obstacles = emptyList()
    }

    private fun speak(zone: Zone): Boolean {
        val engine = speech ?: return false
        if (!speechReady || activeUtterance != null) return false
        val direction = getString(zone.labelId).lowercase(Locale.ROOT)
        val message = getString(R.string.obstacle_alert, direction)
        val utteranceId = "obstacle-${++utteranceCount}"
        if (engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) return false
        activeUtterance = utteranceId
        return true
    }

    private fun setCameraMessage(message: Int) {
        if (message == cameraMessage) return
        cameraMessage = message
        updateStatus()
    }

    private fun updateStatus() {
        val message = if (cameraMessage != 0) cameraMessage else speechMessage
        status.visibility = if (message == 0) View.GONE else View.VISIBLE
        if (message != 0) status.setText(message)
    }

    private companion object {
        const val TAG = "SmartCane"
        const val ORIENTATION_PREFERENCE = "orientation_mode"
    }
}
