/**
 * Overlay foreground PLAY per la riproduzione flussi Maestro (Beta).
 */
package dev.accessscope.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.MainActivity
import dev.accessscope.scanner.R
import dev.accessscope.scanner.util.AppFileLogger

/**
 * Overlay trascinabile «STOP PLAY · Beta» durante il playback flussi Maestro.
 */
class PlaybackOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var titleView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPlaybackNow("notification")
            ACTION_ERROR -> {
                val msg = intent.getStringExtra(EXTRA_ERROR).orEmpty()
                titleView?.text = if (msg.isBlank()) "ERR · Beta" else "ERR · ${msg.take(48)}"
            }
            ACTION_STEP -> {
                val step = intent.getIntExtra(EXTRA_STEP_INDEX, -1)
                val total = intent.getIntExtra(EXTRA_STEP_TOTAL, 0)
                if (step >= 0 && total > 0) {
                    titleView?.text = "PLAY · ${step + 1}/$total"
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun stopPlaybackNow(source: String) {
        AppFileLogger.info("PlaybackOverlay", "stop_$source")
        (application as AccessScopeApp).stopFlowPlayback(userCancelled = true)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()

        val cardBg = GradientDrawable().apply {
            setColor(0xCC1B5E20.toInt())
            setStroke((1 * density).toInt(), 0x55FFFFFF)
            cornerRadius = 16 * density
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "PLAY · Beta"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        titleView = title
        root.addView(title)

        val stopBtn = TextView(this).apply {
            text = "STOP"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                setColor(0xFF2E7D32.toInt())
                cornerRadius = 24 * density
            }
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), btnBg, null)
            val vPad = (10 * density).toInt()
            val hPad = (18 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            setOnClickListener { stopPlaybackNow("overlay") }
        }
        root.addView(stopBtn)

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (120 * density).toInt()
        }

        var downX = 0f
        var downY = 0f
        var paramX = 0
        var paramY = 0
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    paramX = params.x
                    paramY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    params.x = (paramX + dx).coerceIn(0, metrics.widthPixels)
                    params.y = (paramY + dy).coerceIn(0, metrics.heightPixels)
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        overlayRoot = root
        windowManager?.addView(root, params)
    }

    private fun removeOverlay() {
        overlayRoot?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Maestro",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PlaybackOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AccessScope · Maestro Play (Beta)")
            .setContentText("Riproduzione flusso in corso")
            .setSmallIcon(R.drawable.ic_access_scope_logo)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "accessscope_playback"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "dev.accessscope.scanner.STOP_PLAYBACK"
        private const val ACTION_ERROR = "dev.accessscope.scanner.PLAYBACK_ERROR"
        private const val ACTION_STEP = "dev.accessscope.scanner.PLAYBACK_STEP"
        private const val EXTRA_ERROR = "error"
        private const val EXTRA_STEP_INDEX = "step_index"
        private const val EXTRA_STEP_TOTAL = "step_total"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PlaybackOverlayService::class.java))
        }

        /** Aggiorna overlay con errore; il servizio resta fino a STOP. */
        fun showError(context: Context, message: String) {
            context.startService(
                Intent(context, PlaybackOverlayService::class.java).apply {
                    action = ACTION_ERROR
                    putExtra(EXTRA_ERROR, message)
                },
            )
        }

        /** Aggiorna progresso step sull’overlay. */
        fun updateStep(context: Context, index: Int, total: Int) {
            context.startService(
                Intent(context, PlaybackOverlayService::class.java).apply {
                    action = ACTION_STEP
                    putExtra(EXTRA_STEP_INDEX, index)
                    putExtra(EXTRA_STEP_TOTAL, total)
                },
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackOverlayService::class.java))
        }
    }
}
