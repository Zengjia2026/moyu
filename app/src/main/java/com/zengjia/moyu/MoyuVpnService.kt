package com.zengjia.moyu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.oviron.libmihomo.Clash
import io.github.oviron.libmihomo.TunInterface
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MoyuVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.zengjia.moyu.CONNECT"
        const val ACTION_DISCONNECT = "com.zengjia.moyu.DISCONNECT"
        const val ACTION_STATE = "com.zengjia.moyu.STATE"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_UP = "up"
        const val EXTRA_DOWN = "down"
        const val EXTRA_TOTAL_UP = "total_up"
        const val EXTRA_TOTAL_DOWN = "total_down"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1001
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private var trafficThread: Thread? = null

    private val tunInterface = object : TunInterface {
        override fun protect(fd: Int) {
            this@MoyuVpnService.protect(fd)
        }

        override fun resolverProcess(protocol: Int, source: String, target: String, uid: Int): String = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> executor.execute { stopVpn("已断开") }
            ACTION_CONNECT -> {
                startForegroundNow("正在连接…")
                val profile = intent.getStringExtra(EXTRA_PROFILE)
                executor.execute {
                    if (profile.isNullOrBlank()) {
                        fail("没有可用的配置文件")
                    } else {
                        startVpn(profile)
                    }
                }
            }
        }
        return Service.START_STICKY
    }

    private fun startVpn(profilePath: String) {
        try {
            if (running.get()) stopVpn("重新连接")
            sendState("CONNECTING", "正在加载配置…")

            Clash.load(applicationInfo.nativeLibraryDir)
            Clash.assertReady()

            // libmihomo v0.3.7 固定从 <home-dir>/config.yaml 读取配置。
            // 兼容旧版本已经导入到 files/profiles/active.yaml 的配置文件。
            val sourceProfile = File(profilePath)
            require(sourceProfile.isFile && sourceProfile.length() > 0) { "配置文件不存在或为空" }
            val mihomoConfig = File(filesDir, "config.yaml")
            if (sourceProfile.canonicalPath != mihomoConfig.canonicalPath) {
                sourceProfile.copyTo(mihomoConfig, overwrite = true)
            }

            val setupLatch = CountDownLatch(1)
            var setupError: String? = null
            val initParams = JSONObject()
                .put("home-dir", filesDir.absolutePath)
                .put("version", Build.VERSION.SDK_INT)
                .toString()

            Clash.quickSetup(
                initParams = initParams,
                setupParams = "{}"
            ) { result ->
                setupError = result?.takeIf { it.isNotBlank() }
                setupLatch.countDown()
            }
            if (!setupLatch.await(15, TimeUnit.SECONDS)) error("Mihomo 初始化超时")
            setupError?.let { error("配置加载失败：$it") }

            val builder = Builder()
                .setSession("摸鱼 VPN")
                .setMtu(1400)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")

            tun = builder.establish() ?: error("系统 VPN 建立失败")

            Clash.startTUN(
                fd = tun!!.fd,
                cb = tunInterface,
                device = "moyu-vpn",
                stack = "system",
                address = "172.19.0.1/30",
                dns = "1.1.1.1,8.8.8.8",
                mtu = 1400
            )

            running.set(true)
            startTrafficLoop()
            updateNotification("VPN 已连接")
            sendState("CONNECTED", "已连接")
        } catch (t: Throwable) {
            Log.e("MoyuVpnService", "startVpn failed", t)
            fail(t.message ?: "连接失败")
        }
    }

    private fun startTrafficLoop() {
        trafficThread?.interrupt()
        trafficThread = Thread {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val now = JSONObject(Clash.getTraffic())
                    val total = JSONObject(Clash.getTotalTraffic())
                    sendTraffic(
                        up = now.optLong("up"),
                        down = now.optLong("down"),
                        totalUp = total.optLong("up"),
                        totalDown = total.optLong("down")
                    )
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    Log.w("MoyuVpnService", "traffic read failed", t)
                    try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                }
            }
        }.apply { name = "moyu-traffic"; start() }
    }

    private fun stopVpn(message: String) {
        running.set(false)
        trafficThread?.interrupt()
        trafficThread = null
        runCatching { if (Clash.isLoaded()) Clash.stopTun() }
        runCatching { tun?.close() }
        tun = null
        sendState("DISCONNECTED", message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        running.set(false)
        runCatching { if (Clash.isLoaded()) Clash.stopTun() }
        runCatching { tun?.close() }
        tun = null
        sendState("ERROR", message)
        updateNotification("连接失败：$message")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        executor.execute { stopVpn("VPN 权限已被系统撤销") }
        super.onRevoke()
    }

    override fun onDestroy() {
        running.set(false)
        trafficThread?.interrupt()
        runCatching { if (Clash.isLoaded()) Clash.stopTun() }
        runCatching { tun?.close() }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun sendState(state: String, message: String) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_MESSAGE, message)
        })
    }

    private fun sendTraffic(up: Long, down: Long, totalUp: Long, totalDown: Long) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, "TRAFFIC")
            putExtra(EXTRA_UP, up)
            putExtra(EXTRA_DOWN, down)
            putExtra(EXTRA_TOTAL_UP, totalUp)
            putExtra(EXTRA_TOTAL_DOWN, totalDown)
        })
    }

    private fun startForegroundNow(text: String) {
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(text))
    }

    private fun updateNotification(text: String) {
        createChannel()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("摸鱼 VPN")
        .setContentText(text)
        .setOngoing(running.get())
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN 连接状态", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
