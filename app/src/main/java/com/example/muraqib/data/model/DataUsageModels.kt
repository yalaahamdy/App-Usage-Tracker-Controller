package com.example.muraqib.data.model

import android.graphics.drawable.Drawable
import java.util.Locale

/**
 * فلترة نوع الشبكة المستهلكة
 */
enum class NetworkTypeFilter(val titleAr: String) {
    ALL("الكل"),
    MOBILE("بيانات الجوال"),
    WIFI("واي فاي")
}

/**
 * تنسيق حجم البيانات بالبايت إلى صيغة عربية مفهومة
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 بايت"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0

    return when {
        tb >= 1.0 -> "%.2f تيرابايت".format(Locale.getDefault(), tb)
        gb >= 1.0 -> "%.2f جيجابايت".format(Locale.getDefault(), gb)
        mb >= 1.0 -> "%.1f ميجابايت".format(Locale.getDefault(), mb)
        kb >= 1.0 -> "%.0f كيلوبايت".format(Locale.getDefault(), kb)
        else -> "$bytes بايت"
    }
}

/**
 * بيانات استهلاك الإنترنت لتطبيق معين
 */
data class DataUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val percentageOfTotal: Float = 0f
) {
    val wifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileBytes: Long get() = mobileRxBytes + mobileTxBytes
    val totalBytes: Long get() = wifiBytes + mobileBytes
    val totalRxBytes: Long get() = wifiRxBytes + mobileRxBytes
    val totalTxBytes: Long get() = wifiTxBytes + mobileTxBytes

    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedWifi: String get() = formatBytes(wifiBytes)
    val formattedMobile: String get() = formatBytes(mobileBytes)
    val formattedRx: String get() = formatBytes(totalRxBytes)
    val formattedTx: String get() = formatBytes(totalTxBytes)

    fun getBytesByFilter(filter: NetworkTypeFilter): Long {
        return when (filter) {
            NetworkTypeFilter.ALL -> totalBytes
            NetworkTypeFilter.MOBILE -> mobileBytes
            NetworkTypeFilter.WIFI -> wifiBytes
        }
    }

    fun getFormattedByFilter(filter: NetworkTypeFilter): String {
        return formatBytes(getBytesByFilter(filter))
    }
}

/**
 * ملخص استهلاك الإنترنت الشامل للجهاز خلال فترة محددة
 */
data class DataUsageOverview(
    val period: UsagePeriod,
    val totalWifiRxBytes: Long = 0L,
    val totalWifiTxBytes: Long = 0L,
    val totalMobileRxBytes: Long = 0L,
    val totalMobileTxBytes: Long = 0L,
    val appList: List<DataUsageInfo> = emptyList()
) {
    val totalWifiBytes: Long get() = totalWifiRxBytes + totalWifiTxBytes
    val totalMobileBytes: Long get() = totalMobileRxBytes + totalMobileTxBytes
    val totalBytes: Long get() = totalWifiBytes + totalMobileBytes
    val totalRxBytes: Long get() = totalWifiRxBytes + totalMobileRxBytes
    val totalTxBytes: Long get() = totalWifiTxBytes + totalMobileTxBytes

    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedWifi: String get() = formatBytes(totalWifiBytes)
    val formattedMobile: String get() = formatBytes(totalMobileBytes)
    val formattedRx: String get() = formatBytes(totalRxBytes)
    val formattedTx: String get() = formatBytes(totalTxBytes)

    val wifiRatio: Float get() = if (totalBytes > 0) (totalWifiBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val mobileRatio: Float get() = if (totalBytes > 0) (totalMobileBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    fun getFilteredAppList(filter: NetworkTypeFilter): List<DataUsageInfo> {
        val list = when (filter) {
            NetworkTypeFilter.ALL -> appList.filter { it.totalBytes > 0 }
            NetworkTypeFilter.MOBILE -> appList.filter { it.mobileBytes > 0 }
            NetworkTypeFilter.WIFI -> appList.filter { it.wifiBytes > 0 }
        }
        return list.sortedByDescending { it.getBytesByFilter(filter) }
    }
}
