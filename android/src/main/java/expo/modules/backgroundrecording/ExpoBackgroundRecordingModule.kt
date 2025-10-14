package expo.modules.backgroundrecording

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoBackgroundRecordingModule : Module() {
  private var recordingService: RecordingForegroundService? = null
  private var serviceBound = false
  private val pendingActions = mutableListOf<() -> Unit>()

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      Log.d("ExpoBackgroundRecording", "Service connected")
      val binder = service as RecordingForegroundService.LocalBinder
      recordingService = binder.getService()
      serviceBound = true
      
      Log.d("ExpoBackgroundRecording", "Executing ${pendingActions.size} pending actions")
      // Execute pending actions
      pendingActions.forEach { it() }
      pendingActions.clear()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      Log.d("ExpoBackgroundRecording", "Service disconnected")
      recordingService = null
      serviceBound = false
    }
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoBackgroundRecording")

    Events("onRecordingStateChange")

    AsyncFunction("startRecording") { options: Map<String, Any>?, promise: Promise ->
      try {
        Log.d("ExpoBackgroundRecording", "startRecording called, serviceBound: $serviceBound")
        
        val action = {
          Log.d("ExpoBackgroundRecording", "Executing startRecording action")
          val service = recordingService
          if (service != null) {
            service.startRecording(options) { success, error ->
              Log.d("ExpoBackgroundRecording", "Recording callback: success=$success, error=$error")
              if (success) {
                sendEvent("onRecordingStateChange", mapOf(
                  "isRecording" to true,
                  "isPaused" to false,
                  "duration" to 0
                ))
                promise.resolve(null)
              } else {
                promise.reject("ERR_RECORDING", error ?: "Failed to start recording", null)
              }
            }
          } else {
            Log.e("ExpoBackgroundRecording", "Recording service is null in action")
            promise.reject("ERR_RECORDING", "Recording service not available", null)
          }
        }

        if (serviceBound && recordingService != null) {
          Log.d("ExpoBackgroundRecording", "Service already bound, executing immediately")
          action()
        } else {
          Log.d("ExpoBackgroundRecording", "Service not bound, queueing action and starting service")
          pendingActions.add(action)
          startForegroundService()
        }
      } catch (e: Exception) {
        Log.e("ExpoBackgroundRecording", "Exception in startRecording", e)
        promise.reject("ERR_RECORDING", "Failed to start recording: ${e.message}", e)
      }
    }

    AsyncFunction("pauseRecording") { promise: Promise ->
      try {
        recordingService?.pauseRecording { success, error ->
          if (success) {
            sendEvent("onRecordingStateChange", mapOf(
              "isRecording" to true,
              "isPaused" to true,
              "duration" to (recordingService?.getDuration() ?: 0L).toInt()
            ))
            promise.resolve(null)
          } else {
            promise.reject("ERR_RECORDING", error ?: "Failed to pause recording", null)
          }
        }
      } catch (e: Exception) {
        promise.reject("ERR_RECORDING", "Failed to pause recording: ${e.message}", e)
      }
    }

    AsyncFunction("resumeRecording") { promise: Promise ->
      try {
        recordingService?.resumeRecording { success, error ->
          if (success) {
            sendEvent("onRecordingStateChange", mapOf(
              "isRecording" to true,
              "isPaused" to false,
              "duration" to (recordingService?.getDuration() ?: 0L).toInt()
            ))
            promise.resolve(null)
          } else {
            promise.reject("ERR_RECORDING", error ?: "Failed to resume recording", null)
          }
        }
      } catch (e: Exception) {
        promise.reject("ERR_RECORDING", "Failed to resume recording: ${e.message}", e)
      }
    }

    AsyncFunction("stopRecording") { promise: Promise ->
      try {
        recordingService?.stopRecording { filePath, error ->
          if (filePath != null) {
            sendEvent("onRecordingStateChange", mapOf(
              "isRecording" to false,
              "isPaused" to false,
              "duration" to 0
            ))
            stopForegroundService()
            promise.resolve(filePath)
          } else {
            promise.reject("ERR_RECORDING", error ?: "Failed to stop recording", null)
          }
        }
      } catch (e: Exception) {
        promise.reject("ERR_RECORDING", "Failed to stop recording: ${e.message}", e)
      }
    }

    AsyncFunction("getRecordingState") { promise: Promise ->
      val service = recordingService
      if (service != null) {
        promise.resolve(mapOf(
          "isRecording" to service.isRecording(),
          "isPaused" to service.isPaused(),
          "duration" to service.getDuration()
        ))
      } else {
        promise.resolve(mapOf(
          "isRecording" to false,
          "isPaused" to false,
          "duration" to 0
        ))
      }
    }

    OnDestroy {
      stopForegroundService()
    }
  }

  private fun startForegroundService() {
    val context = appContext.reactContext
    if (context == null) {
      Log.e("ExpoBackgroundRecording", "React context is null, cannot start service")
      return
    }

    Log.d("ExpoBackgroundRecording", "Starting foreground service")
    val intent = Intent(context, RecordingForegroundService::class.java)

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
        Log.d("ExpoBackgroundRecording", "Started foreground service (API >= O)")
      } else {
        context.startService(intent)
        Log.d("ExpoBackgroundRecording", "Started service (API < O)")
      }

      val bound = context.bindService(
        intent,
        serviceConnection,
        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
      )
      Log.d("ExpoBackgroundRecording", "Bind service result: $bound")
    } catch (e: Exception) {
      Log.e("ExpoBackgroundRecording", "Error starting/binding service", e)
    }
  }

  private fun stopForegroundService() {
    val context = appContext.reactContext ?: return

    if (serviceBound) {
      context.unbindService(serviceConnection)
      serviceBound = false
    }

    val intent = Intent(context, RecordingForegroundService::class.java)
    context.stopService(intent)
  }
}
