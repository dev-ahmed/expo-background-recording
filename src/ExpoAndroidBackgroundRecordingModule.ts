import { NativeModule, requireNativeModule } from 'expo';

import { ExpoAndroidBackgroundRecordingModuleEvents, RecordingOptions } from './ExpoAndroidBackgroundRecording.types';

declare class ExpoAndroidBackgroundRecordingModule extends NativeModule<ExpoAndroidBackgroundRecordingModuleEvents> {
  startRecording(options?: RecordingOptions): Promise<void>;
  pauseRecording(): Promise<void>;
  resumeRecording(): Promise<void>;
  stopRecording(): Promise<string>;
  getRecordingState(): Promise<{ isRecording: boolean; isPaused: boolean; duration: number }>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoAndroidBackgroundRecordingModule>('ExpoAndroidBackgroundRecording');
