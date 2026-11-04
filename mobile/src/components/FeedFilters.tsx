import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { colors, control, radius, size, space, type } from '../theme';
import { FEED_FILTERS, type FeedFilter } from '../utils/feedFilters';

export default function FeedFilters({ value, onChange }: {
  value: FeedFilter; onChange: (filter: FeedFilter) => void;
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false}
      style={styles.scroll} contentContainerStyle={styles.row}>
      {FEED_FILTERS.map(({ key, label }) => (
        <Pressable key={key} accessibilityRole="tab" accessibilityLabel={label}
          accessibilityState={{ selected: value === key }} onPress={() => onChange(key)}
          style={({ pressed }) => [styles.chip, value === key && styles.selected, pressed && styles.pressed]}>
          <Text style={[styles.label, value === key && styles.selectedLabel]}>{label}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  scroll: { flexGrow: 0, flexShrink: 0 },
  row: { paddingHorizontal: space.md, paddingBottom: space.sm, gap: space.xs },
  chip: { minHeight: size.touchTarget, minWidth: 66, paddingHorizontal: space.md,
    paddingVertical: space.xs, borderRadius: radius.sm, backgroundColor: control.surface,
    justifyContent: 'center', alignItems: 'center' },
  selected: { backgroundColor: colors.text.primary },
  label: { ...type.bodyStrong, color: colors.text.primary },
  selectedLabel: { color: colors.bg.canvas },
  pressed: { opacity: 0.75 },
});
