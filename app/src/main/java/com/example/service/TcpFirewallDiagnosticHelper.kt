package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated TCP Socket & Firewall Reachability Diagnostic Helper.
 * 
 * Specifically tests raw TCP reachability to target SQL Server IP address and port (default 55200)
 * BEFORE attempting any jTDS JDBC driver handshake.
 * 
 * Provides structured Logcat logs under the tags "PO_SQL_FIREWALL_DIAG" and "PO_SQL_TCP_TEST"
 * to decisively confirm whether a Windows Firewall, router ACL, ISP block, or port forwarding
 * issue is preventing network connectivity.
 */
object TcpFirewallDiagnosticHelper {
    const val TAG_FIREWALL = "PO_SQL_FIREWALL_DIAG"
    const val TAG_TCP = "PO_SQL_TCP_TEST"

    data class TcpReachabilityResult(
        val host: String,
        val port: Int,
        val isReachable: Boolean,
        val latencyMs: Long,
        val resolvedIp: String,
        val statusSummary: String,
        val technicalDetails: String,
        val firewallAssessment: FirewallAssessment,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    )

    enum class FirewallAssessment {
        PORT_OPEN_CONNECTED,
        FIREWALL_TIMEOUT_BLOCKED,
        PORT_CLOSED_CONNECTION_REFUSED,
        DNS_HOST_UNRESOLVABLE,
        NETWORK_UNREACHABLE_OFFLINE,
        UNKNOWN_ERROR
    }

    /**
     * Executes a deep, multi-stage TCP reachability test with detailed Logcat logging.
     * 
     * @param host Target host name or IP address (e.g. "103.164.3.18")
     * @param port Target TCP port (e.g. 55200)
     * @param timeoutMs Timeout in milliseconds for socket connection attempt (default 5000ms)
     */
    suspend fun testTcpReachability(
        host: String,
        port: Int = 55200,
        timeoutMs: Int = 5000
    ): TcpReachabilityResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val startTime = System.currentTimeMillis()

        Log.i(TAG_FIREWALL, "================================================================")
        Log.i(TAG_FIREWALL, "⚡ STARTING TCP FIREWALL & PORT REACHABILITY PRE-FLIGHT TEST")
        Log.i(TAG_FIREWALL, "🎯 Target Destination: $cleanHost:$port (Timeout: ${timeoutMs}ms)")
        Log.i(TAG_FIREWALL, "================================================================")

        // Stage 1: DNS & IP Resolution
        var resolvedIp = ""
        var isDnsOk = false
        val dnsStart = System.currentTimeMillis()
        try {
            Log.d(TAG_TCP, "🔍 Resolving host: '$cleanHost'...")
            val inetAddress = InetAddress.getByName(cleanHost)
            resolvedIp = inetAddress.hostAddress ?: cleanHost
            val dnsLatency = System.currentTimeMillis() - dnsStart
            isDnsOk = true
            Log.i(TAG_FIREWALL, "✓ DNS RESOLUTION SUCCESS: Host '$cleanHost' resolved to IP [$resolvedIp] in ${dnsLatency}ms")
        } catch (e: Exception) {
            val totalLatency = System.currentTimeMillis() - startTime
            Log.e(TAG_FIREWALL, "✕ DNS RESOLUTION FAILED for '$cleanHost': ${e.message}", e)
            Log.e(TAG_FIREWALL, "👉 DIAGNOSIS: The hostname '$cleanHost' could not be resolved. Check device internet connection or domain name.")
            return@withContext TcpReachabilityResult(
                host = cleanHost,
                port = port,
                isReachable = false,
                latencyMs = totalLatency,
                resolvedIp = "Unresolved",
                statusSummary = "DNS Resolution Failed",
                technicalDetails = "Cannot resolve hostname '$cleanHost': ${e.localizedMessage ?: e.javaClass.simpleName}",
                firewallAssessment = FirewallAssessment.DNS_HOST_UNRESOLVABLE
            )
        }

        // Stage 2: Direct Raw TCP Socket Handshake
        Log.d(TAG_TCP, "🔌 Attempting TCP SYN/ACK handshake with [$resolvedIp]:$port...")
        val socket = Socket()
        try {
            // Configure socket parameters
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            val socketAddress = InetSocketAddress(resolvedIp, port)

            val connectStart = System.currentTimeMillis()
            socket.connect(socketAddress, timeoutMs)
            val connectLatency = System.currentTimeMillis() - connectStart
            val totalLatency = System.currentTimeMillis() - startTime

            val localPort = socket.localPort
            val localAddress = socket.localAddress?.hostAddress ?: "local"
            Log.i(TAG_FIREWALL, "✓ TCP SOCKET CONNECTION ESTABLISHED!")
            Log.i(TAG_FIREWALL, "   • Remote Endpoint : [$resolvedIp]:$port")
            Log.i(TAG_FIREWALL, "   • Local Endpoint  : [$localAddress]:$localPort")
            Log.i(TAG_FIREWALL, "   • Handshake Latency: ${connectLatency}ms (Total: ${totalLatency}ms)")
            Log.i(TAG_FIREWALL, "👉 DIAGNOSIS: Firewall is NOT blocking port $port. Port is OPEN and ready for jTDS JDBC connection.")

            try {
                socket.close()
            } catch (ignored: Exception) {}

            return@withContext TcpReachabilityResult(
                host = cleanHost,
                port = port,
                isReachable = true,
                latencyMs = totalLatency,
                resolvedIp = resolvedIp,
                statusSummary = "TCP Port $port is OPEN (${connectLatency}ms)",
                technicalDetails = "Successfully performed TCP 3-way handshake with $resolvedIp:$port in ${connectLatency}ms. No firewall blocks detected.",
                firewallAssessment = FirewallAssessment.PORT_OPEN_CONNECTED
            )

        } catch (e: SocketTimeoutException) {
            val totalLatency = System.currentTimeMillis() - startTime
            Log.e(TAG_FIREWALL, "✕ TCP CONNECTION TIMED OUT after ${totalLatency}ms to [$resolvedIp]:$port", e)
            Log.e(TAG_FIREWALL, "🔥 FIREWALL DETECTION ALERT:")
            Log.e(TAG_FIREWALL, "   • Packets to [$resolvedIp]:$port were silently dropped (no SYN-ACK, no RST received).")
            Log.e(TAG_FIREWALL, "   • CAUSE: A Firewall (Windows Defender Firewall on the SQL Server, AWS/Azure Security Group, or Router) is active and blocking inbound traffic on port $port.")
            Log.e(TAG_FIREWALL, "   • REMEDY: On the SQL Server host machine, open Windows Defender Firewall with Advanced Security -> Inbound Rules -> Create New Rule -> Port -> TCP $port -> Allow the connection.")

            return@withContext TcpReachabilityResult(
                host = cleanHost,
                port = port,
                isReachable = false,
                latencyMs = totalLatency,
                resolvedIp = resolvedIp,
                statusSummary = "FIREWALL BLOCK DETECTED: Connection Timed Out (${totalLatency}ms)",
                technicalDetails = "TCP SYN packets to $resolvedIp:$port were dropped without response. This indicates an active Firewall (Windows Firewall or Cloud Security Group) is blocking TCP port $port.",
                firewallAssessment = FirewallAssessment.FIREWALL_TIMEOUT_BLOCKED
            )

        } catch (e: java.net.ConnectException) {
            val totalLatency = System.currentTimeMillis() - startTime
            val msg = e.message?.lowercase() ?: ""
            val isRefused = msg.contains("refused")

            if (isRefused) {
                Log.e(TAG_FIREWALL, "✕ TCP CONNECTION REFUSED by [$resolvedIp]:$port (${totalLatency}ms)", e)
                Log.e(TAG_FIREWALL, "🔍 SERVICE STATUS ALERT:")
                Log.e(TAG_FIREWALL, "   • The host [$resolvedIp] is online and received the packet, but actively REFUSED the connection (sent TCP RST).")
                Log.e(TAG_FIREWALL, "   • CAUSE: SQL Server instance is either NOT RUNNING, or TCP/IP protocol is disabled in SQL Server Configuration Manager, or it is listening on a different port than $port.")
                Log.e(TAG_FIREWALL, "   • REMEDY: Open 'SQL Server Configuration Manager' -> SQL Server Network Configuration -> Protocols for MSSQLSERVER -> Enable TCP/IP -> Properties -> IPAll -> Set TCP Port to $port -> Restart SQL Server service.")

                return@withContext TcpReachabilityResult(
                    host = cleanHost,
                    port = port,
                    isReachable = false,
                    latencyMs = totalLatency,
                    resolvedIp = resolvedIp,
                    statusSummary = "PORT CLOSED / CONNECTION REFUSED (${totalLatency}ms)",
                    technicalDetails = "Host $resolvedIp is online but actively refused connection on port $port. Verify that SQL Server service is running and TCP/IP is enabled on port $port.",
                    firewallAssessment = FirewallAssessment.PORT_CLOSED_CONNECTION_REFUSED
                )
            } else {
                Log.e(TAG_FIREWALL, "✕ NETWORK UNREACHABLE to [$resolvedIp]:$port: ${e.message}", e)
                return@withContext TcpReachabilityResult(
                    host = cleanHost,
                    port = port,
                    isReachable = false,
                    latencyMs = totalLatency,
                    resolvedIp = resolvedIp,
                    statusSummary = "Network Unreachable (${e.localizedMessage})",
                    technicalDetails = "Could not route packets to $resolvedIp:$port. Check network connectivity.",
                    firewallAssessment = FirewallAssessment.NETWORK_UNREACHABLE_OFFLINE
                )
            }

        } catch (e: Exception) {
            val totalLatency = System.currentTimeMillis() - startTime
            Log.e(TAG_FIREWALL, "✕ UNEXPECTED SOCKET ERROR to [$resolvedIp]:$port: ${e.message}", e)
            return@withContext TcpReachabilityResult(
                host = cleanHost,
                port = port,
                isReachable = false,
                latencyMs = totalLatency,
                resolvedIp = resolvedIp,
                statusSummary = "Socket Error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                technicalDetails = "Socket failure: ${e.message}",
                firewallAssessment = FirewallAssessment.UNKNOWN_ERROR
            )
        } finally {
            try {
                if (!socket.isClosed) socket.close()
            } catch (ignored: Exception) {}
            Log.i(TAG_FIREWALL, "================================================================")
        }
    }
}
