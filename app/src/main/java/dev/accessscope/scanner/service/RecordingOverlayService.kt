/**
 * Overlay foreground REC per la registrazione Maestro (Beta).
 *
 * Mostra anteprima YAML live, contatore step, modalità PICK e STOP.
 */
package dev.accessscope.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.MainActivity
import dev.accessscope.scanner.R
import dev.accessscope.scanner.recorder.PickActionKind
import dev.accessscope.scanner.recorder.RecordingLivePreview
import dev.accessscope.scanner.util.AppFileLogger

/**
 * Overlay trascinabile durante la registrazione flussi Maestro.
 */
class RecordingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var yamlView: TextView? = null
    private var stepCountView: TextView? = null
    private var pickBtn: TextView? = null
    private var pauseBtn: TextView? = null
    private var pickKindsRow: LinearLayout? = null
    private var expanded = true

    private val previewReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecordingLivePreview.ACTION_PREVIEW) return
            val snippet = intent.getStringExtra(RecordingLivePreview.EXTRA_YAML_SNIPPET).orEmpty()
            val steps = intent.getIntExtra(RecordingLivePreview.EXTRA_STEP_COUNT, 0)
            val pick = intent.getBooleanExtra(RecordingLivePreview.EXTRA_PICK_MODE, false)
            val paused = intent.getBooleanExtra(RecordingLivePreview.EXTRA_PAUSED, false)
            yamlView?.text = when {
                paused && snippet.isBlank() -> "# In pausa — PICK o riprendi"
                paused -> "# PAUSA\n$snippet"
                else -> snippet.ifBlank { "# in attesa di step…" }
            }
            stepCountView?.text = if (paused) "$steps · PAUSA" else "$steps step"
            pickBtn?.text = if (pick) "PICK ON" else "PICK"
            pauseBtn?.text = if (paused) "RESUME" else "PAUSE"
            pickKindsRow?.visibility = if (pick) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        val filter = IntentFilter(RecordingLivePreview.ACTION_PREVIEW)
        ContextCompat.registerReceiver(
            this,
            previewReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Prima anteprima.
        val app = application as AccessScopeApp
        val st = app.recordingController.state.value
        if (st.isRecording && !st.targetPackage.isNullOrBlank()) {
            RecordingLivePreview.publish(this, st.actions, st.targetPackage!!, st.pickMode, st.isPaused)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecordingNow("notification")
            ACTION_TOGGLE_PICK -> togglePick()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(previewReceiver) }
        removeOverlay()
        super.onDestroy()
    }

    private fun stopRecordingNow(source: String) {
        AppFileLogger.info("RecordingOverlay", "stop_$source")
        (application as AccessScopeApp).stopRecordingSession(save = true)
    }

    private fun togglePick() {
        val ctrl = (application as AccessScopeApp).recordingController
        val next = !ctrl.state.value.pickMode
        ctrl.setPickMode(next)
    }

    private fun togglePause() {
        val ctrl = (application as AccessScopeApp).recordingController
        ctrl.setPaused(!ctrl.state.value.isPaused)
    }

    private fun setPickKind(kind: PickActionKind) {
        val ctrl = (application as AccessScopeApp).recordingController
        ctrl.pickActionKind = kind
        ctrl.setPickMode(true)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pad = (8 * density).toInt()

        val cardBg = GradientDrawable().apply {
            setColor(0xE6121820.toInt())
            setStroke((1 * density).toInt(), 0x55FFFFFF)
            cornerRadius = 14 * density
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(pad, pad, pad, pad)
            elevation = 12f
            minimumWidth = (220 * density).toInt()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dragHandle = TextView(this).apply {
            text = "⋮⋮ REC"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        stepCountView = TextView(this).apply {
            text = "0 step"
            setTextColor(0xAAFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }
        header.addView(dragHandle)
        header.addView(stepCountView)
        root.addView(header)

        val yamlScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (110 * density).toInt(),
            )
        }
        yamlView = TextView(this).apply {
            text = "# in attesa di step…"
            setTextColor(0xFFE2E8F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        yamlScroll.addView(yamlView)
        root.addView(yamlScroll)

        pickKindsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val hsv = HorizontalScrollView(this@RecordingOverlayService)
            // populated below via chips in parent
        }
        val kindsScroll = HorizontalScrollView(this)
        val kindsInner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "tap" to PickActionKind.TAP,
            "assert" to PickActionKind.ASSERT_VISIBLE,
            "input" to PickActionKind.INPUT_TEXT,
            "erase" to PickActionKind.ERASE_TEXT,
            "2×" to PickActionKind.DOUBLE_TAP,
            "long" to PickActionKind.LONG_PRESS,
        ).forEach { (label, kind) ->
            kindsInner.addView(chipButton(label, density) { setPickKind(kind) })
        }
        kindsScroll.addView(kindsInner)
        pickKindsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(kindsScroll)
        }
        root.addView(pickKindsRow)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, 0)
        }
        pickBtn = chipButton("PICK", density, accent = 0xFF0D9488.toInt()) { togglePick() }
        pauseBtn = chipButton("PAUSE", density, accent = 0xFFCA8A04.toInt()) { togglePause() }
        val undoBtn = chipButton("UNDO", density) {
            val ok = (application as? AccessScopeApp)?.recordingController?.undoLastStep() == true
            Toast.makeText(
                this,
                if (ok) "Ultimo step rimosso" else "Niente da annullare",
                Toast.LENGTH_SHORT,
            ).show()
        }
        val optBtn = chipButton("OPT", density, accent = 0xFF7C3AED.toInt()) {
            val ok = (application as? AccessScopeApp)?.recordingController?.markLastTapOptional() == true
            Toast.makeText(
                this,
                if (ok) "Ultimo tap → optional" else "Nessun tap da segnare",
                Toast.LENGTH_SHORT,
            ).show()
        }
        val collapseBtn = chipButton("↕", density) {
            expanded = !expanded
            yamlScroll.visibility = if (expanded) View.VISIBLE else View.GONE
        }
        val stopBtn = chipButton("STOP", density, accent = 0xFFBA1A1A.toInt()) {
            stopRecordingNow("overlay")
        }
        actionsRow.addView(pickBtn)
        actionsRow.addView(pauseBtn)
        actionsRow.addView(undoBtn)
        actionsRow.addView(optBtn)
        actionsRow.addView(collapseBtn)
        actionsRow.addView(stopBtn)
        root.addView(actionsRow)

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            (248 * density).toInt(),
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
            gravity = Gravity.TOP or Gravity.END
            x = (8 * density).toInt()
            y = (100 * density).toInt()
        }

        var downX = 0f
        var downY = 0f
        var paramX = 0
        var paramY = 0
        dragHandle.setOnTouchListener { _, event ->
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
                    params.x = (paramX - dx).coerceIn(0, metrics.widthPixels)
                    params.y = (paramY + dy).coerceIn(0, metrics.heightPixels)
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        overlayRoot = root
        overlayParams = params
        windowManager?.addView(root, params)
    }

    private fun chipButton(
        label: String,
        density: Float,
        accent: Int = 0xFF334155.toInt(),
        onClick: () -> Unit,
    ): TextView {
        val btnBg = GradientDrawable().apply {
            setColor(accent)
            cornerRadius = 20 * density
        }
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), btnBg, null)
            val vPad = (8 * density).toInt()
            val hPad = (12 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.marginEnd = (6 * density).toInt()
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun removeOverlay() {
        overlayRoot?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        overlayParams = null
    }

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Registrazione Maestro",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AccessScope · Maestro (Beta)")
            .setContentText("YAML live + PICK — STOP per salvare")
            .setSmallIcon(R.drawable.ic_access_scope_logo)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "accessscope_recording"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "dev.accessscope.scanner.STOP_RECORDING"
        private const val ACTION_TOGGLE_PICK = "dev.accessscope.scanner.TOGGLE_PICK"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingOverlayService::class.java))
        }
    }
}
