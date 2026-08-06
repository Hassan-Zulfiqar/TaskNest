package com.hassan.tasknest.presentation.noteslist

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.databinding.ItemNoteCardBinding
import com.hassan.tasknest.presentation.addeditnotes.formatting.SpannableConverter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NoteAdapter(
	private val onNoteClick: (Note) -> Unit,
	private val onNoteLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

	private var notes: List<Note> = emptyList()

	fun submitNotes(newNotes: List<Note>) {
		notes = newNotes
		notifyDataSetChanged()
	}

	fun getNoteAt(position: Int): Note = notes[position]

	override fun getItemCount(): Int = notes.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
		val binding = ItemNoteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return NoteViewHolder(binding)
	}

	override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
		holder.bind(notes[position])
	}

	inner class NoteViewHolder(private val binding: ItemNoteCardBinding) :
		RecyclerView.ViewHolder(binding.root) {

		fun bind(note: Note) {
			binding.tvNoteTitle.text = note.title.ifBlank { "Untitled Note" }
			// note.content may be JSON (formatted note) or plain text (legacy note); strip formatting
			// for this plain-text list preview via jsonToSpannable's existing fallback + toString().
			binding.tvNotePreview.text = SpannableConverter.jsonToSpannable(note.content, itemView.context).toString()
			binding.tvNoteTimestamp.text = formatNoteTimestamp(note.updatedAt)

			val imagePaths = SpannableConverter.extractImagePaths(note.content)
			val thumbnailBitmap = if (imagePaths.isNotEmpty()) {
				try {
					BitmapFactory.decodeFile(imagePaths.first())
				} catch (e: Exception) {
					null
				}
			} else {
				null
			}
			if (thumbnailBitmap != null) {
				binding.ivNoteThumbnail.setImageBitmap(thumbnailBitmap)
				binding.ivNoteThumbnail.visibility = View.VISIBLE
			} else {
				binding.ivNoteThumbnail.visibility = View.GONE
			}

			binding.root.setOnClickListener { onNoteClick(note) }
			binding.root.setOnLongClickListener {
				onNoteLongClick(note)
				true
			}
		}

		private fun formatNoteTimestamp(updatedAt: Long): String {
			val updatedCalendar = Calendar.getInstance().apply {
				timeInMillis = updatedAt
			}
			val todayCalendar = Calendar.getInstance()

			return if (updatedCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
				updatedCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)
			) {
				SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(updatedAt))
			} else {
				SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(updatedAt))
			}
		}
	}
}