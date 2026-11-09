package com.example.api

import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address

object Ipv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val ipv4Only = addresses.filterIsInstance<Inet4Address>()
            if (ipv4Only.isNotEmpty()) {
                ipv4Only
            } else {
                addresses
            }
        } catch (e: Exception) {
            try {
                Dns.SYSTEM.lookup(hostname)
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }
}
