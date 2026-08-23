package com.bioluck.copywork.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bioluck.copywork.data.InteractiveSegment
import kotlin.math.ceil
import kotlin.math.max

/**
 * 한자 본문 baseline은 일반 문자와 동일한 y에 그리고, 후리가나 공간은 ascent에만
 * 추가하는 span이다. 따라서 루비가 붙은 한자가 아래로 밀리지 않는다.
 */
private class BaselineRubySpan(
    private val reading: String,
    private val rubyScale: Float = .48f,
    private val gapPx: Float,
    private val rubyColor: Int,
) : ReplacementSpan() {
    private fun rubyPaint(base: Paint) = Paint(base).apply {
        textSize = base.textSize * rubyScale
        color = rubyColor
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val ruby = rubyPaint(paint)
        val width = max(paint.measureText(text, start, end), ruby.measureText(reading))
        fm?.let {
            val original = paint.fontMetricsInt
            val rubyHeight = ruby.fontMetricsInt.descent - ruby.fontMetricsInt.ascent
            it.ascent = original.ascent - rubyHeight - ceil(gapPx).toInt()
            it.top = minOf(original.top, it.ascent)
            it.descent = original.descent
            it.bottom = original.bottom
        }
        return ceil(width).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val base = text.subSequence(start, end).toString()
        val ruby = rubyPaint(paint)
        val cellWidth = max(paint.measureText(base), ruby.measureText(reading))
        canvas.drawText(base, x + (cellWidth - paint.measureText(base)) / 2f, y.toFloat(), paint)
        val rubyBaseline = y + paint.fontMetrics.ascent - gapPx - ruby.fontMetrics.descent
        canvas.drawText(reading, x + (cellWidth - ruby.measureText(reading)) / 2f, rubyBaseline, ruby)
    }
}

@Composable
fun InteractiveJapaneseText(
    paragraphs: List<List<InteractiveSegment>>,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 1.dp.toPx() }
    val horizontalPadding = with(density) { 2.dp.roundToPx() }
    val verticalPadding = with(density) { 10.dp.roundToPx() }
    val lineExtra = with(density) { 8.dp.toPx() }
    val rubyColor = Color(0xFF4E6E5D).toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = 20f
                setTextColor(Color(0xFF202A33).toArgb())
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                includeFontPadding = false
                setLineSpacing(lineExtra, 1.12f)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = Color(0x334E6E5D).toArgb()
                isVerticalScrollBarEnabled = true
                linksClickable = true
            }
        },
        update = { view ->
            view.text = buildInteractiveText(paragraphs, gapPx, rubyColor, onWordTap)
        },
    )
}

private fun buildInteractiveText(
    paragraphs: List<List<InteractiveSegment>>,
    gapPx: Float,
    rubyColor: Int,
    onWordTap: (String) -> Unit,
): SpannableStringBuilder = SpannableStringBuilder().apply {
    paragraphs.forEachIndexed { paragraphIndex, paragraph ->
        paragraph.forEach { segment ->
            val start = length
            append(segment.text)
            val end = length
            if (!segment.reading.isNullOrBlank()) {
                setSpan(BaselineRubySpan(segment.reading, gapPx = gapPx, rubyColor = rubyColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            segment.lookupWord?.let { word ->
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onWordTap(word)
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                    }
                }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (paragraphIndex != paragraphs.lastIndex) append("\n\n")
    }
}
