import React, { useEffect, useState } from 'react';
import { AccessibilityInfo, Platform, StyleSheet, View } from 'react-native';
import { BlurView } from 'expo-blur';
import { glass } from '../theme';

/** Actual native blur behind the tabs. Opaque fallback for reduced transparency/web.
 * Mounted after the screen so Android captures the already-mounted list underneath.
 * Android uses Expo SDK 53's opt-in Dimezis backend, not an imitation alpha card.
 */
export default function GlassDock() {
  const [reduceTransparency, setReduceTransparency] = useState(true);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => {
      if (mounted) setReduceTransparency(enabled);
    }).catch(() => { /* Keep the opaque, accessible fallback. */ });
    const subscription = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setReduceTransparency);
    return () => { mounted = false; subscription.remove(); };
  }, []);
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFillObject}>
      {!reduceTransparency && Platform.OS !== 'web' ? (
        <BlurView tint="dark" intensity={45} experimentalBlurMethod="dimezisBlurView"
          blurReductionFactor={4} style={StyleSheet.absoluteFillObject} />
      ) : <View style={[StyleSheet.absoluteFillObject, { backgroundColor: glass.strong }]} />}
      <View style={[StyleSheet.absoluteFillObject, styles.tint]} />
    </View>
  );
}
const styles = StyleSheet.create({ tint: { backgroundColor: 'rgba(18,19,23,0.60)' } });
