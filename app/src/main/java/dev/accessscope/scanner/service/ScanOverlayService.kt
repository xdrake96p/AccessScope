/**
 * Servizio in primo piano che mostra l'overlay di scansione attiva.
 *
 * Pulsante STOP in una card semi-trasparente trascinabile dall'utente.
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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.MainActivity
import dev.accessscope.scanner.R
import dev.accessscope.scanner.util.AppFileLogger

/**
 * [Service] foreground con overlay STOP in card trascinabile.
 */
class ScanOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

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
        AppFileLogger.info("OverlayService", "stop_$source")
        (application as AccessScopeApp).stopScanSession(fromOverlay = true)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = (application as AccessScopeApp).scanSettingsStore
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()

        val cardBg = GradientDrawable().apply {
            // Deep space del nuovo design system (surface dark #0D1518, 60% opaco)
            setColor(0x990D1518.toInt())
            setStroke((1 * density).toInt(), 0x55FFFFFF)
            cornerRadius = 16 * density
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(pad, pad, pad, pad)
            elevation = 12f
        }

        val dragHandle = TextView(this).apply {
            text = "⋮⋮  Trascina"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        root.addView(dragHandle)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        buttonRow.addView(
            createPillButton(
                label = "STOP",
                // Error del nuovo design system (#BA1A1A)
                bgColor = 0xFFBA1A1A.toInt(),
                onClick = { stopScanNow("button") },
            ),
        )
        root.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            val defaultX = dm.widthPixels - (200 * density).toInt()
            val defaultY = (100 * density).toInt()
            x = if (settings.overlayPositionX >= 0) settings.overlayPositionX else defaultX
            y = if (settings.overlayPositionY >= 0) settings.overlayPositionY else defaultY
        }

        setupDragHandle(dragHandle, root, settings)

        overlayRoot = root
        overlayParams = params
        windowManager?.addView(root, params)
        root.post { clampAndUpdateOverlayPosition() }
    }

    private fun setupDragHandle(handle: View, root: View, settings: dev.accessscope.scanner.util.ScanSettingsStore) {
        var touchStartX = 0f
        var touchStartY = 0f
        var paramStartX = 0
        var paramStartY = 0
        handle.setOnTouchListener { _, event ->
            val params = overlayParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    paramStartX = params.x
                    paramStartY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = paramStartX + (event.rawX - touchStartX).toInt()
                    params.y = paramStartY + (event.rawY - touchStartY).toInt()
                    clampOverlayPosition(params, root)
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    settings.overlayPositionX = params.x
                    settings.overlayPositionY = params.y
                    true
                }
                else -> false
            }
        }
    }

    private fun clampAndUpdateOverlayPosition() {
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        clampOverlayPosition(params, root)
        windowManager?.updateViewLayout(root, params)
    }

    private fun clampOverlayPosition(params: WindowManager.LayoutParams, view: View) {
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (dm.heightPixels - view.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun createPillButton(label: String, bgColor: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (8 * density).toInt()
        val shape = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 14 * density
        }
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
        } else {
            shape
        }
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(padH, padV, padH, padV)
            background = bg
            elevation = 4f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun removeOverlay() {
        overlayRoot?.let { windowManager?.removeView(it) }
        overlayRoot = null
        overlayParams = null
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, ScanOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_access_scope)
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
