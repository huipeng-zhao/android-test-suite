package com.calibur.nfchcetest

import android.app.Activity
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.TextAppearanceSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.ScrollView

class HtmlText(
    private val mContext: Activity,
    private val mText: MyTextView?,
    containerResId: Int
) {
    private val mScrollView: ScrollView? = mContext.findViewById<View>(containerResId) as ScrollView

    fun getColoredText(msg: String?, color: Int): String {
        return "<font color=#" + String.format("%06X", color and 0xFFFFFF) + ">" + msg + "</font>"
    }

    fun getColoredTextRes(msg: String, colorResId: Int): String {
        val color = mContext.resources.getColor(colorResId) and 0xFFFFFF
        return "<font color=#" + String.format("%06X", color) + ">" + msg + "</font>"
    }

    fun addTextLineGreen(msg: String?) {
        appendText(getColoredText(msg, GREEN1) + BR)
    }

    fun addTextLineYellow(msg: String?) {
        appendText(getColoredText(msg, YELLOW1) + BR)
    }

    fun addTextLineRed(msg: String?) {
        appendText(getColoredText(msg, RED1) + BR)
    }

    fun addTextLineBlue(msg: String?) {
        appendText(getColoredText(msg, BLUE1) + BR)
    }

    fun addTextLineU(msg: String?, color: Int) {
        appendText("<u>" + getColoredText(msg, color) + "</u>" + BR)
    }

    fun addTextLine(msg: String?, color: Int) {
        appendText(getColoredText(msg, color) + BR)
    }

    fun addTextLineB(msg: String) {
        appendText("<b>" + msg + "</b>" + BR)
    }

    fun addTextLineU(msg: String) {
        appendText("<u>" + msg + "</u>" + BR)
    }

    fun addTextLine(msg: String) {
        appendText(msg + BR)
    }

    fun addTextRed(msg: String?) {
        appendText(getColoredText(msg, RED1))
    }

    fun addTextBlue(msg: String?) {
        appendText(getColoredText(msg, BLUE1))
    }

    fun addTextYellow(msg: String?) {
        appendText(getColoredText(msg, YELLOW1))
    }

    fun addTextU(msg: String?, color: Int) {
        appendText("<u>" + getColoredText(msg, color) + "</u>")
    }

    fun addText(msg: String?, color: Int) {
        appendText(getColoredText(msg, color))
    }

    fun addText(msg: String?) {
        appendText(msg)
    }

    fun getText(msg: String?, color: Int): String {
        return getColoredText(msg, color)
    }

    fun getTextLine(msg: String): String {
        return msg + BR
    }

    fun getTextLine(msg: String?, color: Int): String {
        return getText(msg, color) + BR
    }

    private fun execute(runnable: Runnable) {
        // Switch threads to redraw canvas correctly.
        try {
            val th = Thread { mContext.runOnUiThread(runnable) }
            th.start()
            th.join()
        } catch (e: InterruptedException) {
        }
    }

    private fun appendText(html: String?) {
        if (html == null) return

        execute {
            mText!!.append(Html.fromHtml(html.replace("\n".toRegex(), BR)))
            mScrollView?.scrollTo(0, mText.bottom)
        }
    }

    @JvmOverloads
    fun addSubject1(msg: String, bgColor: Int = SUBJECT_BG1) {
        execute {
            val html = "<b>" + msg + "</b>" + BR
            mText!!.append(Html.fromHtml(html.replace("\n".toRegex(), BR)))
            mScrollView?.scrollTo(0, mText.bottom)
            //                mText.setLastLineColor(bgColor);
        }
    }

    @JvmOverloads
    fun addSubject2(msg: String, bgColor: Int = SUBJECT_BG2, isUnderline: Boolean = false) {
        execute {
            val sb = SpannableStringBuilder()
            var pos = 0

            sb.append(mText!!.text)
            pos = sb.length

            sb.append(msg + "\n")
            sb.setSpan(
                BackgroundColorSpan(bgColor),
                pos,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isUnderline) {
                sb.setSpan(UnderlineSpan(), pos, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.setSpan(
                TextAppearanceSpan(mContext, R.style.SubjectStyle),
                pos,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            mText.text = sb
            mScrollView?.scrollTo(0, mText.bottom)
        }
    }

    fun addWideText(msg: String, fgColor: Int, bgColor: Int) {
        execute {
            val sb = SpannableStringBuilder()
            var pos = 0

            sb.append(mText!!.text)
            pos = sb.length

            sb.append(msg + "\n")
            sb.setSpan(ScaleXSpan(2.0f), pos, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                BackgroundColorSpan(bgColor),
                pos,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                ForegroundColorSpan(fgColor),
                pos,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            mText.text = sb
            mScrollView?.scrollTo(0, mText.bottom)
        }
    }

    companion object {
        const val RED1: Int = -0x2fcfd0
        const val TRANS_RED1: Int = 0x28D03030
        const val BLUE1: Int = -0xff8f80
        const val TRANS_BLUE2: Int = 0x280040C0
        const val GREEN1: Int = -0xdf9000
        const val TRANS_GREEN1: Int = 0x20207000
        const val YELLOW1: Int = -0x7f8fd0
        const val LIGHTGRAY1: Int = -0x555556
        const val SUBJECT_BG1: Int = -0x6faf4fb0
        const val SUBJECT_BG2: Int = -0x6f8f7f40

        private const val BR = "<br>"

        fun createPartialUnderlineText(buff: ByteArray, ul_offset: Int, ul_len: Int): String? {
            try {
                val sb = StringBuilder()
                sb.append(Util.toHexString(buff, 0, ul_offset))
                sb.append("<u>")
                sb.append(Util.toHexString(buff, ul_offset, ul_len))
                sb.append("</u>")
                if (buff.size > ul_offset + ul_len) {
                    sb.append(Util.toHexString(buff, ul_offset + ul_len))
                }
                return sb.toString()
            } catch (e: Exception) {
                return Util.toHexString(buff)
            }
        }
    }
}
