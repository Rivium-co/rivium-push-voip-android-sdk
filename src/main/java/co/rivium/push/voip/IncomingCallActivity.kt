package co.rivium.push.voip

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import coil.load
import coil.transform.CircleCropTransformation

/**
 * Full-screen incoming call UI activity with animations
 */
class IncomingCallActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_CALL_DATA = "call_data"
    }

    private var callData: CallData? = null
    private val animators = mutableListOf<ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup window flags AFTER super.onCreate() so DecorView exists
        setupWindowFlags()

        val callDataJson = intent.getStringExtra(EXTRA_CALL_DATA)
        callData = if (callDataJson != null) CallData.fromJson(callDataJson) else null

        if (callData == null) {
            Log.e(TAG, "No call data provided")
            finish()
            return
        }

        Log.d(TAG, "Showing incoming call UI for: ${callData?.callerName}, callType: ${callData?.callType}, isVideoCall: ${callData?.isVideoCall}")
        setupUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop all animations
        animators.forEach { it.cancel() }
        animators.clear()
    }

    private fun setupWindowFlags() {
        // Request fullscreen before window is created
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Make the activity truly full-screen (edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Add layout flags to draw under system bars
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Make status bar and navigation bar transparent
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun setupUI() {
        // Main container
        val mainContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        // Full-screen background avatar (blurred effect via alpha)
        val backgroundAvatar = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.25f
        }

        val avatarUrl = callData?.callerAvatar
        if (!avatarUrl.isNullOrEmpty()) {
            backgroundAvatar.load(avatarUrl) {
                crossfade(300)
            }
        } else {
            backgroundAvatar.setBackgroundColor(Color.parseColor("#1a1a2e"))
        }
        mainContainer.addView(backgroundAvatar)

        // Gradient overlay
        val gradientOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CC0D1117"),
                    Color.parseColor("#800D1117"),
                    Color.parseColor("#CC0D1117")
                )
            )
        }
        mainContainer.addView(gradientOverlay)

        // Content layout
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(32), dpToPx(60), dpToPx(32), dpToPx(50))
        }

        // Call type label with icon
        val callTypeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val callTypeLabel = TextView(this).apply {
            text = if (callData?.isVideoCall == true) "Incoming Video Call" else "Incoming Voice Call"
            textSize = 18f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            alpha = 0f
        }
        callTypeContainer.addView(callTypeLabel)
        contentLayout.addView(callTypeContainer)

        // Animate call type label
        callTypeLabel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(100)
            .start()

        // Top spacer
        contentLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(50))
        })

        // Avatar container with pulsing rings
        val avatarContainerSize = dpToPx(200)
        val avatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(avatarContainerSize, avatarContainerSize).apply {
                gravity = Gravity.CENTER
            }
        }

        // Pulsing rings
        val ringColors = listOf("#4CAF50", "#66BB6A", "#81C784")
        val ringSizes = listOf(dpToPx(200), dpToPx(220), dpToPx(240))

        for (i in ringSizes.indices.reversed()) {
            val ring = View(this).apply {
                val size = ringSizes[i]
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(2), Color.parseColor(ringColors[i]))
                    setColor(Color.TRANSPARENT)
                }
                alpha = 0f
            }
            avatarContainer.addView(ring)

            // Pulsing animation for each ring
            val pulseAnimator = ValueAnimator.ofFloat(0.3f, 0.8f, 0.3f).apply {
                duration = 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    ring.alpha = animation.animatedValue as Float
                }
            }
            pulseAnimator.start()
            animators.add(pulseAnimator)

            // Scale animation
            val scaleAnimator = ValueAnimator.ofFloat(0.9f, 1.05f, 0.9f).apply {
                duration = 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    val scale = animation.animatedValue as Float
                    ring.scaleX = scale
                    ring.scaleY = scale
                }
            }
            scaleAnimator.start()
            animators.add(scaleAnimator)
        }

        // Avatar image
        val avatarSize = dpToPx(170)
        val avatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        if (!avatarUrl.isNullOrEmpty()) {
            avatarImage.load(avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        } else {
            avatarImage.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#667eea"),
                    Color.parseColor("#764ba2")
                )
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }
        }
        avatarContainer.addView(avatarImage)

        // Animate avatar entrance
        avatarImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .setStartDelay(200)
            .start()

        contentLayout.addView(avatarContainer)

        // Spacer after avatar
        contentLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(35))
        })

        // Caller name
        val callerNameText = TextView(this).apply {
            text = callData?.callerName ?: "Unknown"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setShadowLayer(12f, 0f, 4f, Color.parseColor("#80000000"))
            alpha = 0f
            translationY = dpToPx(20).toFloat()
        }
        contentLayout.addView(callerNameText)

        // Animate caller name
        callerNameText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .start()

        // App name / subtitle
        val config = RiviumPushVoip.getConfig()
        val appNameText = TextView(this).apply {
            text = config?.appName ?: "App"
            textSize = 15f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, 0)
            alpha = 0f
        }
        contentLayout.addView(appNameText)

        // Animate app name
        appNameText.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(400)
            .start()

        // Flexible spacer
        contentLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        // Buttons container
        val buttonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            alpha = 0f
            translationY = dpToPx(30).toFloat()
        }

        val buttonSize = dpToPx(72)
        val iconSize = dpToPx(36)

        // Decline button column
        val declineColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(50)
            }
        }

        val declineButton = createEnhancedButton(
            size = buttonSize,
            iconSize = iconSize,
            gradientColors = intArrayOf(Color.parseColor("#FF5252"), Color.parseColor("#C62828")),
            iconRes = R.drawable.ic_call_decline
        ) { declineCall() }
        declineColumn.addView(declineButton)

        val declineLabel = TextView(this).apply {
            text = "Decline"
            textSize = 13f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, 0)
        }
        declineColumn.addView(declineLabel)

        buttonsContainer.addView(declineColumn)

        // Accept button column
        val acceptColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val acceptButton = createEnhancedButton(
            size = buttonSize,
            iconSize = iconSize,
            gradientColors = intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#2E7D32")),
            iconRes = R.drawable.ic_call_accept
        ) { acceptCall() }
        acceptColumn.addView(acceptButton)

        val acceptLabel = TextView(this).apply {
            text = "Accept"
            textSize = 13f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, 0)
        }
        acceptColumn.addView(acceptLabel)

        buttonsContainer.addView(acceptColumn)

        // Animate buttons entrance
        buttonsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(500)
            .start()

        contentLayout.addView(buttonsContainer)

        // Swipe hint text
        val swipeHint = TextView(this).apply {
            text = "Swipe up for more options"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(24), 0, 0)
            alpha = 0f
        }
        contentLayout.addView(swipeHint)

        swipeHint.animate()
            .alpha(0.6f)
            .setDuration(500)
            .setStartDelay(800)
            .start()

        mainContainer.addView(contentLayout)
        setContentView(mainContainer)
    }

    private fun createEnhancedButton(
        size: Int,
        iconSize: Int,
        gradientColors: IntArray,
        iconRes: Int,
        onClick: () -> Unit
    ): FrameLayout {
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        // Outer glow/shadow
        val glowSize = size + dpToPx(8)
        val glow = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(glowSize, glowSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#40${Integer.toHexString(gradientColors[0]).takeLast(6)}"))
            }
            alpha = 0.5f
        }
        container.addView(glow)

        // Button background with gradient
        val buttonBackground = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = gradientColors
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size.toFloat()
            }
            elevation = dpToPx(6).toFloat()
        }
        container.addView(buttonBackground)

        // Icon - must have higher elevation than buttonBackground to be visible
        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            // Use AppCompatResources for proper vector drawable loading on all API levels
            val drawable = AppCompatResources.getDrawable(this@IncomingCallActivity, iconRes)?.mutate()
            if (drawable != null) {
                // Use setTint for more reliable color application on vector drawables
                drawable.setTint(Color.WHITE)
                setImageDrawable(drawable)
                Log.d(TAG, "Icon drawable loaded successfully for res: $iconRes, drawable: $drawable")
            } else {
                Log.e(TAG, "Failed to load icon drawable for res: $iconRes")
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Set elevation higher than buttonBackground (6dp) so icon appears on top
            elevation = dpToPx(8).toFloat()
        }
        container.addView(icon)

        // Touch feedback animation
        container.setOnClickListener {
            container.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    container.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction { onClick() }
                        .start()
                }
                .start()
        }
        container.isClickable = true
        container.isFocusable = true

        return container
    }

    private fun acceptCall() {
        Log.d(TAG, "Call accepted from activity")
        val data = callData
        if (data != null) {
            CallNotificationManager.cancelNotification(this, data.callId)
            RiviumPushVoip.activeCall = null
            RiviumPushVoip.setInitialCall(data)

            val hasNativeHandler = RiviumPushVoip.hasNativeCallHandler()
            val appWasRunning = RiviumPushVoip.hasCallback()
            Log.d(TAG, "Has native call handler: $hasNativeHandler, app was running: $appWasRunning")

            if (hasNativeHandler) {
                if (appWasRunning) {
                    Log.d(TAG, "App running + native handler - launching MainActivity first, then Jitsi")

                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        launchIntent.putExtra("rivium_push_voip_call_accepted", true)
                        launchIntent.putExtra("rivium_push_voip_call_data", data.toJson())
                        launchIntent.putExtra("rivium_push_voip_launching_native", true)
                        startActivity(launchIntent)
                        Log.d(TAG, "MainActivity launched (will be in background)")
                    }

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val handledNatively = RiviumPushVoip.notifyCallAccepted(data)
                        Log.d(TAG, "Call handled natively: $handledNatively")

                        val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED)
                        broadcastIntent.putExtra(RiviumPushVoip.EXTRA_CALL_DATA, data.toJson())
                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                    }, 100)
                } else {
                    Log.d(TAG, "Cold start + native handler - launching Jitsi directly (no MainActivity)")

                    val handledNatively = RiviumPushVoip.notifyCallAccepted(data)
                    Log.d(TAG, "Call handled natively: $handledNatively")

                    val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED)
                    broadcastIntent.putExtra(RiviumPushVoip.EXTRA_CALL_DATA, data.toJson())
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                }
            } else {
                Log.d(TAG, "No native handler - launching MainActivity only")

                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    launchIntent.putExtra("rivium_push_voip_call_accepted", true)
                    launchIntent.putExtra("rivium_push_voip_call_data", data.toJson())
                    startActivity(launchIntent)
                }

                RiviumPushVoip.notifyCallAccepted(data)
                val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED)
                broadcastIntent.putExtra(RiviumPushVoip.EXTRA_CALL_DATA, data.toJson())
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            }
        }
        finish()
    }

    private fun declineCall() {
        Log.d(TAG, "Call declined from activity")
        val data = callData
        if (data != null) {
            CallNotificationManager.cancelNotification(this, data.callId)
            RiviumPushVoip.activeCall = null

            RiviumPushVoip.notifyCallDeclined(data)

            val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_DECLINED)
            broadcastIntent.putExtra(RiviumPushVoip.EXTRA_CALL_DATA, data.toJson())
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't allow back press to dismiss incoming call
    }
}
