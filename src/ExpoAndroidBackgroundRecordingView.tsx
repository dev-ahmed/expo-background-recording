import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoAndroidBackgroundRecordingViewProps } from './ExpoAndroidBackgroundRecording.types';

const NativeView: React.ComponentType<ExpoAndroidBackgroundRecordingViewProps> =
  requireNativeView('ExpoAndroidBackgroundRecording');

export default function ExpoAndroidBackgroundRecordingView(props: ExpoAndroidBackgroundRecordingViewProps) {
  return <NativeView {...props} />;
}
