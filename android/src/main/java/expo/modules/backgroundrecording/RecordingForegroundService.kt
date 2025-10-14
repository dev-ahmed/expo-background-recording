package expo.modules.backgroundrecording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class RecordingForegroundService : Service() {
    private val binder = LocalBinder()
    private var recordingState = RecordingState()
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String? = null

    companion object {
        private const val CHANNEL_ID = "RecordingServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    data class RecordingState(
        var isRecording: Boolean = false,
        var isPaused: Boolean = false,
        var startTime: Long = 0,
        var pauseTime: Long = 0,
        var totalPausedTime: Long = 0
    )

    inner class LocalBinder : Binder() {
        fun getService(): RecordingForegroundService = this@RecordingForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RecordingService", "Service onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RecordingService", "Service onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("RecordingService", "Service onBind called, returning binder")
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio recording"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Audio")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun startRecording(options: Map<String, Any>?, callback: (Boolean, String?) -> Unit) {
        Log.d("RecordingService", "startRecording called with options: $options")

        if (recordingState.isRecording) {
            Log.w("RecordingService", "Recording already in progress")
            callback(false, "Recording already in progress")
            return
        }

        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d("RecordingService", "Started foreground with notification")
            val outputDir = getExternalFilesDir(null)
            Log.d("RecordingService", "Output directory: $outputDir")
            
            val timestamp = System.currentTimeMillis()
            val format = (options?.get("outputFormat") as? String) ?: "m4a"
            outputFile = File(outputDir, "recording_$timestamp.$format").absolutePath
            Log.d("RecordingService", "Output file: $outputFile")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            Log.d("RecordingService", "MediaRecorder created")

            mediaRecorder?.apply {
                Log.d("RecordingService", "Configuring MediaRecorder...")
                setAudioSource(MediaRecorder.AudioSource.MIC)

                val outputFormat = when (format) {
                    "aac" -> MediaRecorder.OutputFormat.AAC_ADTS
                    "3gp" -> MediaRecorder.OutputFormat.THREE_GPP
                    else -> MediaRecorder.OutputFormat.MPEG_4
                }
                setOutputFormat(outputFormat)

                val audioEncoder = when (format) {
                    "3gp" -> MediaRecorder.AudioEncoder.AMR_NB
                    else -> MediaRecorder.AudioEncoder.AAC
                }
                setAudioEncoder(audioEncoder)

                (options?.get("sampleRate") as? Int)?.let { 
                    Log.d("RecordingService", "Setting sample rate: $it")
                    setAudioSamplingRate(it) 
                }
                (options?.get("bitRate") as? Int)?.let { 
                    Log.d("RecordingService", "Setting bit rate: $it")
                    setAudioEncodingBitRate(it) 
                }
                (options?.get("channels") as? Int)?.let { 
                    Log.d("RecordingService", "Setting channels: $it")
                    setAudioChannels(it) 
                }

                setOutputFile(outputFile)
                Log.d("RecordingService", "Preparing MediaRecorder...")
                prepare()
                Log.d("RecordingService", "Starting MediaRecorder...")
                start()
                Log.d("RecordingService", "MediaRecorder started successfully")
            }

            recordingState.isRecording = true
            recordingState.isPaused = false
            recordingState.startTime = System.currentTimeMillis()
            recordingState.totalPausedTime = 0

            Log.d("RecordingService", "Recording state updated, calling callback")
            callback(true, null)
        } catch (e: Exception) {
            Log.e("RecordingService", "Error starting recording", e)
            callback(false, e.message)
        }
    }

    fun pauseRecording(callback: (Boolean, String?) -> Unit) {
        if (!recordingState.isRecording) {
            callback(false, "No recording in progress")
            return
        }

        if (recordingState.isPaused) {
            callback(false, "Recording already paused")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                recordingState.isPaused = true
                recordingState.pauseTime = System.currentTimeMillis()
                callback(true, null)
            } else {
                callback(false, "Pause not supported on this Android version")
            }
        } catch (e: Exception) {
            callback(false, e.message)
        }
    }

    fun resumeRecording(callback: (Boolean, String?) -> Unit) {
        if (!recordingState.isRecording) {
            callback(false, "No recording in progress")
            return
        }

        if (!recordingState.isPaused) {
            callback(false, "Recording not paused")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                recordingState.totalPausedTime += System.currentTimeMillis() - recordingState.pauseTime
                recordingState.isPaused = false
                callback(true, null)
            } else {
                callback(false, "Resume not supported on this Android version")
            }
        } catch (e: Exception) {
            callback(false, e.message)
        }
    }

    fun stopRecording(callback: (String?, String?) -> Unit) {
        if (!recordingState.isRecording) {
            callback(null, "No recording in progress")
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            recordingState.isRecording = false
            recordingState.isPaused = false

            val result = outputFile
            outputFile = null
            callback(result, null)
        } catch (e: Exception) {
            callback(null, e.message)
        }
    }

    fun isRecording(): Boolean = recordingState.isRecording

    fun isPaused(): Boolean = recordingState.isPaused

    fun getDuration(): Long {
        if (!recordingState.isRecording) return 0
        val currentTime = if (recordingState.isPaused) recordingState.pauseTime else System.currentTimeMillis()
        return currentTime - recordingState.startTime - recordingState.totalPausedTime
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recordingState.isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
            }
            mediaRecorder = null
        }
    }
}
