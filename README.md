# expo-background-recording

Recording Audio in background for Android

## Installation

```bash
pnpm add expo-background-recording
```

### Configuration

Add the plugin to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": ["expo-background-recording"]
  }
}
```

Then run prebuild to apply the configuration:

```bash
npx expo prebuild
```

The plugin automatically configures:
- Required Android permissions (RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE)
- Foreground service declaration in AndroidManifest.xml

## API

### Methods

#### `startRecording(options?: RecordingOptions): Promise<void>`

Starts audio recording with optional configuration.

**Options:**
- `sampleRate?: number` - Sample rate in Hz (default: 44100)
- `channels?: number` - Number of channels (default: 2)
- `bitRate?: number` - Bit rate in bits per second (default: 128000)
- `outputFormat?: 'aac' | 'm4a' | '3gp'` - Output format (default: 'm4a')

#### `pauseRecording(): Promise<void>`

Pauses the current recording.

#### `resumeRecording(): Promise<void>`

Resumes a paused recording.

#### `stopRecording(): Promise<string>`

Stops recording and returns the file URI.

#### `getRecordingState(): Promise<RecordingState>`

Returns the current recording state.

**Returns:**
```typescript
{
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
}
```

### Events

#### `onRecordingStateChange`

Emitted when the recording state changes.

**Payload:**
```typescript
{
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
}
```

## Usage

```typescript
import { useState, useEffect } from 'react';
import ExpoBackgroundRecording, { RecordingStateChangePayload } from 'expo-background-recording';
import { Alert, PermissionsAndroid, Platform } from 'react-native';

const requestPermissions = async () => {
  if (Platform.OS === 'android') {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }
  return true;
};

const startRecording = async () => {
  const hasPermission = await requestPermissions();
  if (!hasPermission) {
    Alert.alert('Error', 'Microphone permission is required');
    return;
  }

  await ExpoBackgroundRecording.startRecording({
    sampleRate: 44100,
    channels: 2,
    bitRate: 128000,
    outputFormat: 'm4a',
  });
};

const stopRecording = async () => {
  const uri = await ExpoBackgroundRecording.stopRecording();
  console.log('Recording saved at:', uri);
};

const useRecordingState = () => {
  const [recordingState, setRecordingState] = useState({
    isRecording: false,
    isPaused: false,
    duration: 0,
  });

  useEffect(() => {
    const subscription = ExpoBackgroundRecording.addListener(
      'onRecordingStateChange',
      (event: RecordingStateChangePayload) => {
        setRecordingState(event);
      },
    );

    return () => subscription.remove();
  }, []);

  return recordingState;
};
```

## License

MIT
