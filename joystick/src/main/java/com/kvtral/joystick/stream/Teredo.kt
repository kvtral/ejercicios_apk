package com.kvtral.joystick.stream

import com.kvtral.joystick.xhome.IceCandidate

/**
 * La consola a veces solo anuncia una dirección Teredo (IPv6 2001:0::/32) que no sirve en
 * redes sin IPv6. De ella se pueden sacar sus IPv4 y puertos, y se añaden como candidatos extra.
 */
object Teredo {
    private const val XBOX_GAME_PORT = 3074

    fun expand(candidates: List<IceCandidate>): List<IceCandidate> {
        val out = mutableListOf<IceCandidate>()
        var foundation = candidates.size + 1
        for (c in candidates) {
            out += c
            val tokens = c.candidate.trim().split(Regex("\\s+"))
            for ((i, token) in tokens.withIndex()) {
                val teredo = parse(token) ?: continue
                val port = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 9002
                val hosts = buildList {
                    add(teredo.client to teredo.clientPort)
                    add(teredo.server to port)
                    add(teredo.client to port)
                    if (port != XBOX_GAME_PORT) {
                        add(teredo.server to XBOX_GAME_PORT)
                        add(teredo.client to XBOX_GAME_PORT)
                    }
                }
                for ((ip, p) in hosts) {
                    out += IceCandidate(
                        candidate = "a=candidate:${foundation++} 1 UDP 100 $ip $p typ host ",
                        sdpMid = c.sdpMid,
                        sdpMLineIndex = c.sdpMLineIndex,
                    )
                }
            }
        }
        return out
    }

    data class Address(val server: String, val client: String, val clientPort: Int)

    /** Decodifica 2001:0000:SSSS:SSSS:FFFF:PPPP:CCCC:CCCC (puerto y cliente van invertidos bit a bit). */
    fun parse(address: String): Address? {
        if (!address.contains(':') || !address.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) {
            return null
        }
        val groups = expandIpv6(address) ?: return null
        if (groups[0] != 0x2001 || groups[1] != 0) return null
        fun ipv4(hi: Int, lo: Int) = "${hi shr 8}.${hi and 0xFF}.${lo shr 8}.${lo and 0xFF}"
        return Address(
            server = ipv4(groups[2], groups[3]),
            client = ipv4(groups[6] xor 0xFFFF, groups[7] xor 0xFFFF),
            clientPort = groups[5] xor 0xFFFF,
        )
    }

    private fun expandIpv6(address: String): IntArray? {
        val parts = address.split("::")
        if (parts.size > 2) return null
        val head = parts[0].split(':').filter { it.isNotEmpty() }
        val tail = if (parts.size == 2) parts[1].split(':').filter { it.isNotEmpty() } else emptyList()
        val missing = 8 - head.size - tail.size
        if (missing < 0 || (parts.size == 1 && missing != 0)) return null
        val all = head + List(missing) { "0" } + tail
        return IntArray(8) { all[it].toIntOrNull(16)?.takeIf { v -> v in 0..0xFFFF } ?: return null }
    }
}
