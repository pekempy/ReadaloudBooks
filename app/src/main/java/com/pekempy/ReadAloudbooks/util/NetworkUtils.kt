package com.pekempy.ReadAloudbooks.util

import android.content.Context
import android.net.wifi.WifiManager

object NetworkUtils {
    fun getCurrentSsid(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val info = wifiManager.connectionInfo
                if (info != null && info.supplicantState == android.net.wifi.SupplicantState.COMPLETED) {
                    var ssid = info.ssid
                    if (ssid != null) {
                        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid = ssid.substring(1, ssid.length - 1)
                        }
                        if (ssid == "<unknown ssid>") return null
                        return ssid
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isLocalIp(url: String): Boolean {
        try {
             val cleanUrl = if (!url.startsWith("http")) "http://$url" else url
             val uri = java.net.URI(cleanUrl)
             val host = uri.host ?: return false
             
             if (host == "localhost") return true
             
             // Check for IPv4 pattern
             val parts = host.split(".")
             if (parts.size == 4) {
                 val first = parts[0].toIntOrNull() ?: return false
                 val second = parts[1].toIntOrNull() ?: return false
                 
                 // 10.x.x.x
                 if (first == 10) return true
                 // 192.168.x.x
                 if (first == 192 && second == 168) return true
                 // 172.16.x.x - 172.31.x.x
                 if (first == 172 && (second in 16..31)) return true
             }
        } catch (e: Exception) {
            return false
        }
        return false
    }
}
