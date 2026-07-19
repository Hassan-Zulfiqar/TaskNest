package com.hassan.tasknest.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class VoskModelManager(private val context: Context) {

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val _modelState = MutableStateFlow<VoskModelState>(VoskModelState.Idle)
	val modelState: StateFlow<VoskModelState> = _modelState

	private val loadMutex = Mutex()
	private var inProgressJob: Deferred<Model?>? = null

	private var loadedModel: Model? = null

	suspend fun ensureModelReady(): Model? {
		val existingJob = inProgressJob
		if (existingJob != null && existingJob.isActive) {
			return existingJob.await()
		}

		val newJob = scope.async {
			doEnsureModelReady()
		}
		inProgressJob = newJob

		return try {
			newJob.await()
		} finally {
			inProgressJob = null
		}
	}

	private suspend fun doEnsureModelReady(): Model? = withContext(Dispatchers.IO) {
		loadMutex.withLock {
			loadedModel?.let { return@withLock it }

			val targetDirectory = File(context.filesDir, MODEL_DIR_NAME)
			if (targetDirectory.exists() && targetDirectory.isDirectory && targetDirectory.listFiles()?.isNotEmpty() == true) {
				loadExistingModel(targetDirectory)?.let { return@withLock it }
			}

			if (!downloadAndPrepareModel(targetDirectory)) {
				return@withLock null
			}

			loadModelFromDirectory(targetDirectory)
		}
	}

	fun getLoadedModel(): Model? = loadedModel

	private fun loadExistingModel(targetDirectory: File): Model? {
		return try {
			loadModelFromDirectory(targetDirectory)
		} catch (_: IOException) {
			null
		}
	}

	private fun loadModelFromDirectory(targetDirectory: File): Model? {
		return try {
			val model = Model(targetDirectory.absolutePath)
			loadedModel = model
			_modelState.value = VoskModelState.Ready
			model
		} catch (e: Exception) {
			_modelState.value = VoskModelState.Error("Failed to load speech model: ${e.message}")
			targetDirectory.deleteRecursively()
			loadedModel = null
			null
		}
	}

	private fun downloadAndPrepareModel(targetDirectory: File): Boolean {
		_modelState.value = VoskModelState.Downloading(0)

		val zipFile = File(context.cacheDir, "$MODEL_DIR_NAME.zip")
		return try {
			downloadModelZip(zipFile)
			_modelState.value = VoskModelState.Unzipping
			unzipModel(zipFile, context.filesDir)
			true
		} catch (e: IOException) {
			_modelState.value = VoskModelState.Error("Failed to download speech model: ${e.message}")
			targetDirectory.deleteRecursively()
			false
		} catch (e: Exception) {
			_modelState.value = VoskModelState.Error("Failed to prepare speech model: ${e.message}")
			targetDirectory.deleteRecursively()
			false
		} finally {
			if (zipFile.exists()) {
				zipFile.delete()
			}
		}
	}

	private fun downloadModelZip(zipFile: File) {
		val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
			connectTimeout = 15_000
			readTimeout = 30_000
			instanceFollowRedirects = true
		}

		try {
			val responseCode = connection.responseCode
			if (responseCode !in 200..299) {
				throw IOException("HTTP $responseCode")
			}

			val contentLength = connection.contentLengthLong
			var lastProgressPercent = -1
			var totalBytesRead = 0L

			connection.inputStream.use { inputStream ->
				BufferedInputStream(inputStream).use { bufferedInput ->
					FileOutputStream(zipFile).use { fileOutputStream ->
						BufferedOutputStream(fileOutputStream).use { bufferedOutput ->
							val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
							while (true) {
								val bytesRead = bufferedInput.read(buffer)
								if (bytesRead == -1) {
									break
								}
								bufferedOutput.write(buffer, 0, bytesRead)
								totalBytesRead += bytesRead

								if (contentLength > 0) {
									val progressPercent = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
									if (progressPercent > lastProgressPercent) {
										lastProgressPercent = progressPercent
										_modelState.value = VoskModelState.Downloading(progressPercent)
									}
								}
							}
							bufferedOutput.flush()
						}
					}
				}
			}

			if (contentLength > 0 && lastProgressPercent < 100) {
				_modelState.value = VoskModelState.Downloading(100)
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun unzipModel(zipFile: File, destinationDir: File) {
		ZipInputStream(zipFile.inputStream().buffered()).use { zipInputStream ->
			while (true) {
				val entry = zipInputStream.nextEntry ?: break
				val outputFile = resolveZipEntry(destinationDir, entry.name)
				if (entry.isDirectory) {
					outputFile.mkdirs()
				} else {
					outputFile.parentFile?.mkdirs()
					FileOutputStream(outputFile).use { fileOutputStream ->
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						while (true) {
							val bytesRead = zipInputStream.read(buffer)
							if (bytesRead == -1) {
								break
							}
							fileOutputStream.write(buffer, 0, bytesRead)
						}
					}
				}
				zipInputStream.closeEntry()
			}
		}
	}

	private fun resolveZipEntry(destinationDir: File, entryName: String): File {
		val outputFile = File(destinationDir, entryName)
		val destinationPath = destinationDir.canonicalPath + File.separator
		val outputPath = outputFile.canonicalPath
		if (!outputPath.startsWith(destinationPath)) {
			throw IOException("Blocked zip entry outside destination: $entryName")
		}
		return outputFile
	}

	companion object {
		private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
		private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
	}
}