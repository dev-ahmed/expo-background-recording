import { useState, useEffect } from 'react';
import ExpoBackgroundRecording, { RecordingStateChangePayload } from '../src';
import {
  Alert,
  Button,
  Text,
  View,
  PermissionsAndroid,
  Platform,
  AppState,
} from 'react-native';
import { useAudioPlayer } from 'expo-audio';

export default function App() {
  const [recordingState, setRecordingState] = useState({
    isRecording: false,
    isPaused: false,
    duration: 0,
  });
  const [recordingUri, setRecordingUri] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const player = useAudioPlayer();

  console.log('recordingUri', recordingUri, player.playing, recordingState);

  useEffect(() => {
    if (recordingUri) {
      player.replace(recordingUri);
    }
  }, [recordingUri]);

  useEffect(() => {
    const interval = setInterval(() => {
      setIsPlaying(player.playing);
    }, 100);
    return () => clearInterval(interval);
  }, [player]);

  const syncRecordingState = async () => {
    try {
      const state = await ExpoBackgroundRecording.getRecordingState();
      setRecordingState(state);
    } catch (error) {
      console.error('Failed to sync recording state:', error);
    }
  };

  useEffect(() => {
    syncRecordingState();

    const subscription = ExpoBackgroundRecording.addListener(
      'onRecordingStateChange',
      (event: RecordingStateChangePayload) => {
        setRecordingState(event);
      },
    );

    const appStateSubscription = AppState.addEventListener(
      'change',
      (nextAppState) => {
        if (nextAppState === 'active') {
          syncRecordingState();
        }
      },
    );

    return () => {
      subscription.remove();
      appStateSubscription.remove();
    };
  }, []);

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
    try {
      const hasPermission = await requestPermissions();
      if (!hasPermission) {
        Alert.alert('Error', 'Microphone permission is required');
        return;
      }

      console.log('startRecording started');
      const result = await ExpoBackgroundRecording.startRecording({
        sampleRate: 44100,
        channels: 2,
        bitRate: 128000,
        outputFormat: 'm4a',
      });
      console.log('startRecording promise resolved:', result);

      await new Promise((resolve) => setTimeout(resolve, 500));

      const state = await ExpoBackgroundRecording.getRecordingState();
      console.log('State after 500ms:', state);
      setRecordingState(state);
    } catch (error) {
      Alert.alert('Error', `Failed to start recording: ${error}`);
    } finally {
      console.log('startRecording finished');
    }
  };

  const pauseRecording = async () => {
    try {
      await ExpoBackgroundRecording.pauseRecording();
    } catch (error) {
      Alert.alert('Error', `Failed to pause recording: ${error}`);
    }
  };

  const resumeRecording = async () => {
    try {
      await ExpoBackgroundRecording.resumeRecording();
    } catch (error) {
      Alert.alert('Error', `Failed to resume recording: ${error}`);
    }
  };

  const stopRecording = async () => {
    try {
      const uri = await ExpoBackgroundRecording.stopRecording();
      setRecordingUri(uri);
      Alert.alert('Recording Saved', `File saved at: ${uri}`);
    } catch (error) {
      Alert.alert('Error', `Failed to stop recording: ${error}`);
    }
  };

  const playAudio = () => {
    if (isPlaying) {
      player.pause();
      setIsPlaying(false);
    } else {
      player.play();
      setIsPlaying(true);
    }
  };

  const stopAudio = () => {
    player.seekTo(0);
    player.pause();
    setIsPlaying(false);
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.header}>Background Recording Test</Text>

        <View style={styles.statusContainer}>
          <Text style={styles.statusLabel}>Status:</Text>
          <Text style={styles.statusValue}>
            {recordingState.isRecording
              ? recordingState.isPaused
                ? 'Paused'
                : 'Recording'
              : 'Idle'}
          </Text>
        </View>

        <View style={styles.statusContainer}>
          <Text style={styles.statusLabel}>Duration:</Text>
          <Text style={styles.statusValue}>{recordingState.duration}s</Text>
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Start Recording"
            onPress={startRecording}
            disabled={recordingState.isRecording}
          />
          <Button
            title="Pause"
            onPress={pauseRecording}
            disabled={!recordingState.isRecording || recordingState.isPaused}
          />
          <Button
            title="Resume"
            onPress={resumeRecording}
            disabled={!recordingState.isPaused}
          />
          <Button
            title="Stop Recording"
            onPress={stopRecording}
            disabled={!recordingState.isRecording}
          />
        </View>

        {recordingUri && (
          <View style={styles.resultContainer}>
            <Text style={styles.resultLabel}>Last Recording:</Text>
            <Text style={styles.resultPath}>{recordingUri}</Text>
            <Text style={styles.audioDuration}>
              Duration: {player.duration.toFixed(2)}s
            </Text>

            <View style={styles.audioControls}>
              <Button
                title={isPlaying ? 'Pause' : 'Play'}
                onPress={playAudio}
              />
              <Button title="Stop" onPress={stopAudio} />
            </View>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = {
  container: {
    marginTop: 100,
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    flex: 1,
    padding: 20,
  },
  header: {
    fontSize: 28,
    fontWeight: 'bold' as const,
    marginBottom: 30,
    textAlign: 'center' as const,
  },
  statusContainer: {
    flexDirection: 'row' as const,
    marginBottom: 15,
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
  },
  statusLabel: {
    fontSize: 18,
    fontWeight: '600' as const,
    marginRight: 10,
  },
  statusValue: {
    fontSize: 18,
    color: '#666',
  },
  buttonContainer: {
    marginTop: 20,
    gap: 10,
  },
  resultContainer: {
    marginTop: 30,
    padding: 15,
    backgroundColor: '#e8f5e9',
    borderRadius: 8,
  },
  resultLabel: {
    fontSize: 16,
    fontWeight: '600' as const,
    marginBottom: 5,
  },
  resultPath: {
    fontSize: 12,
    color: '#666',
  },
  audioDuration: {
    fontSize: 16,
    fontWeight: '600' as const,
    marginTop: 10,
    color: '#333',
  },
  audioControls: {
    marginTop: 15,
    flexDirection: 'row' as const,
    gap: 10,
  },
};
