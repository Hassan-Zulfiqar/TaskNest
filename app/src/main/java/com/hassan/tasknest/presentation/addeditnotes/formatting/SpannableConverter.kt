package com.hassan.tasknest.presentation.addeditnotes.formatting

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray
import org.json.JSONObject

/** Converts between Android's Spanned/Spannable text and a JSON string representation of [NoteSpan]s. */
object SpannableConverter {

    /** Serializes a Spanned's plain text and recognized formatting spans into a JSON string. */
    fun spannableToJson(spanned: Spanned): String {
        val noteSpans = mutableListOf<NoteSpan>()

        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)

            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> noteSpans.add(NoteSpan.Bold(start, end))
                        Typeface.ITALIC -> noteSpans.add(NoteSpan.Italic(start, end))
                        Typeface.BOLD_ITALIC -> {
                            noteSpans.add(NoteSpan.Bold(start, end))
                            noteSpans.add(NoteSpan.Italic(start, end))
                        }
                    }
                }
                is UnderlineSpan -> noteSpans.add(NoteSpan.Underline(start, end))
                is AbsoluteSizeSpan -> noteSpans.add(NoteSpan.Size(start, end, span.size))
                is ForegroundColorSpan -> {
                    val hex = String.format("#%06X", 0xFFFFFF and span.foregroundColor)
                    noteSpans.add(NoteSpan.TextColor(start, end, hex))
                }
                is BulletSpan -> noteSpans.add(NoteSpan.Bullet(start, end))
            }
        }

        val spansJson = JSONArray()
        noteSpans.forEach { noteSpan ->
            val spanObject = JSONObject()
            when (noteSpan) {
                is NoteSpan.Bold -> {
                    spanObject.put("type", "BOLD")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                }
                is NoteSpan.Italic -> {
                    spanObject.put("type", "ITALIC")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                }
                is NoteSpan.Underline -> {
                    spanObject.put("type", "UNDERLINE")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                }
                is NoteSpan.Size -> {
                    spanObject.put("type", "SIZE")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                    spanObject.put("value", noteSpan.sizeSp)
                }
                is NoteSpan.TextColor -> {
                    spanObject.put("type", "COLOR")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                    spanObject.put("value", noteSpan.colorHex)
                }
                is NoteSpan.Bullet -> {
                    spanObject.put("type", "BULLET")
                    spanObject.put("start", noteSpan.start)
                    spanObject.put("end", noteSpan.end)
                }
            }
            spansJson.put(spanObject)
        }

        val root = JSONObject()
        root.put("text", spanned.toString())
        root.put("spans", spansJson)
        return root.toString()
    }

    /** Parses a JSON string produced by [spannableToJson] back into a Spannable, falling back to plain text on failure. */
    fun jsonToSpannable(json: String): Spannable {
        return try {
            val root = JSONObject(json)
            val text = root.getString("text")
            val builder = SpannableStringBuilder(text)
            val spansJson = root.getJSONArray("spans")

            for (i in 0 until spansJson.length()) {
                val spanObject = spansJson.getJSONObject(i)
                val start = spanObject.getInt("start")
                val end = spanObject.getInt("end")

                when (spanObject.getString("type")) {
                    "BOLD" -> builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    "ITALIC" -> builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    "UNDERLINE" -> builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    "SIZE" -> {
                        val value = spanObject.getInt("value")
                        builder.setSpan(AbsoluteSizeSpan(value), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    "COLOR" -> {
                        val value = spanObject.getString("value")
                        builder.setSpan(ForegroundColorSpan(Color.parseColor(value)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    "BULLET" -> builder.setSpan(BulletSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            builder
        } catch (e: Exception) {
            SpannableStringBuilder(json)
        }
    }
}
