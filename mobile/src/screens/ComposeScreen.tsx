import React, { useState } from 'react';
import {
  ActivityIndicator,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import Avatar from '../components/atoms/Avatar';
import { useFeedStore } from '../store/useFeedStore';
import { CURRENT_USER } from '../api/mockApi';
import { colors, radius, shadow, size, space, type } from '../theme';
import type { LocalFile } from '../api/client';
import type { Privacy } from '../types';

interface ComposeScreenProps {
  visible: boolean;
  onClose: () => void;
}

const PRIVACY_OPTIONS: Array<{ key: Privacy; label: string; icon: 'globe-outline' | 'people-outline' | 'lock-closed-outline' }> = [
  { key: 'public', label: 'العامة', icon: 'globe-outline' },
  { key: 'friends', label: 'الأصدقاء', icon: 'people-outline' },
  { key: 'private', label: 'أنا فقط', icon: 'lock-closed-outline' },
];

/**
 * شاشة إنشاء منشور
 * الصورة كترفع مباشرة للخادم، والخادم كيدزها لسحابة تلغرام ويرجع file_id،
 * والتطبيق كيستعمل رابط البث /media/:id — يعني التيليفون ما كيحتاجش يبقى مفتوح.
 */
export const ComposeScreen: React.FC<ComposeScreenProps> = ({ visible, onClose }) => {
  const insets = useSafeAreaInsets();
  const createPost = useFeedStore((s) => s.createPost);
  const publishing = useFeedStore((s) => s.publishing);

  const [text, setText] = useState('');
  const [privacy, setPrivacy] = useState<Privacy>('public');
  const [file, setFile] = useState<LocalFile | null>(null);
  const [aspectRatio, setAspectRatio] = useState(4 / 3);

  const reset = () => {
    setText('');
    setFile(null);
    setPrivacy('public');
    setAspectRatio(4 / 3);
  };

  const close = () => {
    if (publishing) return;
    reset();
    onClose();
  };

  const pickImage = async (fromCamera: boolean) => {
    const permission = fromCamera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permission.granted) {
      useFeedStore.getState().showToast('خاصنا الإذن للوصول للصور', 'error');
      return;
    }

    const result = fromCamera
      ? await ImagePicker.launchCameraAsync({ quality: 0.85 })
      : await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85 });

    if (result.canceled || !result.assets?.length) return;

    const asset = result.assets[0];
    setFile({
      uri: asset.uri,
      name: asset.fileName ?? `photo_${Date.now()}.jpg`,
      type: asset.mimeType ?? 'image/jpeg',
    });
    if (asset.width && asset.height) setAspectRatio(asset.width / asset.height);
  };

  const publish = async () => {
    const ok = await createPost({ text, privacy, file: file ?? undefined, aspectRatio });
    if (ok) {
      reset();
      onClose();
    }
  };

  const canPublish = (text.trim().length > 0 || !!file) && !publishing;

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={close}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={insets.top}
      >
        <View style={[styles.header, { paddingTop: Math.max(insets.top, space.sm) }]}>
          <Pressable onPress={close} hitSlop={8} accessibilityLabel="إغلاق" style={styles.headerBtn}>
            <Ionicons name="close" size={24} color={colors.text.primary} />
          </Pressable>
          <Text style={styles.headerTitle}>إنشاء منشور</Text>
          <Pressable
            onPress={publish}
            disabled={!canPublish}
            accessibilityRole="button"
            accessibilityLabel="نشر"
            style={({ pressed }) => [styles.publishBtn, !canPublish && styles.publishBtnDisabled, pressed && { opacity: 0.8 }]}
          >
            {publishing ? (
              <ActivityIndicator size="small" color={colors.text.inverse} />
            ) : (
              <Text style={styles.publishText}>نشر</Text>
            )}
          </Pressable>
        </View>

        <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent} keyboardShouldPersistTaps="handled">
          <View style={styles.authorRow}>
            <Avatar initial={CURRENT_USER.initial} color={CURRENT_USER.color} avatarUrl={CURRENT_USER.avatarUrl} size={size.avatarMd} />
            <View>
              <Text style={styles.authorName}>{CURRENT_USER.name}</Text>
              <View style={styles.privacyRow}>
                {PRIVACY_OPTIONS.map((option) => {
                  const active = privacy === option.key;
                  return (
                    <Pressable
                      key={option.key}
                      onPress={() => setPrivacy(option.key)}
                      accessibilityRole="button"
                      accessibilityState={{ selected: active }}
                      style={[styles.privacyChip, active && styles.privacyChipActive]}
                    >
                      <Ionicons
                        name={option.icon}
                        size={12}
                        color={active ? colors.brand.primary : colors.text.secondary}
                      />
                      <Text style={[styles.privacyText, active && styles.privacyTextActive]}>{option.label}</Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>
          </View>

          <TextInput
            value={text}
            onChangeText={setText}
            placeholder="فيمَ تفكر، يوسف؟"
            placeholderTextColor={colors.text.secondary}
            multiline
            autoFocus
            maxLength={2000}
            style={styles.input}
            textAlignVertical="top"
          />

          {file && (
            <View style={styles.previewWrap}>
              <Image source={{ uri: file.uri }} style={[styles.preview, { aspectRatio }]} resizeMode="cover" />
              <Pressable style={styles.removeMedia} onPress={() => setFile(null)} accessibilityLabel="حذف الصورة">
                <Ionicons name="close" size={16} color={colors.text.inverse} />
              </Pressable>
            </View>
          )}
        </ScrollView>

        <View style={[styles.toolbar, { paddingBottom: Math.max(insets.bottom, space.sm) }]}>
          <Pressable style={styles.tool} onPress={() => pickImage(false)} accessibilityLabel="اختيار صورة من المعرض">
            <Ionicons name="image-outline" size={20} color="#E9710F" />
            <Text style={styles.toolLabel}>المعرض</Text>
          </Pressable>
          <Pressable style={styles.tool} onPress={() => pickImage(true)} accessibilityLabel="التقاط صورة">
            <Ionicons name="camera-outline" size={20} color="#1877F2" />
            <Text style={styles.toolLabel}>الكاميرا</Text>
          </Pressable>
          <View style={styles.storageHint}>
            <Ionicons name="cloud-upload-outline" size={13} color={colors.text.tertiary} />
            <Text style={styles.storageHintText}>الصور تُخزَّن على سحابة تلغرام</Text>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.surface },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingBottom: space.sm,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.subtle,
  },
  headerBtn: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { ...type.title, color: colors.text.primary },
  publishBtn: {
    minWidth: 74,
    height: 36,
    borderRadius: radius.pill,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.md,
  },
  publishBtnDisabled: { backgroundColor: colors.border.subtle },
  publishText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
  body: { flex: 1 },
  bodyContent: { padding: space.md, gap: space.sm },
  authorRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  authorName: { ...type.bodyStrong, color: colors.text.primary },
  privacyRow: { flexDirection: 'row', gap: 6, marginTop: 4 },
  privacyChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: space.xs,
    paddingVertical: 3,
    borderRadius: radius.pill,
    backgroundColor: colors.bg.canvas,
  },
  privacyChipActive: { backgroundColor: colors.brand.soft },
  privacyText: { ...type.caption, color: colors.text.secondary },
  privacyTextActive: { color: colors.brand.primary, fontFamily: type.bodyStrong.fontFamily },
  input: { ...type.body, color: colors.text.primary, minHeight: 120, lineHeight: 24 },
  previewWrap: { borderRadius: radius.md, overflow: 'hidden', position: 'relative', backgroundColor: colors.bg.canvas },
  preview: { width: '100%' },
  removeMedia: {
    position: 'absolute',
    top: space.xs,
    left: space.xs,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingHorizontal: space.md,
    paddingTop: space.sm,
    borderTopWidth: size.hairline,
    borderTopColor: colors.border.subtle,
    backgroundColor: colors.bg.surface,
    ...shadow.card,
  },
  tool: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  toolLabel: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  storageHint: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 4 },
  storageHintText: { ...type.caption, color: colors.text.tertiary },
});

export default ComposeScreen;
