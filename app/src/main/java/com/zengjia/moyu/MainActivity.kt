package com.zengjia.moyu

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zengjia.moyu.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var profileStore: ProfileStore
    private var connected = false

    private val importProfile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { profileStore.import(uri) }
            .onSuccess {
                refreshProfile()
                toast("已导入：$it")
            }
            .onFailure { toast(it.message ?: "导入失败") }
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
        else toast("需要 VPN 权限才能连接")
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(MoyuVpnService.EXTRA_STATE)) {
                "CONNECTING" -> setStatus("正在连接…", false)
                "CONNECTED" -> setStatus("已连接", true)
                "DISCONNECTED" -> setStatus("未连接", false)
                "ERROR" -> {
                    setStatus("连接失败", false)
                    toast(intent.getStringExtra(MoyuVpnService.EXTRA_MESSAGE) ?: "连接失败")
                }
                "TRAFFIC" -> {
                    binding.upSpeedText.text = TrafficFormatter.speed(intent.getLongExtra(MoyuVpnService.EXTRA_UP, 0))
                    binding.downSpeedText.text = TrafficFormatter.speed(intent.getLongExtra(MoyuVpnService.EXTRA_DOWN, 0))
                    val tu = TrafficFormatter.bytes(intent.getLongExtra(MoyuVpnService.EXTRA_TOTAL_UP, 0))
                    val td = TrafficFormatter.bytes(intent.getLongExtra(MoyuVpnService.EXTRA_TOTAL_DOWN, 0))
                    binding.totalText.text = "本次流量：↓ $td  ↑ $tu"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profileStore = ProfileStore(this)

        refreshProfile()
        binding.importButton.setOnClickListener { importProfile.launch(arrayOf("application/x-yaml", "text/yaml", "text/plain", "*/*")) }
        binding.connectButton.setOnClickListener {
            if (connected) disconnect() else connect()
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MoyuVpnService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(receiver) }
        super.onStop()
    }

    private fun refreshProfile() {
        binding.profileText.text = profileStore.displayName()
    }

    private fun connect() {
        if (!profileStore.hasProfile()) {
            toast("请先导入 YAML 配置")
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else startVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, MoyuVpnService::class.java).apply {
            action = MoyuVpnService.ACTION_CONNECT
            putExtra(MoyuVpnService.EXTRA_PROFILE, profileStore.activeFile.absolutePath)
        }
        ContextCompat.startForegroundService(this, intent)
        setStatus("正在连接…", false)
    }

    private fun disconnect() {
        startService(Intent(this, MoyuVpnService::class.java).apply { action = MoyuVpnService.ACTION_DISCONNECT })
    }

    private fun setStatus(text: String, isConnected: Boolean) {
        connected = isConnected
        binding.statusText.text = text
        binding.connectButton.text = if (isConnected) "断开 VPN" else "连接 VPN"
        if (!isConnected && text != "正在连接…") {
            binding.upSpeedText.text = "0 B/s"
            binding.downSpeedText.text = "0 B/s"
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
