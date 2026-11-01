import { Platform } from 'react-native';
import * as Updates from 'expo-updates';
import { createUpdateStore } from '../updates/createUpdateStore';

export const useUpdateStore = createUpdateStore({
  enabled: !__DEV__ && Platform.OS !== 'web' && Updates.isEnabled,
  check: Updates.checkForUpdateAsync,
  fetch: Updates.fetchUpdateAsync,
  reload: Updates.reloadAsync,
});
