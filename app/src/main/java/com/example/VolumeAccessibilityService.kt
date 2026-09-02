package com.example
import com.example.R

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Production-ready Accessibility Service designed for Android 11 (API 30) on OPPO A9 2020
 * and modern Android devices to replace broken physical volume buttons.
 *
 * Provides:
 * 1. Native Accessibility Button on the navigation bar.
 * 2. Responsive multi-stream volume control panel (Call, Media, Ring, Alarm).
 * 3. Smart call context switching (auto-focusing voice call stream during phone/VoIP calls).
 * 4. Android 11 (API 30) native screenshot trigger via takeScreenshot().
 * 5. Dynamic orientation handling (Portrait & Landscape).
 */
class VolumeAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private var isPanelShowing = false

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    // Accessibility button callback for the navigation bar
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    // Volume settings change observer
    private var volumeObserver: ContentObserver? = null
    private var volumeReceiver: BroadcastReceiver? = null

    // Saved stream levels for unmuting
    private val previousVolumes = mutableMapOf<Int, Int>()

    companion object {
        var instance: VolumeAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        fun triggerPanel() {
            instance?.showVolumePanel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerVolumeListeners()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Register callback for the software button on the navigation bar (Android 8.0+ / API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val controller = accessibilityButtonController
            buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    showVolumePanel()
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                    // Availability updated
                }
            }
            buttonCallback?.let {
                controller.registerAccessibilityButtonCallback(it)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events handled if needed
    }

    override fun onInterrupt() {
        dismissVolumePanel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isPanelShowing) {
            // Re-inflate with orientation-specific layout without losing state
            dismissVolumePanel()
            showVolumePanel()
        }
    }

    /**
     * Inflates and presents the responsive floating volume overlay.
     */
    fun showVolumePanel() {
        if (isPanelShowing && panelView != null) {
            resetAutoDismissTimer()
            updateAllSliders()
            return
        }

        try {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.dialog_volume_panel, null)
            panelView = view

            val displayMetrics = resources.displayMetrics
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val panelWidth = if (isLandscape) {
                (displayMetrics.widthPixels * 0.85).toInt().coerceAtMost(dpToPx(620))
            } else {
                (displayMetrics.widthPixels * 0.92).toInt().coerceAtMost(dpToPx(440))
            }

            val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }

            val params = WindowManager.LayoutParams(
                panelWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                y = if (isLandscape) dpToPx(16) else dpToPx(48)
            }

            // Outside touch listener to dismiss panel
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismissVolumePanel()
                    true
                } else {
                    resetAutoDismissTimer()
                    false
                }
            }

            setupPanelViews(view)

            windowManager?.addView(view, params)
            isPanelShowing = true
            resetAutoDismissTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not open volume panel: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds all sliders, call detection, screenshot button, and actions.
     */
    private fun setupPanelViews(view: View) {
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)
        val btnScreenshot = view.findViewById<View>(R.id.btn_screenshot)
        val btnSoundMode = view.findViewById<Button>(R.id.btn_sound_mode)
        val btnOpenApp = view.findViewById<Button>(R.id.btn_open_app)
        val tvCallBadge = view.findViewById<TextView>(R.id.tv_call_status_badge)
        val cardCall = view.findViewById<LinearLayout>(R.id.card_stream_call)

        // Close action
        btnClose?.setOnClickListener {
            dismissVolumePanel()
        }

        // Android 11 Native Screenshot Shortcut
        btnScreenshot?.setOnClickListener {
            handleTakeScreenshot()
        }

        // Open Companion / Settings App
        btnOpenApp?.setOnClickListener {
            dismissVolumePanel()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        // Sound Mode Cycle (Normal -> Vibrate -> Silent -> Normal)
        updateSoundModeButtonText(btnSoundMode)
        btnSoundMode?.setOnClickListener {
            cycleSoundMode(btnSoundMode)
            resetAutoDismissTimer()
        }

        // Smart Context Switching: Check if call is active
        val isCallActive = isCallInProgress()
        if (isCallActive) {
            tvCallBadge?.visibility = View.VISIBLE
            cardCall?.setBackgroundResource(R.drawable.bg_stream_card_active)
        } else {
            tvCallBadge?.visibility = View.GONE
            cardCall?.setBackgroundResource(R.drawable.bg_stream_card)
        }

        // Setup individual stream sliders
        setupStreamSlider(
            view = view,
            streamType = AudioManager.STREAM_VOICE_CALL,
            seekBarId = R.id.seekbar_call,
            levelTextId = R.id.tv_call_level,
            muteBtnId = R.id.btn_mute_call
        )

        setupStreamSlider(
            view = view,
            streamType = AudioManager.STREAM_MUSIC,
            seekBarId = R.id.seekbar_media,
            levelTextId = R.id.tv_media_level,
            muteBtnId = R.id.btn_mute_media
        )

        setupStreamSlider(
            view = view,
            streamType = AudioManager.STREAM_RING,
            seekBarId = R.id.seekbar_ring,
            levelTextId = R.id.tv_ring_level,
            muteBtnId = R.id.btn_mute_ring
        )

        setupStreamSlider(
            view = view,
            streamType = AudioManager.STREAM_ALARM,
            seekBarId = R.id.seekbar_alarm,
            levelTextId = R.id.tv_alarm_level,
            muteBtnId = R.id.btn_mute_alarm
        )
    }

    /**
     * Configures a SeekBar for a specific audio stream with live updates and muting.
     */
    private fun setupStreamSlider(
        view: View,
        streamType: Int,
        seekBarId: Int,
        levelTextId: Int,
        muteBtnId: Int
    ) {
        val seekBar = view.findViewById<SeekBar>(seekBarId) ?: return
        val levelText = view.findViewById<TextView>(levelTextId)
        val muteBtn = view.findViewById<ImageView>(muteBtnId)

        val max = audioManager.getStreamMaxVolume(streamType)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(streamType)
        } else {
            0
        }
        val current = audioManager.getStreamVolume(streamType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            seekBar.min = min
        }
        seekBar.max = max
        seekBar.progress = current
        levelText?.text = "$current/$max"

        updateMuteIcon(muteBtn, current, min)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        audioManager.setStreamVolume(streamType, progress, 0)
                        levelText?.text = "$progress/$max"
                        updateMuteIcon(muteBtn, progress, min)
                        resetAutoDismissTimer()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                cancelAutoDismissTimer()
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                resetAutoDismissTimer()
            }
        })

        muteBtn?.setOnClickListener {
            val currVol = audioManager.getStreamVolume(streamType)
            if (currVol > min) {
                // Mute
                previousVolumes[streamType] = currVol
                audioManager.setStreamVolume(streamType, min, 0)
                seekBar.progress = min
                levelText?.text = "$min/$max"
                updateMuteIcon(muteBtn, min, min)
            } else {
                // Unmute
                val restoreVol = previousVolumes[streamType] ?: (max / 2).coerceAtLeast(min + 1)
                audioManager.setStreamVolume(streamType, restoreVol, 0)
                seekBar.progress = restoreVol
                levelText?.text = "$restoreVol/$max"
                updateMuteIcon(muteBtn, restoreVol, min)
            }
            resetAutoDismissTimer()
        }
    }

    private fun updateMuteIcon(muteBtn: ImageView?, current: Int, min: Int) {
        if (muteBtn == null) return
        if (current <= min) {
            muteBtn.setImageResource(R.drawable.ic_volume_mute)
            muteBtn.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            muteBtn.setImageResource(R.drawable.ic_volume_up)
            muteBtn.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    /**
     * Detects if an active call or VoIP communication is taking place.
     */
    private fun isCallInProgress(): Boolean {
        val mode = audioManager.mode
        return mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
    }

    /**
     * Executes device screenshot using the Android 11 (API 30) takeScreenshot API.
     */
    private fun handleTakeScreenshot() {
        // Temporarily dismiss the panel so it doesn't appear in the user's screenshot
        dismissVolumePanel()

        handler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        Toast.makeText(
                            this@VolumeAccessibilityService,
                            R.string.screenshot_captured,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onFailure(errorCode: Int) {
                        Toast.makeText(
                            this@VolumeAccessibilityService,
                            getString(R.string.screenshot_failed, errorCode),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            } else {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }, 250)
    }

    private fun updateSoundModeButtonText(button: Button?) {
        if (button == null) return
        val text = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Mode: Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Mode: Vibrate"
            else -> "Mode: Normal"
        }
        button.text = text
    }

    private fun cycleSoundMode(button: Button?) {
        try {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                else -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
            updateSoundModeButtonText(button)
            updateAllSliders()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Updates all UI sliders when volume changes externally.
     */
    fun updateAllSliders() {
        panelView?.let { view ->
            updateSlider(view, AudioManager.STREAM_VOICE_CALL, R.id.seekbar_call, R.id.tv_call_level, R.id.btn_mute_call)
            updateSlider(view, AudioManager.STREAM_MUSIC, R.id.seekbar_media, R.id.tv_media_level, R.id.btn_mute_media)
            updateSlider(view, AudioManager.STREAM_RING, R.id.seekbar_ring, R.id.tv_ring_level, R.id.btn_mute_ring)
            updateSlider(view, AudioManager.STREAM_ALARM, R.id.seekbar_alarm, R.id.tv_alarm_level, R.id.btn_mute_alarm)
        }
    }

    private fun updateSlider(view: View, stream: Int, seekId: Int, levelId: Int, muteId: Int) {
        val sb = view.findViewById<SeekBar>(seekId) ?: return
        val tv = view.findViewById<TextView>(levelId)
        val mute = view.findViewById<ImageView>(muteId)

        val max = audioManager.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioManager.getStreamMinVolume(stream) else 0
        val current = audioManager.getStreamVolume(stream)

        sb.progress = current
        tv?.text = "$current/$max"
        updateMuteIcon(mute, current, min)
    }

    private fun resetAutoDismissTimer() {
        cancelAutoDismissTimer()
        autoDismissRunnable = Runnable {
            dismissVolumePanel()
        }
        handler.postDelayed(autoDismissRunnable!!, 6000)
    }

    private fun cancelAutoDismissTimer() {
        autoDismissRunnable?.let {
            handler.removeCallbacks(it)
            autoDismissRunnable = null
        }
    }

    fun dismissVolumePanel() {
        cancelAutoDismissTimer()
        if (isPanelShowing && panelView != null) {
            try {
                windowManager?.removeView(panelView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            panelView = null
            isPanelShowing = false
        }
    }

    private fun registerVolumeListeners() {
        // Observer for Settings.System volume changes
        volumeObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (isPanelShowing) {
                    updateAllSliders()
                }
            }
        }
        try {
            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // BroadcastReceiver for volume changes
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isPanelShowing) {
                    updateAllSliders()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.RINGER_MODE_CHANGED")
        }
        registerReceiver(volumeReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        dismissVolumePanel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && buttonCallback != null) {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback!!)
        }

        volumeObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
