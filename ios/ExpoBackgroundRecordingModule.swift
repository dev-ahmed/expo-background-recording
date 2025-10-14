import ExpoModulesCore
import AVFoundation

public class ExpoBackgroundRecordingModule: Module {
  private var audioRecorder: AVAudioRecorder?
  private var recordingStartTime: Date?
  private var pausedDuration: TimeInterval = 0
  private var pauseStartTime: Date?

  public func definition() -> ModuleDefinition {
    Name("ExpoBackgroundRecording")

    Events("onRecordingStateChange")

    AsyncFunction("startRecording") { (options: [String: Any]?) in
      try self.startRecording(options: options)
    }

    AsyncFunction("pauseRecording") {
      try self.pauseRecording()
    }

    AsyncFunction("resumeRecording") {
      try self.resumeRecording()
    }

    AsyncFunction("stopRecording") { () -> String in
      return try self.stopRecording()
    }

    AsyncFunction("getRecordingState") { () -> [String: Any] in
      return self.getRecordingState()
    }
  }

  private func startRecording(options: [String: Any]?) throws {
    let audioSession = AVAudioSession.sharedInstance()
    try audioSession.setCategory(.playAndRecord, mode: .default)
    try audioSession.setActive(true)

    let settings: [String: Any] = [
      AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
      AVSampleRateKey: options?["sampleRate"] as? Int ?? 44100,
      AVNumberOfChannelsKey: options?["channels"] as? Int ?? 2,
      AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
    ]

    let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("recording_\(Date().timeIntervalSince1970).m4a")

    audioRecorder = try AVAudioRecorder(url: fileURL, settings: settings)
    audioRecorder?.record()
    recordingStartTime = Date()
    pausedDuration = 0

    sendEvent("onRecordingStateChange", [
      "isRecording": true,
      "isPaused": false,
      "duration": 0
    ])
  }

  private func pauseRecording() throws {
    guard let recorder = audioRecorder, recorder.isRecording else {
      throw Exception(name: "RecordingError", description: "No active recording")
    }

    recorder.pause()
    pauseStartTime = Date()

    sendEvent("onRecordingStateChange", [
      "isRecording": true,
      "isPaused": true,
      "duration": getCurrentDuration()
    ])
  }

  private func resumeRecording() throws {
    guard let recorder = audioRecorder else {
      throw Exception(name: "RecordingError", description: "No active recording")
    }

    if let pauseStart = pauseStartTime {
      pausedDuration += Date().timeIntervalSince(pauseStart)
      pauseStartTime = nil
    }

    recorder.record()

    sendEvent("onRecordingStateChange", [
      "isRecording": true,
      "isPaused": false,
      "duration": getCurrentDuration()
    ])
  }

  private func stopRecording() throws -> String {
    guard let recorder = audioRecorder else {
      throw Exception(name: "RecordingError", description: "No active recording")
    }

    recorder.stop()
    let fileURL = recorder.url.path

    audioRecorder = nil
    recordingStartTime = nil
    pausedDuration = 0
    pauseStartTime = nil

    sendEvent("onRecordingStateChange", [
      "isRecording": false,
      "isPaused": false,
      "duration": 0
    ])

    return fileURL
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
