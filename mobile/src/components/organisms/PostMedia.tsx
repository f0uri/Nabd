import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { colors, type } from '../../theme';
import FeedVideo from './FeedVideo';
import type { Media } from '../../types';

interface PostMediaProps {
  media: Media;
}

/**
 * ORGANISM — PostMedia
 * - في الإنتاج: <Image> من CDN أو من وسيط تلغرام (رابط /media/:id).
 * - في وضع mock: مشاهد مرسومة بـ Views متجهية (بلا إيموجي وبلا صور خارجية).
 * - نحافظ دائماً على aspectRatio حتى لا تحدث قفزة في التخطيط عند وصول الصورة.
 */
const SunriseScene: React.FC = () => (
  <View style={[styles.scene, { backgroundColor: '#2B1B57' }]}>
    <View style={styles.skyOverlay} />
    <View style={styles.sunGlow} />
    <View style={styles.sun} />
    <View style={[styles.mountain, styles.mountainBack]} />
    <View style={[styles.mountain, styles.mountainFront]} />
  </View>
);

const CoffeeScene: React.FC = () => (
  <View style={[styles.scene, { backgroundColor: '#8C6039' }]}>
    <View style={styles.woodLine1} />
    <View style={styles.woodLine2} />
    <View style={styles.plate} />
    <View style={styles.cup} />
    <View style={styles.coffee} />
    <View style={styles.handle} />
  </View>
);

const SCENES: Record<string, React.FC> = {
  'mock://sunrise': SunriseScene,
  'mock://coffee': CoffeeScene,
};

export const PostMedia: React.FC<PostMediaProps> = ({ media }) => {
  if (media.kind === 'video') return <FeedVideo key={media.uri} media={media} />;
  const Scene = SCENES[media.uri];

  if (Scene) {
    return (
      <View style={{ width: '100%', aspectRatio: media.aspectRatio }}>
        <Scene />
        {!!media.alt && <Text style={styles.alt}>{media.alt}</Text>}
      </View>
    );
  }

  return (
    <Image
      source={{ uri: media.uri }}
      style={[styles.image, { aspectRatio: media.aspectRatio }]}
      accessibilityLabel={media.alt}
      resizeMode="cover"
    />
  );
};

const styles = StyleSheet.create({
  image: { width: '100%', backgroundColor: colors.bg.skeletonBase },
  scene: { flex: 1, overflow: 'hidden' },
  alt: {
    position: 'absolute',
    bottom: 8,
    left: 10,
    ...type.caption,
    color: 'rgba(255,255,255,0.75)',
  },

  /* شروق */
  skyOverlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: '45%',
    backgroundColor: 'rgba(255,160,90,0.28)',
  },
  sun: {
    position: 'absolute',
    right: '22%',
    bottom: '34%',
    width: 78,
    height: 78,
    borderRadius: 39,
    backgroundColor: '#FFE7B8',
  },
  sunGlow: {
    position: 'absolute',
    right: '15%',
    bottom: '27%',
    width: 130,
    height: 130,
    borderRadius: 65,
    backgroundColor: 'rgba(255,208,138,0.16)',
  },
  mountain: {
    position: 'absolute',
    bottom: 0,
    width: 0,
    height: 0,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
  },
  mountainBack: {
    right: '8%',
    borderLeftWidth: 120,
    borderRightWidth: 120,
    borderBottomWidth: 150,
    borderBottomColor: '#5B4477',
  },
  mountainFront: {
    right: '-6%',
    borderLeftWidth: 150,
    borderRightWidth: 150,
    borderBottomWidth: 110,
    borderBottomColor: '#1B1130',
  },

  /* قهوة */
  woodLine1: { position: 'absolute', left: 0, right: 0, top: '22%', height: 1.5, backgroundColor: 'rgba(90,60,32,0.35)' },
  woodLine2: { position: 'absolute', left: 0, right: 0, top: '66%', height: 1.5, backgroundColor: 'rgba(90,60,32,0.35)' },
  plate: {
    position: 'absolute',
    alignSelf: 'center',
    top: '18%',
    width: 168,
    height: 168,
    borderRadius: 84,
    backgroundColor: '#F4EFE7',
  },
  cup: {
    position: 'absolute',
    alignSelf: 'center',
    top: '26%',
    width: 128,
    height: 128,
    borderRadius: 64,
    backgroundColor: '#FFFFFF',
  },
  coffee: {
    position: 'absolute',
    alignSelf: 'center',
    top: '34%',
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: '#3A1C0B',
  },
  handle: {
    position: 'absolute',
    alignSelf: 'center',
    top: '38%',
    width: 168,
    height: 92,
    borderRadius: 92,
    borderWidth: 10,
    borderColor: 'rgba(255,255,255,0.85)',
    borderRightColor: 'transparent',
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    transform: [{ rotate: '-90deg' }],
    opacity: 0.55,
  },
});

export default PostMedia;
