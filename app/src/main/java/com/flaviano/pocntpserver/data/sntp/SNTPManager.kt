package com.flaviano.pocntpserver.data.sntp

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.Date


class SNTPManager {

    @Throws(IOException::class)
    fun initialize() {
        initialize(NTP_HOST)
    }

    @Throws(IOException::class)
    private fun initialize(ntpHost: String) {
        if (isInitialized()) {
            Log.d(TAG, "---- SNTP_Manager already initialized from previous boot/init")
            return
        }
        requestTime(ntpHost)
    }

    @Throws(IOException::class)
    fun requestTime(ntpHost: String): LongArray {
        return SNTP_CLIENT.requestTime(
            ntpHost,
            ROOT_DELAY_MAX,
            ROOT_DISPERSION_MAX,
            SERVER_RESPONSE_DELAY_MAX,
            UDP_SOCKET_TIME_OUT_MILLIS
        )
    }

    @Synchronized
    fun withNtpHost(ntpHost: String): SNTPManager {
        NTP_HOST = ntpHost
        return INSTANCE
    }

    @Synchronized
    fun withConnectionTimeout(timeoutInMillis: Int): SNTPManager {
        UDP_SOCKET_TIME_OUT_MILLIS = timeoutInMillis
        return INSTANCE
    }

    companion object {

        private val TAG = SNTPManager::class.java.simpleName

        private val INSTANCE = SNTPManager()
        private val SNTP_CLIENT = SNTPClient()

        private const val ROOT_DELAY_MAX = 100f
        private const val ROOT_DISPERSION_MAX = 100f
        private const val SERVER_RESPONSE_DELAY_MAX = 750

        private var UDP_SOCKET_TIME_OUT_MILLIS = 30000
        private var NTP_HOST = "1.us.pool.ntp.org"


        fun build(): SNTPManager {
            return INSTANCE
        }

        fun isInitialized(): Boolean {
            return SNTP_CLIENT.wasInitialized()
        }

        private fun getCachedSNTPTime(): Long =
            if (SNTP_CLIENT.wasInitialized()) SNTP_CLIENT.getCachedSNTPTime() else throw RuntimeException(
                "expected SNTP time from last boot to be cached. couldn't find it."
            )

        private fun getCachedDeviceUptime(): Long =
            if (SNTP_CLIENT.wasInitialized()) SNTP_CLIENT.getCachedDeviceUptime() else throw RuntimeException(
                "expected device time from last boot to be cached. couldn't find it."
            )

        fun now(): Date {
            check(isInitialized()) { "You need to call init() on SNTP_Manager at least once." }
            val cachedSNTPTime = getCachedSNTPTime()
            val cachedDeviceUptime = getCachedDeviceUptime()
            val deviceUptime = SystemClock.elapsedRealtime()
            val now = cachedSNTPTime + (deviceUptime - cachedDeviceUptime)
            return Date(now)
        }

    }
}