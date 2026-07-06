package dev.accessscope.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.accessscope.scanner.AccessScopeApp
import dev.accessscope.scanner.MainActivity
import dev.accessscope.scanner.R
import dev.accessscope.scanner.data.ScanSessionRepository

class ScanOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private val repository: ScanSessionRepository
        get() = (application as AccessScopeApp).scanRepository

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repository.requestStopFromOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScanOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScanOverlayService)
            setContent {
                OverlayStopButton(
                    onStop = {
                        repository.requestStopFromOverlay()
                    }
                )
            }
        }
        overlayView = composeView

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        windowManager?.addView(composeView, layoutParams)
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "access_scope_scan"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "dev.accessscope.scanner.STOP_SCAN"

        fun start(context: Context) {
            val intent = Intent(context, ScanOverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanOverlayService::class.java))
        }
    }
}

@Composable
private fun OverlayStopButton(onStop: () -> Unit) {
    Button(
        onClick = onStop,
        modifier = Modifier
            .background(Color.Transparent)
            .padding(4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC62828),
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
    ) {
        Text(text = "STOP", fontSize = 14.sp)
    }
}
