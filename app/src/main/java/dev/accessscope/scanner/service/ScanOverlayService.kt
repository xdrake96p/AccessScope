package dev.accessscope.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.MainActivity
import dev.accessscope.scanner.R
import dev.accessscope.scanner.util.DebugTrace

class ScanOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScanNow("notification")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun stopScanNow(source: String) {
        // #region agent log
        DebugTrace.log("H-STOP1", "OverlayService", "stop_$source", emptyMap())
        // #endregion
        (application as AccessScopeApp).stopScanSession(fromOverlay = true)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (7 * density).toInt()
        val cornerRadius = 18 * density

        val shape = GradientDrawable().apply {
            setColor(0xFFE53935.toInt())
            this.cornerRadius = cornerRadius
        }
        val backgroundDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
        } else {
            shape
        }

        val stopButton = TextView(this).apply {
            text = "STOP"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(padH, padV, padH, padV)
            background = backgroundDrawable
            elevation = 6f
            isClickable = true
            isFocusable = true
            setOnClickListener { stopScanNow("button") }
        }
        overlayView = stopButton

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        windowManager?.addView(stopButton, layoutParams)
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.overlay_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "access_scope_scan"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "dev.accessscope.scanner.STOP_SCAN"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ScanOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanOverlayService::class.java))
        }
    }
}
