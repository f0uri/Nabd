const base = require('./app.json').expo;

/** The project ID is public configuration. Publication credentials NEVER belong here. */
module.exports = ({ config = base } = {}) => {
  const projectId = (process.env.EXPO_EAS_PROJECT_ID || config.extra?.eas?.projectId || '').trim();
  if (projectId && !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(projectId)) {
    throw new Error('EXPO_EAS_PROJECT_ID must be the real EAS project UUID.');
  }
  return {
    ...base,
    ...config,
    // Native dependency/config changes create a different runtime, not an unsafe JS-only update.
    runtimeVersion: { policy: 'fingerprint' },
    updates: projectId ? {
      ...config.updates,
      enabled: true,
      url: `https://u.expo.dev/${projectId}`,
      checkAutomatically: 'ON_ERROR_RECOVERY',
      fallbackToCacheTimeout: 0,
      requestHeaders: { 'expo-channel-name': 'production' },
    } : { enabled: false },
    extra: {
      ...base.extra,
      ...config.extra,
      ...(projectId ? { eas: { ...config.extra?.eas, projectId } } : {}),
    },
  };
};
