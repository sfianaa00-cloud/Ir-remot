package com.example.irremote

import android.content.Context
import android.hardware.ConsumerIrManager

enum class Protocol(
    val label: String,
    val carrier: Int,
    val maxAddress: Int,
    val maxCommand: Int
) {
    NEC("NEC", 38000, 0xFF, 0xFF),
    NEC_EXT("NEC extended (16-bit address)", 38000, 0xFFFF, 0xFF),
    SAMSUNG("Samsung32", 38000, 0xFF, 0xFF),
    SONY12("Sony SIRC 12-bit", 40000, 0x1F, 0x7F),
    SONY15("Sony SIRC 15-bit", 40000, 0xFF, 0x7F),
    RC5("Philips RC5", 36000, 0x1F, 0x3F),
    RC6("Philips RC6 (mode 0)", 36000, 0xFF, 0xFF)
}

/** Builds mark/space pulse trains (microseconds, starting with a mark). No Android types, easy to test. */
object Encoders {
    private fun lsb(value: Int, bits: Int): List<Int> = (0 until bits).map { (value shr it) and 1 }
    private fun msb(value: Int, bits: Int): List<Int> = (bits - 1 downTo 0).map { (value shr it) and 1 }
    private fun inv(v: Int) = v.inv() and 0xFF

    fun pattern(p: Protocol, address: Int, command: Int): IntArray {
        val a = address and p.maxAddress
        val c = command and p.maxCommand
        return when (p) {
            Protocol.NEC -> pulseDistance(9000, 4500, lsb(a, 8) + lsb(inv(a), 8) + lsb(c, 8) + lsb(inv(c), 8))
            Protocol.NEC_EXT -> pulseDistance(9000, 4500, lsb(a, 16) + lsb(c, 8) + lsb(inv(c), 8))
            Protocol.SAMSUNG -> pulseDistance(4500, 4500, lsb(a, 8) + lsb(a, 8) + lsb(c, 8) + lsb(inv(c), 8))
            Protocol.SONY12 -> sony(a, c, 5)
            Protocol.SONY15 -> sony(a, c, 8)
            Protocol.RC5 -> rc5(a, c)
            Protocol.RC6 -> rc6(a, c)
        }
    }

    private fun pulseDistance(headMark: Int, headSpace: Int, bits: List<Int>): IntArray {
        val out = ArrayList<Int>()
        out.add(headMark)
        out.add(headSpace)
        for (b in bits) {
            out.add(560)
            out.add(if (b == 1) 1690 else 560)
        }
        out.add(560) // stop bit
        return out.toIntArray()
    }

    /** Sony frames are sent 3 times, 45 ms apart (start to start). */
    private fun sony(address: Int, command: Int, addrBits: Int): IntArray {
        val bits = lsb(command, 7) + lsb(address, addrBits)
        val body = ArrayList<Int>()
        body.add(2400)
        for (b in bits) {
            body.add(600) // space before each bit mark
            body.add(if (b == 1) 1200 else 600)
        }
        val gap = 45000 - body.sum()
        return buildSony(body, gap)
    }

    private fun buildSony(body: List<Int>, gap: Int): IntArray {
        // body = mark, (space, mark)* ; ends with a mark. Frames are joined by a space of `gap`.
        val out = ArrayList<Int>()
        for (i in 0 until 3) {
            out.addAll(body)
            if (i < 2) out.add(gap)
        }
        return out.toIntArray()
    }

    private fun merge(halves: List<Pair<Boolean, Int>>): IntArray {
        val out = ArrayList<Int>()
        var started = false
        var curMark = true
        var cur = 0
        for ((mark, d) in halves) {
            if (!started) {
                if (!mark) continue // patterns must begin with a mark
                started = true; curMark = true; cur = d
            } else if (curMark == mark) {
                cur += d
            } else {
                out.add(cur); curMark = mark; cur = d
            }
        }
        if (started) out.add(cur)
        return out.toIntArray()
    }

    private fun rc5(address: Int, command: Int): IntArray {
        val t = 889
        val bits = listOf(1, 1, 0) + msb(address, 5) + msb(command, 6) // start, field, toggle
        val h = ArrayList<Pair<Boolean, Int>>()
        for (b in bits) {
            if (b == 1) { h.add(false to t); h.add(true to t) } else { h.add(true to t); h.add(false to t) }
        }
        return merge(h)
    }

    private fun rc6(address: Int, command: Int): IntArray {
        val t = 444
        val h = ArrayList<Pair<Boolean, Int>>()
        h.add(true to 6 * t); h.add(false to 2 * t)            // leader
        h.add(true to t); h.add(false to t)                    // start bit (1)
        repeat(3) { h.add(false to t); h.add(true to t) }      // mode 000
        h.add(false to 2 * t); h.add(true to 2 * t)            // trailer / toggle bit (0), double width
        for (b in msb(address, 8) + msb(command, 8)) {
            if (b == 1) { h.add(true to t); h.add(false to t) } else { h.add(false to t); h.add(true to t) }
        }
        return merge(h)
    }
}

class IrSender(context: Context) {
    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean get() = manager?.hasIrEmitter() == true

    fun send(protocol: Protocol, address: Int, command: Int) {
        val m = manager ?: return
        if (!m.hasIrEmitter()) return
        m.transmit(protocol.carrier, Encoders.pattern(protocol, address, command))
    }
}
