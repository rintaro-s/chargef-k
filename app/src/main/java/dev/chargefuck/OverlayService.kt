package dev.chargefuck

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import java.io.FileDescriptor
import java.io.PrintWriter

class OverlayService : Service() {
    private var mist: MistView? = null
    private lateinit var windows: WindowManager
    private var registered = false
    private var plugged = false
    private val handler = Handler(Looper.getMainLooper())
    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val sample = object : Runnable {
        override fun run() {
            samplePlugState()
            handler.postDelayed(this, 150)
        }
    }

    private fun samplePlugState() {
        val battery = registerReceiver(null, batteryFilter) ?: return
        val next = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (next != plugged) {
            plugged = next
            mist?.engine?.pulse(SystemClock.elapsedRealtime())
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> { mist?.pause(); handler.removeCallbacks(sample) }
                Intent.ACTION_SCREEN_ON -> {
                    mist?.resume()
                    handler.removeCallbacks(sample)
                    handler.post(sample)
                }
                // Read the latest sticky state; a delayed broadcast can contain an older state.
                Intent.ACTION_BATTERY_CHANGED -> samplePlugState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "ピンクのモヤ", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(dev.chargefuck.R.drawable.ic_bolt)
            .setContentTitle("ChargeF**k 起動中")
            .setContentText("抜き差し待ち。タップで開く / 停止でモヤを消す")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification)
        if (mist == null) {
            try {
                val view = MistView(this)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT)
                params.gravity = Gravity.TOP or Gravity.START
                // Android 12+ lets touches through an untrusted overlay only at <= 0.8 opacity.
                params.alpha = 0.78f
                if (Build.VERSION.SDK_INT >= 28) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                windows.addView(view, params)
                mist = view
                val battery = registerReceiver(null, batteryFilter)
                plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
                }
                // Power broadcasts originate from the system, so this receiver must accept system senders.
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
                else registerReceiver(receiver, filter)
                registered = true
                if (!getSystemService(PowerManager::class.java).isInteractive) view.pause()
                else handler.post(sample)
                running = true
            } catch (_: RuntimeException) { stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (registered) unregisterReceiver(receiver)
        mist?.let { it.pause(); windows.removeView(it) }
        mist = null
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        val engine = mist?.engine
        writer.println("running=$running plugged=$plugged heat=${engine?.heat} kick=${engine?.kick} finaleStart=${engine?.finaleStart}")
    }

    companion object {
        const val STOP = "dev.chargefuck.STOP"
        const val CHANNEL = "pink_overlay"
        @Volatile var running = false
    }
}
