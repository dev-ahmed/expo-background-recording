import ExpoModulesCore
import AVFoundation

public class ExpoBackgroundRecordingModule: Module {
  private var audioRecorder: AVAudioRecorder?
  private var recordingStartTime: Date?
  private var pausedDuration: TimeInterval = 0
  private var pauseStartTime: Date?
  
  // Completion handler type matching Android pattern
  typealias RecordingCompletion = (Bool, String?) -> Void
  typealias StopCompletion = (String?, String?) -> Void

  public func definition() -> ModuleDefinition {
    Name("ExpoBackgroundRecording")

    Events("onRecordingStateChange")

    AsyncFunction("startRecording") { (options: [String: Any]?, promise: Promise) in
      self.startRecording(options: options) { success, error in
        if success {
          self.sendEvent("onRecordingStateChange", [
            "isRecording": true,
            "isPaused": false,
            "duration": 0
          ])
          promise.resolve(nil)
        } else {
          promise.reject("ERR_RECORDING", error ?? "Failed to start recording")
        }
      }
    }

    AsyncFunction("pauseRecording") { (promise: Promise) in
      self.pauseRecording { success, error in
        if success {
          self.sendEvent("onRecordingStateChange", [
            "isRecording": true,
            "isPaused": true,
            "duration": self.getCurrentDuration()
          ])
          promise.resolve(nil)
        } else {
          promise.reject("ERR_RECORDING", error ?? "Failed to pause recording")
        }
      }
    }

    AsyncFunction("resumeRecording") { (promise: Promise) in
      self.resumeRecording { success, error in
        if success {
          self.sendEvent("onRecordingStateChange", [
            "isRecording": true,
            "isPaused": false,
            "duration": self.getCurrentDuration()
          ])
          promise.resolve(nil)
        } else {
          promise.reject("ERR_RECORDING", error ?? "Failed to resume recording")
        }
      }
    }

    AsyncFunction("stopRecording") { (promise: Promise) in
      self.stopRecording { filePath, error in
        if let path = filePath {
          self.sendEvent("onRecordingStateChange", [
            "isRecording": false,
            "isPaused": false,
            "duration": 0
          ])
          promise.resolve(path)
        } else {
          promise.reject("ERR_RECORDING", error ?? "Failed to stop recording")
        }
      }
    }

    AsyncFunction("getRecordingState") { () -> [String: Any] in
      return self.getRecordingState()
    }
  }

  private func startRecording(options: [String: Any]?, completion: @escaping RecordingCompletion) {
    AVAudioSession.sharedInstance().requestRecordPermission { granted in
      if !granted {
        completion(false, "Microphone permission not granted")
        return
      }

      do {
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
        try audioSession.setActive(true)

        let settings: [String: Any] = [
          AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
          AVSampleRateKey: options?["sampleRate"] as? Int ?? 44100,
          AVNumberOfChannelsKey: options?["channels"] as? Int ?? 2,
          AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]

        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("recording_\(Date().timeIntervalSince1970).m4a")

        self.audioRecorder = try AVAudioRecorder(url: fileURL, settings: settings)
        guard let recorder = self.audioRecorder else {
          completion(false, "Failed to initialize audio recorder")
          return
        }

        recorder.prepareToRecord()
        let success = recorder.record()

        if !success {
          completion(false, "Failed to start recording")
          return
        }

        self.recordingStartTime = Date()
        self.pausedDuration = 0

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
          if let recorder = self.audioRecorder, recorder.isRecording {
            completion(true, nil)
          } else {
            completion(false, "Recording failed to start")
          }
        }
      } catch {
        completion(false, "Error starting recording: \(error.localizedDescription)")
      }
    }
  }

  private func pauseRecording(completion: @escaping RecordingCompletion) {
    guard let recorder = audioRecorder, recorder.isRecording else {
      completion(false, "No active recording")
      return
    }

    recorder.pause()
    pauseStartTime = Date()

    // Verify pause after a brief delay
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
      if let recorder = self.audioRecorder, !recorder.isRecording {
        completion(true, nil)
      } else {
        completion(false, "Failed to pause recording")
      }
    }
  }

  private func resumeRecording(completion: @escaping RecordingCompletion) {
    guard let recorder = audioRecorder else {
      completion(false, "No active recording")
      return
    }

    // Verify it's actually paused before resuming
    guard !recorder.isRecording else {
      completion(false, "Recording is not paused")
      return
    }

    if let pauseStart = pauseStartTime {
      pausedDuration += Date().timeIntervalSince(pauseStart)
      pauseStartTime = nil
    }

    recorder.record()

    // Verify resume after a brief delay
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
      if let recorder = self.audioRecorder, recorder.isRecording {
        completion(true, nil)
      } else {
        completion(false, "Failed to resume recording")
      }
    }
  }

  private func stopRecording(completion: @escaping StopCompletion) {
    guard let recorder = audioRecorder else {
      completion(nil, "No active recording")
      return
    }

    let fileURL = recorder.url.path
    recorder.stop()

    // Verify stop and cleanup after a brief delay
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
      if let recorder = self.audioRecorder, !recorder.isRecording {
        self.audioRecorder = nil
        self.recordingStartTime = nil
        self.pausedDuration = 0
        self.pauseStartTime = nil
        
        completion(fileURL, nil)
      } else {
        completion(nil, "Failed to stop recording")
      }
    }
  }

  private func getRecordingState() -> [String: Any] {
    guard let recorder = audioRecorder else {
      return [
        "isRecording": false,
        "isPaused": false,
        "duration": 0
      ]
    }

    return [
      "isRecording": true,
      "isPaused": !recorder.isRecording,
      "duration": getCurrentDuration()
    ]
  }

  private func getCurrentDuration() -> Double {
    guard let startTime = recordingStartTime else { return 0 }

    let elapsed = Date().timeIntervalSince(startTime) - pausedDuration

    if let pauseStart = pauseStartTime {
      return elapsed - Date().timeIntervalSince(pauseStart)
    }

    return elapsed
  }
}
