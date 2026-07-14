package com.hassan.tasknest.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import org.json.JSONObject
import org.vosk.Recognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Wraps Vosk speech recognition for live dictation callbacks. */
class VoskDictationController(
	private val model: org.vosk.Model,
	private val onPartialResult: (String) -> Unit,
	private val onFinalResult: (String) -> Unit,
	private val onError: (String) -> Unit
) {

	private var audioRecord: AudioRecord? = null
	private var recognizer: Recognizer? = null
	private var echoCanceler: AcousticEchoCanceler? = null
	private var noiseSuppressor: NoiseSuppressor? = null
	private var captureJob: Job? = null
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	fun start() {
		try {
			val createdRecognizer = Recognizer(model, SAMPLE_RATE.toFloat())
			val minBufferSize = AudioRecord.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT
			)
			if (minBufferSize <= 0) {
				createdRecognizer.close()
				onError("Failed to determine audio buffer size")
				return
			}

			val bufferSizeInBytes = minBufferSize * 2
			val bufferSizeInShorts = bufferSizeInBytes / 2
			val createdAudioRecord = AudioRecord(
				MediaRecorder.AudioSource.VOICE_RECOGNITION,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferSizeInBytes
			)
			if (createdAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
				createdAudioRecord.release()
				createdRecognizer.close()
				onError("Failed to initialize audio recording")
				return
			}

			recognizer = createdRecognizer
			audioRecord = createdAudioRecord

			val sessionId = createdAudioRecord.audioSessionId
			if (AcousticEchoCanceler.isAvailable()) {
				echoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
			}
			if (NoiseSuppressor.isAvailable()) {
				noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
			}

			createdAudioRecord.startRecording()

			captureJob = scope.launch {
				val shortBuffer = ShortArray(bufferSizeInShorts)
				val byteBuffer = ByteArray(bufferSizeInBytes)
				while (isActive) {
					try {
						val readCount = createdAudioRecord.read(shortBuffer, 0, shortBuffer.size)
						if (readCount > 0) {
							shortsToLittleEndianBytes(shortBuffer, readCount, byteBuffer)
							val activeRecognizer = recognizer ?: break
							val accepted = activeRecognizer.acceptWaveForm(byteBuffer, readCount * 2)
							val recognizedJson = if (accepted) {
								activeRecognizer.result
							} else {
								activeRecognizer.partialResult
							}
							val recognizedText = extractRecognizedText(
								recognizedJson,
								if (accepted) "text" else "partial"
							)
							if (recognizedText != null) {
								if (accepted) {
									onFinalResult(recognizedText)
								} else {
									onPartialResult(recognizedText)
								}
							}
						}
					} catch (exception: Exception) {
						onError(exception.message ?: "Unknown recognition error")
						break
					}
				}
			}
		} catch (exception: Exception) {
			audioRecord = null
			recognizer = null
			echoCanceler = null
			noiseSuppressor = null
			captureJob = null
			onError(exception.message ?: "Failed to start dictation")
		}
	}

	fun stop() {
		try {
			captureJob?.cancel()
			captureJob = null
			audioRecord?.stop()
			audioRecord?.release()
			audioRecord = null
			echoCanceler?.release()
			echoCanceler = null
			noiseSuppressor?.release()
			noiseSuppressor = null
			recognizer?.close()
			recognizer = null
		} catch (_: Exception) {
			// Ignore teardown errors.
		} finally {
			captureJob = null
			audioRecord = null
			echoCanceler = null
			noiseSuppressor = null
			recognizer = null
		}
	}

	private fun extractRecognizedText(hypothesis: String?, key: String): String? {
		if (hypothesis.isNullOrBlank()) {
			return null
		}

		return try {
			val text = JSONObject(hypothesis).optString(key, "").trim()
			text.takeIf { it.isNotBlank() }
		} catch (_: Exception) {
			null
		}
	}

	private fun shortsToLittleEndianBytes(shorts: ShortArray, count: Int, target: ByteArray) {
		var targetIndex = 0
		for (index in 0 until count) {
			val sample = shorts[index].toInt()
			target[targetIndex] = (sample and 0xFF).toByte()
			target[targetIndex + 1] = ((sample shr 8) and 0xFF).toByte()
			targetIndex += 2
		}
	}

	companion object {
		private const val SAMPLE_RATE = 16000
	}
}