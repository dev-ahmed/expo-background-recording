import { useState, useEffect } from 'react';
import ExpoBackgroundRecording, { RecordingStateChangePayload } from '../src';
import { Alert, Button, Text, View, PermissionsAndroid, Platform } from 'react-native';
import { useAudioPlayer } from 'expo-audio';

export default function App() {
  const [recordingState, setRecordingState] = useState({
    isRecording: false,
    isPaused: false,
    duration: 0,
  });
  const [recordingUri, setRecordingUri] = useState<string | null>(null);
  const player = useAudioPlayer(recordingUri || '');

  useEffect(() => {
    const subscription = ExpoBackgroundRecording.addListener('onRecordingStateChange', (event: RecordingStateChangePayload) => {
      setRecordingState(event);
    });
    return () => subscription.remove();
  }, []);

  const requestPermissions = async () => {
    if (Platform.OS === 'android') {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
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
      console.log('result', result);
    } catch (error) {
      Alert.alert('Error', `Failed to start recording: ${error}`);
    }
    finally {
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
    if (player.playing) {
      player.pause();
    } else {
      player.play();
    }
  };

  const stopAudio = () => {
    player.seekTo(0);
    player.pause();
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
                title={player.playing ? 'Pause' : 'Play'}
                onPress={playAudio}
              />
              <Button
                title="Stop"
                onPress={stopAudio}
              />
            </View>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = {
  container: {
    marginTop:100,
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
