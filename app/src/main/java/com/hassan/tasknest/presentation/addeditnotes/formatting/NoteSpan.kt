package com.hassan.tasknest.presentation.addeditnotes.formatting

/** Represents a single formatting range applied to a note's plain text content. */
sealed class  NoteSpan {
    abstract val start: Int
    abstract val end: Int

    data class Bold(override val start: Int, override val end: Int) : NoteSpan()
    data class Italic(override val start: Int, override val end: Int) : NoteSpan()
    data class Underline(override val start: Int, override val end: Int) : NoteSpan()
    data class Size(override val start: Int, override val end: Int, val sizeSp: Int) : NoteSpan()
    data class TextColor(override val start: Int, override val end: Int, val colorHex: String) : NoteSpan()
    data class Bullet(override val start: Int, override val end: Int) : NoteSpan()
}
