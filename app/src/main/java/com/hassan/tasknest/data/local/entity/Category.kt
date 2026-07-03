package com.hassan.tasknest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Stores a task category with its display name and color. */
@Entity(tableName = "categories")
data class Category(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0L,
	val name: String,
	val colorHex: String
)