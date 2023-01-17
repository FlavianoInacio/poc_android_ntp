package com.flaviano.pocntpserver.data.sntp

import android.os.SystemClock
import android.util.Log
import com.flaviano.pocntpserver.data.exception.InvalidNtpServerResponseException
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.experimental.and
import kotlin.math.abs

class SNTPClient {

    private val _cachedDeviceUptime = AtomicLong()
    private val _cachedSntpTime = AtomicLong()
    private val _sntpInitialized = AtomicBoolean(false)


    fun getRoundTripDelay(response: LongArray): Long {
        return response[RESPONSE_INDEX_RESPONSE_TIME] - response[RESPONSE_INDEX_ORIGINATE_TIME] -
                (response[RESPONSE_INDEX_TRANSMIT_TIME] - response[RESPONSE_INDEX_RECEIVE_TIME])
    }


    private fun getClockOffset(response: LongArray): Long {
        return (response[RESPONSE_INDEX_RECEIVE_TIME] - response[RESPONSE_INDEX_ORIGINATE_TIME] +
                (response[RESPONSE_INDEX_TRANSMIT_TIME] - response[RESPONSE_INDEX_RESPONSE_TIME])) / 2
    }

    /**
     * Sends an NTP request to the given host and processes the response.
     *
     * @param ntpHost           host name of the server.
     */
    @Synchronized
    @Throws(IOException::class, InvalidNtpServerResponseException::class)
    fun requestTime(
        ntpHost: String,
        rootDelayMax: Float,
        rootDispersionMax: Float,
        serverResponseDelayMax: Int,
        timeoutInMillis: Int
    ): LongArray {
        var socket: DatagramSocket? = null
        return try {
            val buffer = ByteArray(NTP_PACKET_SIZE)
            val address = InetAddress.getByName(ntpHost)
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            writeVersion(buffer)

            // -----------------------------------------------------------------------------------
            // get current time and write it to the request packet
            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            writeTimeStamp(buffer, INDEX_TRANSMIT_TIME, requestTime)
            socket = DatagramSocket()
            socket.soTimeout = timeoutInMillis
            socket.send(request)

            // -----------------------------------------------------------------------------------
            // read the response
            val t = LongArray(RESPONSE_INDEX_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val responseTicks = SystemClock.elapsedRealtime()
            t[RESPONSE_INDEX_RESPONSE_TICKS] = responseTicks

            // -----------------------------------------------------------------------------------
            // extract the results
            val originateTime = readTimeStamp(buffer, INDEX_ORIGINATE_TIME) // T0
            val receiveTime = readTimeStamp(buffer, INDEX_RECEIVE_TIME) // T1
            val transmitTime = readTimeStamp(buffer, INDEX_TRANSMIT_TIME) // T2
            val responseTime = requestTime + (responseTicks - requestTicks) // T3
            t[RESPONSE_INDEX_ORIGINATE_TIME] = originateTime
            t[RESPONSE_INDEX_RECEIVE_TIME] = receiveTime
            t[RESPONSE_INDEX_TRANSMIT_TIME] = transmitTime
            t[RESPONSE_INDEX_RESPONSE_TIME] = responseTime

            // -----------------------------------------------------------------------------------
            // check validity of response
            t[RESPONSE_INDEX_ROOT_DELAY] = read(buffer, INDEX_ROOT_DELAY)
            val rootDelay = doubleMillis(t[RESPONSE_INDEX_ROOT_DELAY])
            if (rootDelay > rootDelayMax) {
                throw InvalidNtpServerResponseException(
                    "Invalid response from NTP server. %s violation. %f [actual] > %f [expected]",
                    "root_delay", rootDelay.toFloat(),
                    rootDelayMax
                )
            }
            t[RESPONSE_INDEX_DISPERSION] = read(buffer, INDEX_ROOT_DISPERSION)
            val rootDispersion = doubleMillis(t[RESPONSE_INDEX_DISPERSION])
            if (rootDispersion > rootDispersionMax) {
                throw InvalidNtpServerResponseException(
                    "Invalid response from NTP server. %s violation. %f [actual] > %f [expected]",
                    "root_dispersion", rootDispersion.toFloat(),
                    rootDispersionMax
                )
            }
            val mode: Byte = (buffer[0] and 0x7)
            if (mode.toInt() != 4 && mode.toInt() != 5) {
                throw InvalidNtpServerResponseException("untrusted mode value for TrueTime: $mode")
            }
            val stratum: Int = buffer[1].toInt() and 0xff
            t[RESPONSE_INDEX_STRATUM] = stratum.toLong()
            if (stratum < 1 || stratum > 15) {
                throw InvalidNtpServerResponseException("untrusted stratum value for TrueTime: $stratum")
            }
            val leap: Byte = (buffer[0].toInt() shr 6 and 0x3).toByte()
            if (leap.toInt() == 3) {
                throw InvalidNtpServerResponseException("unsynchronized server responded for TrueTime")
            }
            val delay =
                abs(responseTime - originateTime - (transmitTime - receiveTime)).toDouble()
            if (delay >= serverResponseDelayMax) {
                throw InvalidNtpServerResponseException(
                    "%s too large for comfort %f [actual] >= %f [expected]",
                    "server_response_delay", delay.toFloat(),
                    serverResponseDelayMax.toFloat()
                )
            }
            val timeElapsedSinceRequest = abs(originateTime - System.currentTimeMillis())
            if (timeElapsedSinceRequest >= 10000) {
                throw InvalidNtpServerResponseException(
                    "Request was sent more than 10 seconds back " +
                            timeElapsedSinceRequest
                )
            }
            _sntpInitialized.set(true)
            Log.i(TAG, "---- SNTP successful response from $ntpHost")

            // -----------------------------------------------------------------------------------
            // TODO:
            cacheTrueTimeInfo(t)
            t
        } catch (e: Exception) {
            Log.d(TAG, "---- SNTP request failed for $ntpHost")
            throw e
        } finally {
            socket?.close()
        }
    }

    private fun cacheTrueTimeInfo(response: LongArray) {
        _cachedSntpTime.set(sntpTime(response))
        _cachedDeviceUptime.set(response[RESPONSE_INDEX_RESPONSE_TICKS])
    }

    private fun sntpTime(response: LongArray): Long {
        val clockOffset = getClockOffset(response)
        val responseTime = response[RESPONSE_INDEX_RESPONSE_TIME]
        return responseTime + clockOffset
    }

    fun wasInitialized(): Boolean {
        return _sntpInitialized.get()
    }

    /**
     * @return time value computed from NTP server response
     */
    fun getCachedSNTPTime(): Long {
        return _cachedSntpTime.get()
    }

    /**
     * @return device uptime computed at time of executing the NTP request
     */
    fun getCachedDeviceUptime(): Long {
        return _cachedDeviceUptime.get()
    }

    // -----------------------------------------------------------------------------------
    // private helpers

    // -----------------------------------------------------------------------------------
    // private helpers
    /**
     * Writes NTP version as defined in RFC-1305
     */
    private fun writeVersion(buffer: ByteArray) {
        // mode is in low 3 bits of first byte
        // version is in bits 3-5 of first byte
        buffer[INDEX_VERSION] = (NTP_MODE or (NTP_VERSION shl 3)).toByte()
    }

    /**
     * Writes system time (milliseconds since January 1, 1970)
     * as an NTP time stamp as defined in RFC-1305
     * at the given offset in the buffer
     */
    private fun writeTimeStamp(buffer: ByteArray, offsetParam: Int, time: Long) {
        var offset = offsetParam
        var seconds = time / 1000L
        val milliseconds = time - seconds * 1000L

        // consider offset for number of seconds
        // between Jan 1, 1900 (NTP epoch) and Jan 1, 1970 (Java epoch)
        seconds += OFFSET_1900_TO_1970

        // write seconds in big endian format
        buffer[offset++] = (seconds shr 24).toByte()
        buffer[offset++] = (seconds shr 16).toByte()
        buffer[offset++] = (seconds shr 8).toByte()
        buffer[offset++] = (seconds shr 0).toByte()
        val fraction = milliseconds * 0x100000000L / 1000L

        // write fraction in big endian format
        buffer[offset++] = (fraction shr 24).toByte()
        buffer[offset++] = (fraction shr 16).toByte()
        buffer[offset++] = (fraction shr 8).toByte()

        // low order bits should be random data
        buffer[offset++] = (Math.random() * 255.0).toInt().toByte()
    }

    /**
     * @param offset offset index in buffer to start reading from
     * @return NTP timestamp in Java epoch
     */
    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read(buffer, offset)
        val fraction = read(buffer, offset + 4)
        return (seconds - OFFSET_1900_TO_1970) * 1000 + fraction * 1000L / 0x100000000L
    }

    /**
     * Reads an unsigned 32 bit big endian number
     * from the given offset in the buffer
     *
     * @return 4 bytes as a 32-bit long (unsigned big endian)
     */
    private fun read(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset]
        val b1 = buffer[offset + 1]
        val b2 = buffer[offset + 2]
        val b3 = buffer[offset + 3]
        return (ui(b0).toLong() shl 24) +
                (ui(b1).toLong() shl 16) +
                (ui(b2).toLong() shl 8) + ui(b3).toLong()
    }

    /***
     * Convert (signed) byte to an unsigned int
     *
     * Java only has signed types so we have to do
     * more work to get unsigned ops
     *
     * @param b input byte
     * @return unsigned int value of byte
     */
    private fun ui(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    /**
     * Used for root delay and dispersion
     *
     * According to the NTP spec, they are in the NTP Short format
     * viz. signed 16.16 fixed point
     *
     * @param fix signed fixed point number
     * @return as a double in milliseconds
     */
    private fun doubleMillis(fix: Long): Double {
        return fix / 65.536
    }

    companion object {
        const val RESPONSE_INDEX_ORIGINATE_TIME = 0
        const val RESPONSE_INDEX_RECEIVE_TIME = 1
        const val RESPONSE_INDEX_TRANSMIT_TIME = 2
        const val RESPONSE_INDEX_RESPONSE_TIME = 3
        const val RESPONSE_INDEX_ROOT_DELAY = 4
        const val RESPONSE_INDEX_DISPERSION = 5
        const val RESPONSE_INDEX_STRATUM = 6
        const val RESPONSE_INDEX_RESPONSE_TICKS = 7
        const val RESPONSE_INDEX_SIZE = 8

        private val TAG = SNTPClient::class.java.simpleName

        private const val NTP_PORT = 123
        private const val NTP_MODE = 3
        private const val NTP_VERSION = 3
        private const val NTP_PACKET_SIZE = 48

        private const val INDEX_VERSION = 0
        private const val INDEX_ROOT_DELAY = 4
        private const val INDEX_ROOT_DISPERSION = 8
        private const val INDEX_ORIGINATE_TIME = 24
        private const val INDEX_RECEIVE_TIME = 32
        private const val INDEX_TRANSMIT_TIME = 40

        // 70 years plus 17 leap days
        private const val OFFSET_1900_TO_1970 = (365L * 70L + 17L) * 24L * 60L * 60L
    }
}