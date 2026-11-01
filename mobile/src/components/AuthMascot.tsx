import React from 'react';
import { StyleSheet, View } from 'react-native';

/** Lightweight adaptation of the supplied Flutter character: no timers, canvas,
 * native libraries or idle animations competing with password verification. */
export default function AuthMascot({ covered }: { covered: boolean }) {
  return (
    <View
      style={styles.scene}
      accessible={false}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <View style={styles.body} />
      <View style={styles.neck} />
      <View style={styles.face}>
        <View style={styles.hair} />
        <View style={[styles.eye, styles.eyeLeft]}>
          <View style={styles.pupil} />
        </View>
        <View style={[styles.eye, styles.eyeRight]}>
          <View style={styles.pupil} />
        </View>
        <View style={styles.smile} />
      </View>
      {covered && (
        <>
          <View style={[styles.hand, styles.handLeft]} />
          <View style={[styles.hand, styles.handRight]} />
        </>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  scene: { width: 88, height: 106, flexShrink: 0, alignItems: 'center' },
  body: {
    position: 'absolute',
    bottom: 0,
    width: 80,
    height: 36,
    borderTopLeftRadius: 36,
    borderTopRightRadius: 36,
    backgroundColor: '#DCEBFF',
  },
  neck: {
    position: 'absolute',
    top: 62,
    width: 22,
    height: 20,
    borderRadius: 7,
    backgroundColor: '#F5C79E',
  },
  face: {
    top: 5,
    width: 62,
    height: 67,
    borderRadius: 29,
    backgroundColor: '#FFE0BD',
    overflow: 'hidden',
  },
  hair: {
    position: 'absolute',
    top: -5,
    left: -2,
    width: 68,
    height: 25,
    borderRadius: 22,
    backgroundColor: '#192B4D',
    transform: [{ rotate: '-10deg' }],
  },
  eye: {
    position: 'absolute',
    top: 29,
    width: 14,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  eyeLeft: { left: 12 },
  eyeRight: { right: 12 },
  pupil: { width: 6, height: 7, borderRadius: 4, backgroundColor: '#192B4D' },
  smile: {
    position: 'absolute',
    bottom: 10,
    left: 24,
    width: 14,
    height: 7,
    borderBottomWidth: 2,
    borderBottomColor: '#A56245',
    borderRadius: 7,
  },
  hand: {
    position: 'absolute',
    top: 31,
    width: 32,
    height: 27,
    borderRadius: 12,
    backgroundColor: '#F5C79E',
    borderWidth: 1,
    borderColor: '#E8B487',
  },
  handLeft: { left: 11, transform: [{ rotate: '-12deg' }] },
  handRight: { right: 11, transform: [{ rotate: '12deg' }] },
});
