// Reexport the native module. On web, it will be resolved to ExpoAndroidBackgroundRecordingModule.web.ts
// and on native platforms to ExpoAndroidBackgroundRecordingModule.ts
export { default } from './ExpoAndroidBackgroundRecordingModule';
export { default as ExpoAndroidBackgroundRecordingView } from './ExpoAndroidBackgroundRecordingView';
export * from  './ExpoAndroidBackgroundRecording.types';
