package com.u3coding.shaver.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OnboardingIllustrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var pageIndex: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOuterPanel(canvas)
        when (pageIndex) {
            0 -> drawChatRuleScreenshot(canvas)
            1 -> drawAliasScreenshot(canvas)
            2 -> drawWidgetRealScene(canvas)
            else -> drawSceneManagerScreenshot(canvas)
        }
    }

    private fun drawOuterPanel(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(7, 18, 31)
        canvas.drawRoundRect(fullRect(), dp(24f), dp(24f), paint)
        paint.color = Color.argb(48, 33, 184, 255)
        canvas.drawCircle(width * 0.12f, height * 0.12f, width * 0.2f, paint)
        paint.color = Color.argb(42, 98, 245, 154)
        canvas.drawCircle(width * 0.9f, height * 0.86f, width * 0.25f, paint)
    }

    private fun drawChatRuleScreenshot(canvas: Canvas) {
        val screen = drawAppScreen(canvas)
        drawMainTopBar(canvas, screen)
        val y = drawGuideMiniCard(canvas, screen, screen.top + dp(64f))
        val configBottom = drawConfigPanel(canvas, screen, y + dp(8f), "\u5bb6", listOf("\u5f53\u524d\u4eae\u5ea6 80%", "\u5f53\u524d\u97f3\u91cf 40%"))
        drawChatArea(canvas, screen, configBottom + dp(10f))
        drawInputBar(canvas, screen, "\u8fdb\u5165\u5bb6\u65f6\uff0c\u4eae\u5ea6\u8c03\u5230 80%")
    }

    private fun drawAliasScreenshot(canvas: Canvas) {
        val screen = drawAppScreen(canvas)
        drawMainTopBar(canvas, screen)
        val y = drawGuideMiniCard(canvas, screen, screen.top + dp(64f), "\u5148\u8bf4\uff1a\u628a\u5f53\u524d\u65e0\u7ebf\u7f51\u53eb\u505a\u5bb6")
        drawConfigPanel(canvas, screen, y + dp(8f), "HUAWEI-5G-1234", listOf("\u522b\u540d \u5bb6", "\u4e0b\u6b21\u53ef\u76f4\u63a5\u8bf4\u201c\u5bb6\u201d"))
        drawBubble(canvas, screen.left + dp(18f), screen.bottom - dp(150f), screen.width() * 0.78f, "\u628a\u5f53\u524d\u65e0\u7ebf\u7f51\u53eb\u505a\u5bb6", true)
        drawBubble(canvas, screen.left + dp(18f), screen.bottom - dp(94f), screen.width() * 0.82f, "\u5df2\u628a\u8fd9\u4e2a\u65e0\u7ebf\u7f51\u8bbe\u4e3a\u201c\u5bb6\u201d", false)
        drawInputBar(canvas, screen, "\u79bb\u5f00\u5bb6\u65f6\uff0c\u97f3\u91cf\u8c03\u5230 20%")
    }

    private fun drawWidgetRealScene(canvas: Canvas) {
        val phone = drawPhoneShell(canvas)
        drawHomeWallpaper(canvas, phone)
        drawHomeStatus(canvas, phone)
        drawAppIcons(canvas, phone)
        drawActualWidget(canvas, phone)
        drawWidgetSheetHint(canvas, phone)
    }

    private fun drawSceneManagerScreenshot(canvas: Canvas) {
        val screen = drawAppScreen(canvas)
        drawStatusBar(canvas, screen, "\u573a\u666f\u7ba1\u7406")
        drawText(canvas, "\u65e0\u7ebf\u7f51\u573a\u666f\u7ba1\u7406", screen.left + dp(18f), screen.top + dp(58f), 20f, Color.rgb(234, 244, 255), true)
        drawText(canvas, "\u7ba1\u7406\u4efb\u610f\u65e0\u7ebf\u7f51\u7684\u8fdb\u5165\u548c\u79bb\u5f00\u914d\u7f6e", screen.left + dp(18f), screen.top + dp(82f), 11f, Color.rgb(157, 187, 226), false)
        drawButton(canvas, RectF(screen.left + dp(18f), screen.top + dp(102f), screen.centerX() - dp(4f), screen.top + dp(140f)), "\u6dfb\u52a0\u5f53\u524d\u65e0\u7ebf\u7f51", true)
        drawButton(canvas, RectF(screen.centerX() + dp(4f), screen.top + dp(102f), screen.right - dp(18f), screen.top + dp(140f)), "\u624b\u52a8\u6dfb\u52a0", false)
        drawSceneCard(canvas, screen, screen.top + dp(160f), "\u5bb6", "\u65e0\u7ebf\u7f51\u540d\u79f0\uff1aHUAWEI-5G-1234", "\u8fdb\u5165\u914d\u7f6e 3 \u9879  |  \u79bb\u5f00\u914d\u7f6e 2 \u9879")
        drawSceneCard(canvas, screen, screen.top + dp(240f), "\u516c\u53f8", "\u65e0\u7ebf\u7f51\u540d\u79f0\uff1a\u516c\u53f8\u7f51\u7edc", "\u8fdb\u5165\u914d\u7f6e 2 \u9879  |  \u79bb\u5f00\u914d\u7f6e 1 \u9879")
    }

    private fun drawAppScreen(canvas: Canvas): RectF {
        val phone = drawPhoneShell(canvas)
        val screen = phone.insetCopy(dp(9f), dp(18f))
        paint.color = Color.rgb(8, 21, 37)
        canvas.drawRoundRect(screen, dp(22f), dp(22f), paint)
        return screen
    }

    private fun drawPhoneShell(canvas: Canvas): RectF {
        val phoneWidth = width * 0.76f
        val phoneHeight = height * 0.9f
        val left = (width - phoneWidth) / 2f
        val top = height * 0.045f
        val phone = RectF(left, top, left + phoneWidth, top + phoneHeight)
        paint.color = Color.rgb(5, 12, 21)
        canvas.drawRoundRect(phone, dp(30f), dp(30f), paint)
        paint.color = Color.rgb(30, 42, 56)
        canvas.drawRoundRect(phone.insetCopy(dp(3f)), dp(28f), dp(28f), paint)
        return phone.insetCopy(dp(5f))
    }

    private fun drawMainTopBar(canvas: Canvas, screen: RectF) {
        drawStatusBar(canvas, screen, "\u573a\u666f\u52a9\u624b")
        paint.color = Color.rgb(16, 43, 71)
        canvas.drawRect(screen.left, screen.top + dp(20f), screen.right, screen.top + dp(70f), paint)
        drawText(canvas, "\u573a\u666f\u52a9\u624b", screen.left + dp(15f), screen.top + dp(48f), 16f, Color.rgb(234, 245, 255), true)
        drawSmallButton(canvas, screen.right - dp(112f), screen.top + dp(32f), "\u4f7f\u7528\u6307\u5357")
        drawSmallButton(canvas, screen.right - dp(56f), screen.top + dp(32f), "\u573a\u666f")
        drawText(canvas, "\u65e0\u7ebf\u7f51\u573a\u666f\u81ea\u52a8\u5316\u52a9\u624b", screen.left + dp(15f), screen.top + dp(64f), 9f, Color.rgb(187, 217, 243), false)
    }

    private fun drawStatusBar(canvas: Canvas, screen: RectF, title: String) {
        paint.color = Color.rgb(8, 21, 37)
        canvas.drawRoundRect(RectF(screen.left, screen.top, screen.right, screen.top + dp(24f)), dp(18f), dp(18f), paint)
        drawText(canvas, "12:30", screen.left + dp(14f), screen.top + dp(17f), 8f, Color.rgb(205, 235, 255), true)
        drawText(canvas, title, screen.centerX() - dp(24f), screen.top + dp(17f), 8f, Color.rgb(205, 235, 255), false)
        drawText(canvas, "\u4fe1\u53f7", screen.right - dp(42f), screen.top + dp(17f), 8f, Color.rgb(205, 235, 255), true)
    }

    private fun drawGuideMiniCard(canvas: Canvas, screen: RectF, top: Float, text: String = "\u5148\u8bf4\uff1a\u628a\u5f53\u524d\u65e0\u7ebf\u7f51\u53eb\u505a\u5bb6"): Float {
        val rect = RectF(screen.left + dp(12f), top, screen.right - dp(12f), top + dp(58f))
        paint.color = Color.argb(235, 23, 49, 77)
        canvas.drawRoundRect(rect, dp(14f), dp(14f), paint)
        drawText(canvas, "\u65b0\u624b\u5f15\u5bfc\uff1a\u4f60\u53ef\u4ee5\u8fd9\u6837\u8bf4", rect.left + dp(10f), rect.top + dp(19f), 10f, Color.rgb(234, 244, 255), true)
        drawText(canvas, text, rect.left + dp(10f), rect.top + dp(40f), 9f, Color.rgb(175, 203, 232), false)
        return rect.bottom
    }

    private fun drawConfigPanel(canvas: Canvas, screen: RectF, top: Float, ssid: String, rows: List<String>): Float {
        val rect = RectF(screen.left + dp(12f), top, screen.right - dp(12f), top + dp(106f))
        paint.color = Color.argb(235, 23, 49, 77)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        drawText(canvas, "\u5f53\u524d\u65e0\u7ebf\u7f51\uff1a$ssid", rect.left + dp(10f), rect.top + dp(20f), 10f, Color.rgb(234, 244, 255), true)
        drawText(canvas, "\u73af\u5883\u914d\u7f6e\u9762\u677f", rect.left + dp(10f), rect.top + dp(42f), 9f, Color.rgb(157, 187, 226), false)
        rows.forEachIndexed { index, label ->
            val rowTop = rect.top + dp(52f + index * 23f)
            paint.color = if (index == 0) Color.rgb(31, 86, 72) else Color.rgb(18, 39, 63)
            canvas.drawRoundRect(RectF(rect.left + dp(10f), rowTop, rect.right - dp(10f), rowTop + dp(18f)), dp(8f), dp(8f), paint)
            drawText(canvas, label, rect.left + dp(18f), rowTop + dp(13f), 8.5f, Color.rgb(234, 244, 255), false)
        }
        return rect.bottom
    }

    private fun drawChatArea(canvas: Canvas, screen: RectF, top: Float) {
        drawBubble(canvas, screen.left + dp(18f), top, screen.width() * 0.72f, "\u8fdb\u5165\u5bb6\u65f6\uff0c\u4eae\u5ea6\u8c03\u5230 80%", true)
        drawBubble(canvas, screen.left + dp(18f), top + dp(54f), screen.width() * 0.76f, "\u5df2\u6267\u884c\uff1aset_brightness", false)
    }

    private fun drawInputBar(canvas: Canvas, screen: RectF, hint: String) {
        val rect = RectF(screen.left + dp(12f), screen.bottom - dp(54f), screen.right - dp(12f), screen.bottom - dp(14f))
        paint.color = Color.rgb(13, 33, 56)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), paint)
        drawText(canvas, hint, rect.left + dp(12f), rect.top + dp(25f), 8.5f, Color.rgb(111, 137, 170), false)
        drawButton(canvas, RectF(rect.right - dp(48f), rect.top + dp(6f), rect.right - dp(8f), rect.bottom - dp(6f)), "\u53d1\u9001", true, 8f)
    }

    private fun drawBubble(canvas: Canvas, left: Float, top: Float, bubbleWidth: Float, label: String, user: Boolean) {
        paint.color = if (user) Color.rgb(33, 184, 255) else Color.rgb(24, 55, 88)
        val rect = RectF(left, top, left + bubbleWidth, top + dp(40f))
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        drawText(canvas, label, rect.left + dp(10f), rect.top + dp(24f), 8.5f, Color.WHITE, false)
    }

    private fun drawHomeWallpaper(canvas: Canvas, phone: RectF) {
        val screen = phone.insetCopy(dp(9f), dp(18f))
        paint.color = Color.rgb(11, 31, 50)
        canvas.drawRoundRect(screen, dp(22f), dp(22f), paint)
        paint.color = Color.argb(90, 33, 184, 255)
        canvas.drawCircle(screen.left + screen.width() * 0.25f, screen.top + screen.height() * 0.18f, dp(58f), paint)
        paint.color = Color.argb(80, 98, 245, 154)
        canvas.drawCircle(screen.right - dp(28f), screen.bottom - dp(88f), dp(76f), paint)
    }

    private fun drawHomeStatus(canvas: Canvas, phone: RectF) {
        val screen = phone.insetCopy(dp(9f), dp(18f))
        drawText(canvas, "12:30", screen.left + dp(16f), screen.top + dp(22f), 9f, Color.WHITE, true)
        drawText(canvas, "\u65e0\u7ebf", screen.right - dp(48f), screen.top + dp(22f), 9f, Color.WHITE, true)
    }

    private fun drawAppIcons(canvas: Canvas, phone: RectF) {
        val screen = phone.insetCopy(dp(9f), dp(18f))
        val labels = listOf("\u7535\u8bdd", "\u804a\u5929", "\u573a\u666f", "\u5de5\u5177")
        labels.forEachIndexed { index, label ->
            val left = screen.left + dp(26f + index * 48f)
            val top = screen.bottom - dp(86f)
            paint.color = if (label == "\u573a\u666f") Color.rgb(33, 184, 255) else Color.argb(130, 234, 244, 255)
            canvas.drawRoundRect(RectF(left, top, left + dp(30f), top + dp(30f)), dp(9f), dp(9f), paint)
            drawText(canvas, label, left - dp(2f), top + dp(45f), 7f, Color.WHITE, false)
        }
    }

    private fun drawActualWidget(canvas: Canvas, phone: RectF) {
        val screen = phone.insetCopy(dp(9f), dp(18f))
        val rect = RectF(screen.left + dp(20f), screen.top + dp(76f), screen.right - dp(20f), screen.top + dp(178f))
        paint.color = Color.argb(235, 8, 21, 37)
        canvas.drawRoundRect(rect, dp(22f), dp(22f), paint)
        paint.color = Color.rgb(98, 245, 154)
        canvas.drawCircle(rect.left + dp(45f), rect.centerY(), dp(27f), paint)
        drawText(canvas, "\u667a\u80fd", rect.left + dp(22f), rect.centerY() + dp(7f), 13f, Color.rgb(8, 21, 37), true)
        drawText(canvas, "\u65e0\u7ebf\u7f51\u573a\u666f\u5f00\u5173", rect.left + dp(84f), rect.top + dp(38f), 13f, Color.WHITE, true)
        drawText(canvas, "\u70b9\u51fb\u5e94\u7528\u5f53\u524d\u5168\u5c40\u914d\u7f6e", rect.left + dp(84f), rect.top + dp(66f), 9f, Color.rgb(175, 203, 232), false)
    }

    private fun drawWidgetSheetHint(canvas: Canvas, phone: RectF) {
        val screen = phone.insetCopy(dp(9f), dp(18f))
        val sheet = RectF(screen.left, screen.bottom - dp(132f), screen.right, screen.bottom)
        paint.color = Color.argb(238, 234, 244, 255)
        canvas.drawRoundRect(sheet, dp(22f), dp(22f), paint)
        drawText(canvas, "\u957f\u6309\u684c\u9762 > \u5c0f\u7ec4\u4ef6 > \u573a\u666f\u52a9\u624b", sheet.left + dp(18f), sheet.top + dp(34f), 12f, Color.rgb(17, 53, 65), true)
        drawText(canvas, "\u6dfb\u52a0\u540e\uff0c\u4ee5\u540e\u70b9\u4e00\u4e0b\u5c31\u80fd\u6267\u884c", sheet.left + dp(18f), sheet.top + dp(62f), 9f, Color.rgb(77, 111, 124), false)
        drawButton(canvas, RectF(sheet.left + dp(18f), sheet.top + dp(78f), sheet.right - dp(18f), sheet.top + dp(110f)), "\u6dfb\u52a0\u5230\u684c\u9762", true, 10f)
    }

    private fun drawSceneCard(canvas: Canvas, screen: RectF, top: Float, title: String, ssid: String, summary: String) {
        val rect = RectF(screen.left + dp(18f), top, screen.right - dp(18f), top + dp(66f))
        paint.color = Color.rgb(229, 250, 255)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
        drawText(canvas, title, rect.left + dp(14f), rect.top + dp(22f), 14f, Color.rgb(17, 53, 65), true)
        drawText(canvas, ssid, rect.left + dp(14f), rect.top + dp(40f), 8f, Color.rgb(77, 111, 124), false)
        drawText(canvas, summary, rect.left + dp(14f), rect.top + dp(56f), 8f, Color.rgb(77, 111, 124), false)
    }

    private fun drawSmallButton(canvas: Canvas, left: Float, top: Float, text: String) {
        val rect = RectF(left, top, left + dp(48f), top + dp(22f))
        paint.color = Color.rgb(18, 39, 63)
        canvas.drawRoundRect(rect, dp(11f), dp(11f), paint)
        drawText(canvas, text, rect.left + dp(6f), rect.top + dp(14f), 6.5f, Color.rgb(234, 244, 255), false)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, primary: Boolean, textSize: Float = 9f) {
        paint.color = if (primary) Color.rgb(33, 184, 255) else Color.rgb(18, 39, 63)
        canvas.drawRoundRect(rect, dp(13f), dp(13f), paint)
        drawText(canvas, text, rect.left + dp(8f), rect.centerY() + dp(4f), textSize, Color.WHITE, true)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, sizeSp: Float, color: Int, bold: Boolean) {
        textPaint.textSize = sizeSp * resources.displayMetrics.scaledDensity
        textPaint.color = color
        textPaint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        canvas.drawText(text, x, y, textPaint)
    }

    private fun fullRect(): RectF {
        return RectF(0f, 0f, width.toFloat(), height.toFloat())
    }

    private fun RectF.insetCopy(all: Float): RectF {
        return insetCopy(all, all)
    }

    private fun RectF.insetCopy(horizontal: Float, vertical: Float): RectF {
        return RectF(left + horizontal, top + vertical, right - horizontal, bottom - vertical)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
