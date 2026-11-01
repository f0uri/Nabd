import { Platform } from 'react-native';
import * as Updates from 'expo-updates';
import { createUpdateStore } from '../updates/createUpdateStore';

export const useUpdateStore = createUpdateStore({
  diagnostics: {
    runtimeVersion: Updates.runtimeVersion,
    channel: Updates.channel,
    updateId: Updates.updateId,
    embedded: Updates.isEmbeddedLaunch,
    emergency: Updates.isEmergencyLaunch,
    dataSource: ['auto', 'graphql', 'mock', 'telegram'].includes(
      process.env.EXPO_PUBLIC_DATA_SOURCE ?? 'auto',
    )
      ? (process.env.EXPO_PUBLIC_DATA_SOURCE ?? 'auto')
      : 'unknown',
  },
  enabled: !__DEV__ && Platform.OS !== 'web' && Updates.isEnabled,
  check: Updates.checkForUpdateAsync,
  fetch: Updates.fetchUpdateAsync,
  reload: Updates.reloadAsync,
});
