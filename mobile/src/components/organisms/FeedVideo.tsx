import React, { useEffect, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { VideoView, useVideoPlayer } from 'expo-video';
import { colors, space, type } from '../../theme';
import type { Media } from '../../types';

function Player({ media }: { media: Media }) {
  const player = useVideoPlayer(media.uri);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') player.pause();
    });
    return () => subscription.remove();
  }, [player]);
  return <VideoView player={player} style={StyleSheet.absoluteFillObject}
    nativeControls contentFit="contain" surfaceType="textureView" accessibilityLabel={media.alt ?? 'فيديو المنشور'} />;
}

/** Do not create/auto-play a native player for every item while scrolling.
 * Explicit tap loads controls; native resources are released on unmount.
 */
export default function FeedVideo({ media }: { media: Media }) {
  const [opened, setOpened] = useState(false);
  return (
    <View style={[styles.frame, { aspectRatio: media.aspectRatio || 16 / 9 }]}>
      {opened ? <Player media={media} /> : (
        <Pressable onPress={() => setOpened(true)} accessibilityRole="button" accessibilityLabel="فتح فيديو المنشور"
          style={styles.open}>
          <Ionicons name="play-circle" size={56} color={colors.text.primary} />
          <Text style={styles.label}>فتح الفيديو</Text>
        </Pressable>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  frame: { width: '100%', backgroundColor: '#000000' },
  open: { flex: 1, minHeight: 80, alignItems: 'center', justifyContent: 'center', gap: space.xs },
  label: { ...type.bodySm, color: colors.text.primary },
});
