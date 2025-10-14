import { requireNativeModule } from 'expo-modules-core';

import {
  ExpoBackgroundRecordingModuleEvents,
  RecordingOptions,
} from './ExpoBackgroundRecording.types';

export interface ExpoBackgroundRecordingModule {
  startRecording(options?: RecordingOptions): Promise<void>;
  pauseRecording(): Promise<void>;
  resumeRecording(): Promise<void>;
  stopRecording(): Promise<string>;
  getRecordingState(): Promise<{
    isRecording: boolean;
    isPaused: boolean;
    duration: number;
  }>;
  addListener(
    eventName: keyof ExpoBackgroundRecordingModuleEvents,
    listener: (event: any) => void,
  ): { remove: () => void };
  removeListeners(count: number): void;
}

console.log('globalThis.expo?.modules:', globalThis.expo?.modules);
console.log(
  'Available expo modules:',
  Object.keys(globalThis.expo?.modules || {}),
);

export default requireNativeModule<ExpoBackgroundRecordingModule>(
  'ExpoBackgroundRecording',
);
