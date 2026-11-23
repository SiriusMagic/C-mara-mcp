package com.zirakusa.camara

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import com.zirakusa.camara.data.ColorStyle
import com.zirakusa.camara.data.Pixels
import java.time.LocalDateTime

/**
 * What is done to a photo after it is taken: turned upright (and mirrored for the front camera, as the preview shows it),
 * cut square, its colour style or the document look, and the «Cámara · fecha» watermark. The rules are in [Pixels].
 */
class PhotoLook(private val look: ColorStyle, private val watermark: Boolean, private val square: Boolean,
    private val document: Boolean, private val mirror: Boolean) {

    fun render(source: Bitmap, rotation: Int, at: LocalDateTime = LocalDateTime.now()): Bitmap {
        val turn = Matrix().apply { if (mirror) postScale(-1f, 1f); postRotate(rotation.toFloat()) }
        var (x, y, w, h) = listOf(0, 0, source.width, source.height)
        if (square) {
            // The square is the middle of the picture once upright; a 90° turn swaps width and height, the middle is the same.
            val (l, t, side) = Pixels.square(source.width, source.height); x = l; y = t; w = side; h = side
        }
        val upright = Bitmap.createBitmap(source, x, y, w, h, turn, true)
        if (upright !== source) source.recycle()
        val out = if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }
        val canvas = Canvas(out)
        if (document) {
            val px = IntArray(out.width * out.height)
            out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
            Pixels.document(px)
            out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        } else if (look != ColorStyle.NATURAL) {
            val copy = out.copy(Bitmap.Config.ARGB_8888, false)
            canvas.drawBitmap(copy, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(look.matrix) })
            copy.recycle()
        }
        if (watermark) {
            val size = out.width / 38f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(size / 6, 0f, size / 12, 0x99000000.toInt())
            }
            canvas.drawText(Pixels.watermark(at), size, out.height - size, paint)
        }
        return out
    }
}
