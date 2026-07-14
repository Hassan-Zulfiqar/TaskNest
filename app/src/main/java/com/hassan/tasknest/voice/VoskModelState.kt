package com.hassan.tasknest.voice

sealed class VoskModelState {
	object Idle : VoskModelState()

	data class Downloading(val progressPercent: Int) : VoskModelState()

	object Unzipping : VoskModelState()

	object Ready : VoskModelState()

	data class Error(val message: String) : VoskModelState()
}