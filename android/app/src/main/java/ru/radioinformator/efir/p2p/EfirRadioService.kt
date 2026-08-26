package ru.radioinformator.efir.p2p

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import ru.radioinformator.efir.EfirRadio
import ru.radioinformator.efir.MainActivity
import ru.radioinformator.efir.R
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.net.EfirPrefs

/**
 * Служба, которая не даёт эфиру замолкнуть при погасшем экране.
 *
 * Задача у неё узкая и вся про операционную систему, а не про радио: сам
 * движок [EfirRadio] работает и без неё, но через несколько минут после
 * выключения экрана Android усыпляет процесс, и обнаружение служб перестаёт
 * приносить что-либо. Для радиоинформатора это худший из возможных моментов:
 * телефон лежит в кармане, а объявление соседа как раз тогда и приходит.
 *
 * Держится тремя вещами:
 *
 *  - переднеплановой службой с уведомлением — процесс не выгружают;
 *  - WifiLock в режиме высокой производительности — модуль Wi-Fi не уходит
 *    в энергосбережение, где сканирование фактически не работает;
 *  - частичным wake lock — иначе таймеры корутин не срабатывают в глубоком
 *    сне и круги обнаружения не запускаются.
 *
 * Последнее стоит батареи, поэтому его можно отключить в настройках: тогда
 * приём в кармане будет не постоянным, а урывками, зато телефон проживёт
 * дольше. Уведомление убрать нельзя — этого не позволяет ни одна версия
 * Android, и правильно: незаметно жечь чужую батарею приложение не должно.
 */
class EfirRadioService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Кнопка «Стоп» в шторке: снимаем всё с эфира и уходим.
            EfirRadio.get(this).shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        val radio = EfirRadio.get(this)
        startForegroundSafely(buildNotification(radio.uiState.value))

        // Ничего не работает — значит служба уже не нужна: так бывает, когда
        // приём выключили, пока команда на запуск ещё летела.
        if (!radio.isWorking) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireLocks()

        // START_STICKY: если система всё-таки выгрузит процесс под нехваткой
        // памяти, службу поднимут заново, и приём вернётся сам.
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    /**
     * Систему интересует не только уведомление, но и тип службы: с Android 14
     * без него запуск падает с исключением. «Подключённое устройство» — самое
     * близкое из существующих: мы обмениваемся с техникой поблизости.
     */
    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "Не удалось выйти на передний план", it) }
    }

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiLock == null && wifi != null) {
            // HIGH_PERF, а не обычный: в экономном режиме модуль просыпается
            // редко, и чужое объявление успевает пройти мимо.
            wifiLock = runCatching {
                wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
                    .apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        }

        val keepAwake = EfirPrefs(applicationContext).keepAwake
        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (keepAwake && wakeLock == null && power != null) {
            wakeLock = runCatching {
                power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    .apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        } else if (!keepAwake) {
            wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
            wakeLock = null
        }
    }

    private fun releaseLocks() {
        wifiLock?.let { runCatching { if (it.isHeld) it.release() } }
        wifiLock = null
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        // Низкая важность: уведомление должно быть видно, но не звенеть —
        // это индикатор работы, а не сообщение.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Приём эфира",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Показывает, что телефон слушает эфир и что сейчас в нём висит."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: EfirUiState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, EfirRadioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(state))
            .setContentText(details(state))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details(state)))
            .setContentIntent(open)
            .addAction(0, "Выключить", stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun title(state: EfirUiState): String = when {
        state.isScanning && state.isBroadcasting -> "ЭФИР: приём и передача"
        state.isScanning -> "ЭФИР: идёт приём"
        state.isBroadcasting -> "ЭФИР: сообщение в эфире"
        state.schedule.enabled -> "ЭФИР: расписание ждёт"
        else -> "ЭФИР"
    }

    /** Что именно происходит: какие каналы слушаем и что висит в эфире. */
    private fun details(state: EfirUiState): String {
        val parts = mutableListOf<String>()

        if (state.isScanning) {
            val channels = state.listenChannels.sorted()
            val listed = when {
                channels.size >= EfirPrefs.CHANNEL_MAX -> "все каналы"
                channels.size <= 3 -> channels.joinToString(", ") { "к$it ${state.channelTitle(it)}" }
                else -> "каналы " + channels.joinToString(", ") { "к$it" }
            }
            parts += "Слушаем: $listed"
        }

        state.advertisedText?.takeIf { it.isNotBlank() }?.let { parts += "В эфире: $it" }
        state.directRecipient?.let { parts += "Личное для $it" }

        if (state.schedule.enabled && state.advertisedText.isNullOrBlank()) {
            val count = state.schedule.rules.size
            parts += "Расписание включено: правил $count"
        }

        if (parts.isEmpty()) parts += "Ожидание"
        return parts.joinToString("\n")
    }

    companion object {
        private const val TAG = "Efir/Service"
        private const val CHANNEL_ID = "efir_radio"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "ru.radioinformator.efir.STOP"
        private const val WIFI_LOCK_TAG = "efir:wifi"
        private const val WAKE_LOCK_TAG = "efir:radio"

        fun start(context: Context) {
            val intent = Intent(context, EfirRadioService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Не удалось запустить службу приёма", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, EfirRadioService::class.java)) }
        }

        /**
         * Обновляет уведомление под текущее состояние. Отдельный запуск службы
         * не нужен: если она не работает, обновлять нечего.
         */
        fun refresh(context: Context, state: EfirUiState) {
            if (!state.isScanning && !state.isBroadcasting && !state.schedule.enabled) return
            start(context)
        }
    }
}
