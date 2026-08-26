package ru.radioinformator.efir.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Всё, чего платформа требует, прежде чем показать объявление соседа.
 * Правила менялись дважды (API 23 и API 33), поэтому проверки собраны в одном
 * месте, а не размазаны по движку.
 */
object EfirPermissions {

    /** Разрешения, запрашиваемые в рантайме. До API 23 список пуст. */
    fun required(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        // API 21..22 — разрешения выдаются при установке.
        else -> emptyList()
    }

    fun allGranted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Всё, что спрашиваем при первом запуске: к необходимому добавляется
     * уведомление службы приёма.
     *
     * Отдельно от [required] намеренно: без уведомления приём работает, просто
     * молча, поэтому его отсутствие не повод считать окружение непригодным.
     */
    fun requestedAtStart(): List<String> = buildList {
        addAll(required())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Экономия батареи — главная причина, по которой приём в кармане всё-таки
     * замолкает. Особенно на прошивках Xiaomi, где к системному ограничению
     * добавлены свои. Отправляем человека туда, где это снимается.
     */
    fun batteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Экран «работа в фоне» для нашего приложения. Просить исключение
     * напрямую (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) нельзя: Google Play
     * такое запрещает всем, кроме считаных категорий, а мы в них не входим.
     * Поэтому открываем настройки и объясняем словами, что там нажать.
     */
    fun batterySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * До API 33 сканирование Wi-Fi гасит *системный тумблер геолокации*, помимо
     * выданного разрешения. Выданный ACCESS_FINE_LOCATION при выключенной
     * геолокации даёт обнаружение, которое стартует успешно и молчит вечно —
     * самая непонятная поломка во всём этом API.
     */
    fun locationServicesRequired(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun locationServicesEnabled(context: Context): Boolean {
        if (!locationServicesRequired()) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun wifiSettingsIntent(): Intent =
        Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
