export type ExpoBackgroundRecordingModuleEvents = {
  onRecordingStateChange: (params: RecordingStateChangePayload) => void;
};

export type RecordingStateChangePayload = {
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
};

export type RecordingOptions = {
  sampleRate?: number;
  channels?: number;
  bitRate?: number;
  outputFormat?: 'aac' | 'm4a' | '3gp';
};

export type ExpoBackgroundRecordingViewProps = {
  style?: any;
};
