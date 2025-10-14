import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoBackgroundRecordingViewProps } from './ExpoBackgroundRecording.types';

const NativeView: React.ComponentType<ExpoBackgroundRecordingViewProps> =
  requireNativeView('ExpoBackgroundRecording');

export default function ExpoBackgroundRecordingView(props: ExpoBackgroundRecordingViewProps) {
  return <NativeView {...props} />;
}
