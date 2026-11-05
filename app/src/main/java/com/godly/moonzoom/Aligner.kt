package com.godly.moonzoom

import kotlin.math.abs

/**
 * Căn chỉnh tịnh tiến giữa các khung (không phụ thuộc Android).
 * Quy ước: cur(x + dx, y + dy) ≈ ref(x, y).
 * Bước 1: tìm thô trên ảnh thu nhỏ 1/8 (±8 ô = ±64 px). Bước 2: tinh chỉnh ±8 px
 * bằng so khớp mảng 5x5 quanh các điểm đặc trưng (ngôi sao / cạnh rõ nhất trong từng ô).
 */
class Aligner(private val ref: ByteArray, private val w: Int, private val h: Int) {
    private val cw = w / 8
    private val ch = h / 8
    private val refCoarse = downsample8(ref)
    private val featX: IntArray
    private val featY: IntArray

    init {
        val cand = ArrayList<IntArray>()
        val cell = 32
        for (cyc in 0 until h / cell) for (cxc in 0 until w / cell) {
            var best = -1; var bx = 0; var by = 0
            val y0 = maxOf(8, cyc * cell); val y1 = minOf(h - 8, cyc * cell + cell)
            val x0 = maxOf(8, cxc * cell); val x1 = minOf(w - 8, cxc * cell + cell)
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * w + x
                val g = abs((ref[i + 1].toInt() and 0xFF) - (ref[i - 1].toInt() and 0xFF)) +
                    abs((ref[i + w].toInt() and 0xFF) - (ref[i - w].toInt() and 0xFF))
                if (g > best) { best = g; bx = x; by = y }
            }
            if (best >= 20) cand.add(intArrayOf(best, bx, by))
        }
        cand.sortByDescending { it[0] }
        val top = cand.take(600)
        featX = IntArray(top.size) { top[it][1] }
        featY = IntArray(top.size) { top[it][2] }
    }

    /** Trả về [dx, dy]. */
    fun estimate(cur: ByteArray): IntArray {
        val c = coarse(cur)
        return refine(cur, c[0], c[1])
    }

    private fun downsample8(src: ByteArray): ByteArray {
        val out = ByteArray(cw * ch)
        for (by in 0 until ch) for (bx in 0 until cw) {
            var s = 0
            for (yy in 0 until 8) {
                val row = (by * 8 + yy) * w + bx * 8
                for (xx in 0 until 8) s += src[row + xx].toInt() and 0xFF
            }
            out[by * cw + bx] = (s shr 6).toByte()
        }
        return out
    }

    private fun coarse(cur: ByteArray): IntArray {
        val c = downsample8(cur)
        var best = Double.MAX_VALUE
        var bdx = 0; var bdy = 0
        val r = 8
        for (dy in -r..r) for (dx in -r..r) {
            val s = sad(refCoarse, c, cw, ch, dx, dy) + (abs(dx) + abs(dy)) * 0.05
            if (s < best) { best = s; bdx = dx; bdy = dy }
        }
        return intArrayOf(bdx, bdy)
    }

    private fun sad(a: ByteArray, b: ByteArray, w: Int, h: Int, dx: Int, dy: Int): Double {
        val x0 = maxOf(0, -dx); val x1 = minOf(w, w - dx)
        val y0 = maxOf(0, -dy); val y1 = minOf(h, h - dy)
        val cnt = (x1 - x0) * (y1 - y0)
        if (x1 <= x0 || y1 <= y0 || cnt < w * h / 4) return Double.MAX_VALUE
        var sum = 0L
        for (y in y0 until y1) {
            val ra = y * w
            val rb = (y + dy) * w + dx
            for (x in x0 until x1) {
                sum += abs((a[ra + x].toInt() and 0xFF) - (b[rb + x].toInt() and 0xFF))
            }
        }
        return sum.toDouble() / cnt
    }

    private fun refine(cur: ByteArray, cdx: Int, cdy: Int): IntArray {
        val cx = cdx * 8
        val cy = cdy * 8
        if (featX.size < 12) return intArrayOf(cx, cy)
        var best = Double.MAX_VALUE
        var bx = cx; var by = cy
        for (sy in cy - 8..cy + 8) for (sx in cx - 8..cx + 8) {
            var total = 0L
            var used = 0
            for (i in featX.indices) {
                val x2 = featX[i] + sx
                val y2 = featY[i] + sy
                if (x2 < 2 || x2 >= w - 2 || y2 < 2 || y2 >= h - 2) continue
                for (py in -2..2) {
                    val ra = (featY[i] + py) * w + featX[i]
                    val rb = (y2 + py) * w + x2
                    for (px in -2..2) {
                        total += abs((ref[ra + px].toInt() and 0xFF) - (cur[rb + px].toInt() and 0xFF))
                    }
                }
                used++
            }
            if (used >= 10) {
                val s = total.toDouble() / used + (abs(sx - cx) + abs(sy - cy)) * 0.05
                if (s < best) { best = s; bx = sx; by = sy }
            }
        }
        return intArrayOf(bx, by)
    }
}
