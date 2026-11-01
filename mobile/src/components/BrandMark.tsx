import React from 'react';
import { Image } from 'react-native';

/** Local artwork: available offline, before login and with any data provider. */
export default function BrandMark({ size = 40 }: { size?: number }) {
  return (
    <Image
      source={require('../../assets/icon.png')}
      style={{ width: size, height: size }}
      resizeMode="contain"
      accessible
      accessibilityRole="image"
      accessibilityLabel="شعار نبض"
      accessibilityIgnoresInvertColors
    />
  );
}
