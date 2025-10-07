package expo.modules.androidbackgroundrecording

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File

class ExpoAndroidBackgroundRecordingModule : Module() {
  private val TAG = "BackgroundRecording"
  private var mediaRecorder: MediaRecorder? = null
  private var outputFile: File? = null
  private var isRecording = false
  private var isPaused = false
  private var recordingStartTime = 0L
  private var pausedDuration = 0L
  private var pauseStartTime = 0L

  private val context: Context
    get() = appContext.reactContext ?: throw CodedException("React context not available")

  override fun definition() = ModuleDefinition {
    Name("ExpoAndroidBackgroundRecording")

    Events("onRecordingStateChange")

    AsyncFunction("startRecording") { options: Map<String, Any>? ->
      startRecordingImpl(options)
    }

    AsyncFunction("pauseRecording") {
      pauseRecordingImpl()
    }

    AsyncFunction("resumeRecording") {
      resumeRecordingImpl()
    }

    AsyncFunction("stopRecording") {
      stopRecordingImpl()
    }

    AsyncFunction("getRecordingState") {
      getRecordingStateImpl()
    }
  }

  private fun startRecordingImpl(options: Map<String, Any>?) {
    if (isRecording) {
      throw CodedException("Already recording")
    }

    try {
      Log.d(TAG, "Starting recording with options: $options")

      val sampleRate = (options?.get("sampleRate") as? Number)?.toInt() ?: 44100
      val channels = (options?.get("channels") as? Number)?.toInt() ?: 2
      val bitRate = (options?.get("bitRate") as? Number)?.toInt() ?: 128000
      val outputFormat = options?.get("outputFormat") as? String ?: "m4a"

      val extension = when (outputFormat) {
        "aac" -> ".aac"
        "3gp" -> ".3gp"
        else -> ".m4a"
      }

      outputFile = File.createTempFile("recording_", extension, context.cacheDir)

      mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(when (outputFormat) {
          "aac" -> MediaRecorder.OutputFormat.AAC_ADTS
          "3gp" -> MediaRecorder.OutputFormat.THREE_GPP
          else -> MediaRecorder.OutputFormat.MPEG_4
        })
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(sampleRate)
        setAudioChannels(channels)
        setAudioEncodingBitRate(bitRate)
        setOutputFile(outputFile!!.absolutePath)
        prepare()
        start()
      }

      isRecording = true
      isPaused = false
      recordingStartTime = System.currentTimeMillis()
      pausedDuration = 0L

      startForegroundService()

      sendRecordingStateEvent()

      Log.d(TAG, "Recording started successfully: ${outputFile!!.absolutePath}")
    } catch (e: Exception) {
      Log.e(TAG, "Error starting recording", e)
      cleanup()
      throw CodedException("Failed to start recording: ${e.message}")
    }
  }

  private fun pauseRecordingImpl() {
    if (!isRecording || isPaused) {
      throw CodedException("Not currently recording or already paused")
    }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        mediaRecorder?.pause()
        isPaused = true
        pauseStartTime = System.currentTimeMillis()
        sendRecordingStateEvent()
        Log.d(TAG, "Recording paused")
      } else {
        throw CodedException("Pause not supported on this Android version")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error pausing recording", e)
      throw CodedException("Failed to pause recording: ${e.message}")
    }
  }

  private fun resumeRecordingImpl() {
    if (!isRecording || !isPaused) {
      throw CodedException("Not currently paused")
    }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        mediaRecorder?.resume()
        pausedDuration += System.currentTimeMillis() - pauseStartTime
        isPaused = false
        sendRecordingStateEvent()
        Log.d(TAG, "Recording resumed")
      } else {
        throw CodedException("Resume not supported on this Android version")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error resuming recording", e)
      throw CodedException("Failed to resume recording: ${e.message}")
    }
  }

  private fun stopRecordingImpl(): String {
    if (!isRecording) {
      throw CodedException("Not currently recording")
    }

    try {
      mediaRecorder?.stop()
      mediaRecorder?.release()
      mediaRecorder = null

      val filePath = outputFile?.absolutePath ?: ""

      isRecording = false
      isPaused = false

      stopForegroundService()

      sendRecordingStateEvent()

      Log.d(TAG, "Recording stopped: $filePath")

      return filePath
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping recording", e)
      cleanup()
      throw CodedException("Failed to stop recording: ${e.message}")
    }
  }

  private fun getRecordingStateImpl(): Map<String, Any> {
    val duration = if (isRecording && !isPaused) {
      (System.currentTimeMillis() - recordingStartTime - pausedDuration) / 1000
    } else if (isPaused) {
      (pauseStartTime - recordingStartTime - pausedDuration) / 1000
    } else {
      0L
    }

    return mapOf(
      "isRecording" to isRecording,
      "isPaused" to isPaused,
      "duration" to duration
    )
  }

  private fun sendRecordingStateEvent() {
    val state = getRecordingStateImpl()
    sendEvent("onRecordingStateChange", state)
  }

  private fun startForegroundService() {
    try {
      val intent = Intent(context, RecordingForegroundService::class.java)
      intent.action = RecordingForegroundService.ACTION_START

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }

      Log.d(TAG, "Foreground service started")
    } catch (e: Exception) {
      Log.e(TAG, "Error starting foreground service", e)
    }
  }

  private fun stopForegroundService() {
    try {
      val intent = Intent(context, RecordingForegroundService::class.java)
      intent.action = RecordingForegroundService.ACTION_STOP
      context.startService(intent)
      Log.d(TAG, "Foreground service stopped")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping foreground service", e)
    }
  }

  private fun cleanup() {
    try {
      mediaRecorder?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error releasing media recorder", e)
    }
    mediaRecorder = null
    isRecording = false
    isPaused = false
  }

  override fun onDestroy() {
    cleanup()
    stopForegroundService()
    super.onDestroy()
  }
}
