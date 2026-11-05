package com.godly.moonzoom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.pow

/**
 * Chồng nhiều khung: căn chỉnh -> lấy trung bình (giảm nhiễu ~ √N) -> kéo đen/trắng + gamma +
 * tương phản -> độ bão hòa -> unsharp mask nhẹ. Tiến độ báo theo các bước xử lý THẬT.
 * Giới hạn: xử lý ở độ phân giải giảm (cạnh dài <= 2600px) để không hết RAM.
 */
object NightStacker {
    data class Params(val gamma: Float, val saturation: Float, val sharpen: Float, val contrast: Float)

    val NIGHT = Params(gamma = 0.85f, saturation = 1.10f, sharpen = 0.45f, contrast = 0.30f)
    val MILKY = Params(gamma = 0.70f, saturation = 1.25f, sharpen = 0.35f, contrast = 0.40f)

    private const val MAX_LONG_SIDE = 2600
    private const val BINS = 2048 // 8 mức / 1 giá trị 8-bit, giảm banding ở vùng tối

    suspend fun process(
        context: Context,
        files: List<File>,
        params: Params,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.Default) {
        require(files.isNotEmpty()) { "Không có khung nào" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(files[0].path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_LONG_SIDE) sample *= 2

        fun decode(f: File): Bitmap = BitmapFactory.decodeFile(
            f.path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: throw IOException("Không đọc được khung ảnh")

        val n = files.size
        onProgress(0.01f, "Đang đọc khung 1/$n...")
        val first = decode(files[0])
        val w = first.width
        val h = first.height
        val total = w * h
        val sum = ShortArray(total * 3)
        val px = IntArray(total)
        val luma = ByteArray(total)
        var aligner: Aligner? = null
        var used = 0

        for ((i, f) in files.withIndex()) {
            ensureActive()
            val bmp = if (i == 0) first else decode(f)
            if (bmp.width != w || bmp.height != h) { bmp.recycle(); continue }
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            for (k in 0 until total) {
                val p = px[k]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                luma[k] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
            }
            var dx = 0
            var dy = 0
            val al = aligner
            if (al == null) {
                aligner = Aligner(luma.copyOf(), w, h)
            } else {
                val s = al.estimate(luma)
                dx = s[0]; dy = s[1]
            }
            for (y in 0 until h) {
                val sy = (y + dy).coerceIn(0, h - 1)
                for (x in 0 until w) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    val p = px[sy * w + sx]
                    val idx = (y * w + x) * 3
                    sum[idx] = ((sum[idx].toInt() and 0xFFFF) + ((p shr 16) and 0xFF)).toShort()
                    sum[idx + 1] = ((sum[idx + 1].toInt() and 0xFFFF) + ((p shr 8) and 0xFF)).toShort()
                    sum[idx + 2] = ((sum[idx + 2].toInt() and 0xFFFF) + (p and 0xFF)).toShort()
                }
            }
            used++
            onProgress(0.70f * (i + 1) / n, "Căn chỉnh & chồng khung ${i + 1}/$n...")
        }
        if (used == 0) throw IOException("Không có khung hợp lệ")

        // ---- Phân tích mức sáng ----
        ensureActive()
        onProgress(0.72f, "Phân tích mức sáng và nền trời...")
        val inv = 1f / used
        val hist = Array(3) { IntArray(BINS) }
        for (i in 0 until total) {
            for (c in 0..2) {
                val v = (sum[i * 3 + c].toInt() and 0xFFFF) * inv
                hist[c][(v * 8f).toInt().coerceIn(0, BINS - 1)]++
            }
        }
        fun percentile(hst: IntArray, pct: Float): Float {
            val target = (total * pct / 100f).toLong()
            var acc = 0L
            for (b in 0 until BINS) { acc += hst[b]; if (acc >= target) return b / 8f }
            return (BINS - 1) / 8f
        }
        val lo = FloatArray(3) { percentile(hist[it], 0.5f) }
        var hi = 0f
        for (c in 0..2) hi = max(hi, percentile(hist[c], 99.8f))
        hi = max(hi, lo.max() + 16f)

        // ---- Tông màu bằng LUT ----
        onProgress(0.78f, "Cân bằng màu và tông...")
        val lut = Array(3) { c ->
            FloatArray(BINS) { b ->
                val v = b / 8f
                var t = ((v - lo[c]) / (hi - lo[c])).coerceIn(0f, 1f)
                t = t.pow(params.gamma)
                val s = t * t * (3f - 2f * t)
                t * (1f - params.contrast) + s * params.contrast
            }
        }
        val out = IntArray(total)
        for (i in 0 until total) {
            val rb = ((sum[i * 3].toInt() and 0xFFFF) * inv * 8f).toInt().coerceIn(0, BINS - 1)
            val gb = ((sum[i * 3 + 1].toInt() and 0xFFFF) * inv * 8f).toInt().coerceIn(0, BINS - 1)
            val bb = ((sum[i * 3 + 2].toInt() and 0xFFFF) * inv * 8f).toInt().coerceIn(0, BINS - 1)
            var r = lut[0][rb]; var g = lut[1][gb]; var b = lut[2][bb]
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            r = (gray + (r - gray) * params.saturation).coerceIn(0f, 1f)
            g = (gray + (g - gray) * params.saturation).coerceIn(0f, 1f)
            b = (gray + (b - gray) * params.saturation).coerceIn(0f, 1f)
            out[i] = (0xFF shl 24) or ((r * 255f + 0.5f).toInt() shl 16) or
                ((g * 255f + 0.5f).toInt() shl 8) or (b * 255f + 0.5f).toInt()
        }

        // ---- Unsharp mask nhẹ (3x3) ----
        ensureActive()
        onProgress(0.86f, "Làm nét chi tiết...")
        val res: IntArray
        if (params.sharpen > 0f) {
            res = out.copyOf()
            val a = params.sharpen
            for (y in 1 until h - 1) {
                if (y % 64 == 0) {
                    ensureActive()
                    onProgress(0.86f + 0.09f * y / h, "Làm nét chi tiết...")
                }
                for (x in 1 until w - 1) {
                    var sr = 0; var sg = 0; var sb = 0
                    for (yy in -1..1) {
                        val row = (y + yy) * w + x
                        for (xx in -1..1) {
                            val q = out[row + xx]
                            sr += (q shr 16) and 0xFF
                            sg += (q shr 8) and 0xFF
                            sb += q and 0xFF
                        }
                    }
                    val o = out[y * w + x]
                    val or = (o shr 16) and 0xFF
                    val og = (o shr 8) and 0xFF
                    val ob = o and 0xFF
                    val nr = (or + a * (or - sr / 9f)).toInt().coerceIn(0, 255)
                    val ng = (og + a * (og - sg / 9f)).toInt().coerceIn(0, 255)
                    val nb = (ob + a * (ob - sb / 9f)).toInt().coerceIn(0, 255)
                    res[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        } else {
            res = out
        }

        // ---- Xuất JPEG ----
        ensureActive()
        onProgress(0.96f, "Hoàn thiện ảnh...")
        val result = Bitmap.createBitmap(res, w, h, Bitmap.Config.ARGB_8888)
        val outFile = File.createTempFile("stacked_", ".jpg", context.cacheDir)
        FileOutputStream(outFile).use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        result.recycle()
        val inExif = ExifInterface(files[0].path)
        val outExif = ExifInterface(outFile.path)
        listOf(
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL
        ).forEach { tag -> inExif.getAttribute(tag)?.let { outExif.setAttribute(tag, it) } }
        outExif.saveAttributes()
        onProgress(1f, "Xong")
        outFile
    }
}
