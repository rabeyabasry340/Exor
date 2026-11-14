package com.exor.mediaserver.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream

/** Draws the server icon shown on the TV's media list. Rendered once per size, cached. */
object IconRenderer {
    private val cache = HashMap<Int, ByteArray>()

    @Synchronized
    fun png(size: Int): ByteArray = cache.getOrPut(size) {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#FF0B1030"), Color.parseColor("#FF2A1A6B"), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, p)

        val u = size / 108f
        val a = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF38E1FF") }
        val b = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A5CFF") }
        c.drawRect(36 * u, 34 * u, 44 * u, 74 * u, a)
        c.drawRect(36 * u, 34 * u, 68 * u, 42 * u, a)
        c.drawRect(36 * u, 50 * u, 62 * u, 58 * u, b)
        c.drawRect(36 * u, 66 * u, 68 * u, 74 * u, b)

        val bo = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bo)
        bmp.recycle()
        bo.toByteArray()
    }
}
