package com.godly.moonzoom

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object CaptureSaver {
    /** Ảnh crop số nhỏ hơn mức này sẽ được phóng bằng nội suy (KHÔNG thêm chi tiết thật). */
    const val MIN_LONG_SIDE = 1600
    private const val FOLDER = "Pictures/MoonZoom"

    /**
     * Lưu ảnh vào thư viện. [extra] > 1 nghĩa là phần zoom số vượt quá zoom gốc của camera:
     * ảnh được crop giữa thật sự (không giả lập), tên file có hậu tố _digital.
     */
    suspend fun save(context: Context, src: File, extra: Float, tag: String = ""): Uri = withContext(Dispatchers.IO) {
        val digital = extra > 1.01f
        val name = "MoonZoom_" + (if (tag.isNotEmpty()) tag + "_" else "") +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            (if (digital) "_digital" else "") + ".jpg"
        val cropped = if (digital) cropCenter(context, src, extra) else null
        val file = cropped ?: src
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Không tạo được mục MediaStore")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } finally {
            cropped?.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun openRegionDecoder(path: String): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(path)
        else BitmapRegionDecoder.newInstance(path, false)

    private fun cropCenter(context: Context, src: File, extra: Float): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        val cw = max(16, (w / extra).roundToInt())
        val ch = max(16, (h / extra).roundToInt())
        val left = (w - cw) / 2
        val top = (h - ch) / 2

        val decoder = openRegionDecoder(src.path)
        var bmp = decoder.decodeRegion(
            Rect(left, top, left + cw, top + ch),
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        )
        decoder.recycle()

        val longSide = max(bmp.width, bmp.height)
        if (longSide < MIN_LONG_SIDE) {
            val s = MIN_LONG_SIDE.toFloat() / longSide
            val scaled = Bitmap.createScaledBitmap(
                bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true
            )
            bmp.recycle()
            bmp = scaled
        }
        val out = File.createTempFile("crop_", ".jpg", context.cacheDir)
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()

        val inExif = ExifInterface(src.path)
        val outExif = ExifInterface(out.path)
        listOf(
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL
        ).forEach { tag -> inExif.getAttribute(tag)?.let { outExif.setAttribute(tag, it) } }
        outExif.saveAttributes()
        return out
    }
}

object GalleryHelper {
    suspend fun latest(context: Context): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("Pictures/MoonZoom%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                if (c.moveToFirst())
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                else null
            }
        }.getOrNull()
    }

    suspend fun thumb(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()
    }
}
