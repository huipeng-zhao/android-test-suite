package com.calibur.nfchcetest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import kotlin.math.max

class MyTextView : TextView {
    private var mPaint: Paint? = null
    private val mColoredLines = HashMap<Int, Int>()

    constructor(context: Context?, attribute: AttributeSet?) : super(context, attribute)

    constructor(context: Context?) : super(context)

    fun resetText() {
        mColoredLines.clear()
        text = ""
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (DGB) {
            Log.d(
                TAG,
                "onLayout: mPaint=" + mPaint + " changed=" + changed + String.format(
                    " %d,%d,%d,%d",
                    left,
                    top,
                    right,
                    bottom
                )
            )
        }
        if (mPaint == null) {
            mPaint = paint
            textScaleX = 0.95f
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (DGB) {
            Log.d(TAG, "onDraw: $canvas, mPaint=$mPaint")
        }
        try {
            val width = measuredWidth
            val height = measuredHeight - extendedPaddingTop - extendedPaddingBottom
            val paddingTop = extendedPaddingTop
            val lineHeight = lineHeight
            val textCount = height / lineHeight
            val lines = this.lineCount
            val lineCount = max(textCount.toDouble(), lines.toDouble()).toInt()

            val points = FloatArray(lineCount shl 2) //x4
            for (i in 0 until lineCount) {
                points[(i shl 2) + 0] = 0f
                points[(i shl 2) + 1] = (i * lineHeight + paddingTop).toFloat()
                points[(i shl 2) + 2] = width.toFloat()
                points[(i shl 2) + 3] = (i * lineHeight + paddingTop).toFloat()
            }

            for ((line, color) in mColoredLines) {
                val t = points[(line shl 2) + 1]
                val b = t + lineHeight
                val p = Paint()
                p.color = color
                p.strokeWidth = 0f
                p.style = Paint.Style.FILL
                canvas.drawRect(0f, t, width.toFloat(), b, p)
                if (DGB) {
                    Log.d(TAG, String.format("canvas.drawRect(0, %f, %d, %f, paint)", t, width, b))
                }
            }

            if (mPaint == null) {
                mPaint = paint
            }
            mPaint!!.color = context.resources.getColor(R.color.linecolor)
            canvas.drawLines(points, mPaint!!)
        } catch (e: Exception) {
            if (DGB) {
                Log.e(TAG, "onDraw: $e")
            }
        }
        super.onDraw(canvas)
    }

    companion object {
        private const val TAG = "MyTextView"
        private const val DGB = false
    }
}
