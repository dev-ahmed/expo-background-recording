const { withAndroidManifest } = require('@expo/config-plugins');

const withBackgroundRecording = (config) => {
  return withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults.manifest;

    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }

    const permissions = [
      'android.permission.RECORD_AUDIO',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_MICROPHONE',
    ];

    const existingPermissions = androidManifest['uses-permission'].map(
      (perm) => perm.$['android:name']
    );

    permissions.forEach((permission) => {
      if (!existingPermissions.includes(permission)) {
        androidManifest['uses-permission'].push({
          $: {
            'android:name': permission,
          },
        });
      }
    });

    const mainApplication = androidManifest.application?.[0];

    if (mainApplication) {
      if (!mainApplication.service) {
        mainApplication.service = [];
      }

      const serviceExists = mainApplication.service.some(
        (service) =>
          service.$['android:name'] ===
          'expo.modules.backgroundrecording.RecordingForegroundService'
      );

      if (!serviceExists) {
        mainApplication.service.push({
          $: {
            'android:name':
              'expo.modules.backgroundrecording.RecordingForegroundService',
            'android:enabled': 'true',
            'android:foregroundServiceType': 'microphone',
            'android:exported': 'false',
          },
        });
      }
    }

    return config;
  });
};

module.exports = withBackgroundRecording;
