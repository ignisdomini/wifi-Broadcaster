package ru.radioinformator.efir.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

/**
 * Приёмник системных широковещаний Wi-Fi P2P, которые нам интересны.
 *
 * WIFI_P2P_CONNECTION_CHANGED_ACTION намеренно не обрабатывается: приложение
 * никогда не вызывает [WifiP2pManager.connect], поэтому появление группы
 * означало бы, что что-то пошло не так, а не повод на это реагировать.
 */
class EfirBroadcastReceiver(
    private val onStateChanged: (enabled: Boolean) -> Unit,
    private val onDiscoveryChanged: (started: Boolean) -> Unit,
    private val onThisDeviceChanged: (WifiP2pDevice) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED,
                )
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.i(TAG, "Состояние P2P: ${if (enabled) "включено" else "выключено"}")
                onStateChanged(enabled)
            }

            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_DISCOVERY_STATE,
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED,
                )
                val started = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                Log.d(TAG, "Обнаружение ${if (started) "запущено" else "остановлено"}")
                onDiscoveryChanged(started)
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<WifiP2pDevice>(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                )
                if (device != null) {
                    Log.d(TAG, "Это устройство: ${device.deviceName} / ${device.deviceAddress}")
                    onThisDeviceChanged(device)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Efir/Recv"

        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }
}
