package com.hassan.tasknest.presentation.noteslist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hassan.tasknest.data.local.entity.Note
import com.hassan.tasknest.databinding.ItemNoteCardBinding
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
			binding.tvNotePreview.text = note.content
			binding.tvNoteTimestamp.text = formatNoteTimestamp(note.updatedAt)

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