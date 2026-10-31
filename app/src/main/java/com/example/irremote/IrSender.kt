package com.example.irremote

import android.content.Context
import android.hardware.ConsumerIrManager

/** Builds NEC-protocol pulse trains. Kept free of Android types so it is easy to test. */
object Nec {
    const val CARRIER_HZ = 38000

    private const val HDR_MARK = 9000
    private const val HDR_SPACE = 4500
    private const val BIT_MARK = 560
    private const val ZERO_SPACE = 560
    private const val ONE_SPACE = 1690

    /**
     * Standard NEC: 8-bit address + its inverse, 8-bit command + its inverse.
     * Extended NEC: 16-bit address (no inverse), 8-bit command + its inverse.
     * Bits go out least-significant first. Returns alternating mark/space durations in microseconds.
     */
    fun pattern(address: Int, command: Int, extended: Boolean): IntArray {
        val a = address and (if (extended) 0xFFFF else 0xFF)
        val c = command and 0xFF
        val word: Long = if (extended) {
            (a.toLong()) or ((c.toLong()) shl 16) or ((c.inv() and 0xFF).toLong() shl 24)
        } else {
            (a.toLong()) or ((a.inv() and 0xFF).toLong() shl 8) or
                ((c.toLong()) shl 16) or ((c.inv() and 0xFF).toLong() shl 24)
        }
        val out = ArrayList<Int>(68)
        out += HDR_MARK
        out += HDR_SPACE
        for (i in 0 until 32) {
            out += BIT_MARK
            out += if (((word shr i) and 1L) == 1L) ONE_SPACE else ZERO_SPACE
        }
        out += BIT_MARK // stop bit
        return out.toIntArray()
    }
}

class IrSender(context: Context) {
    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean get() = manager?.hasIrEmitter() == true

    fun sendNec(address: Int, command: Int, extended: Boolean) {
        val m = manager ?: return
        if (!m.hasIrEmitter()) return
        m.transmit(Nec.CARRIER_HZ, Nec.pattern(address, command, extended))
    }
}
