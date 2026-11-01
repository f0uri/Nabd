import React, { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from '../components/atoms/Avatar';
import { useAuthStore } from '../store/useAuthStore';
import type { LocalFile } from '../api/client';
import { colors, radius, shadow, size, space, type } from '../theme';

type Mode = 'login' | 'register';

/**
 * شاشة الدخول / إنشاء حساب
 * كل حساب كيتصنع من عند المستخدم: الاسم + البريد + كلمة السر + صورته الحقيقية.
 * ما كاينش حسابات جاهزة ولا شخصيات تجريبية.
 */
export const AuthScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const login = useAuthStore((s) => s.login);
  const register = useAuthStore((s) => s.register);
  const loading = useAuthStore((s) => s.loading);
  const error = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [photo, setPhoto] = useState<LocalFile | null>(null);

  const submit = async () => {
    if (mode === 'login') await login(email, password);
    else await register(name, email, password, photo ?? undefined);
  };

  /** صورة الحساب: من المعرض (بحال فيسبوك، أول خطوة فالتسجيل) */
  const pickPhoto = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) return;
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.85,
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (result.canceled || !result.assets?.length) return;
    const asset = result.assets[0];
    setPhoto({
      uri: asset.uri,
      name: asset.fileName ?? `avatar_${Date.now()}.jpg`,
      type: asset.mimeType ?? 'image/jpeg',
    });
  };

  const canSubmit = mode === 'login' ? email.length > 3 && password.length >= 6 : name.length >= 2 && email.length > 3 && password.length >= 6;

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[styles.content, { paddingTop: insets.top + space.xxl, paddingBottom: insets.bottom + space.xl }]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.brand}>
          <View style={styles.logo}>
            <Text style={styles.logoText}>ن</Text>
          </View>
          <Text style={styles.brandName}>نبض</Text>
        </View>
        <Text style={styles.tagline}>تواصل مع الناس اللي كتهمك — بلا تعقيد.</Text>

        <View style={styles.card}>
          <View style={styles.tabs}>
            <Pressable
              onPress={() => {
                setMode('login');
                clearError();
              }}
              style={[styles.tab, mode === 'login' && styles.tabActive]}
              accessibilityRole="tab"
              accessibilityState={{ selected: mode === 'login' }}
            >
              <Text style={[styles.tabText, mode === 'login' && styles.tabTextActive]}>دخول</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                setMode('register');
                clearError();
              }}
              style={[styles.tab, mode === 'register' && styles.tabActive]}
              accessibilityRole="tab"
              accessibilityState={{ selected: mode === 'register' }}
            >
              <Text style={[styles.tabText, mode === 'register' && styles.tabTextActive]}>حساب جديد</Text>
            </Pressable>
          </View>

          {mode === 'register' && (
            <Pressable
              onPress={pickPhoto}
              accessibilityRole="button"
              accessibilityLabel="أضف صورة الحساب"
              style={({ pressed }) => [styles.photoRow, pressed && { opacity: 0.8 }]}
            >
              <View style={styles.photoWrap}>
                <Avatar initial={name.trim().charAt(0) || 'أ'} color={colors.brand.primary} avatarUrl={photo?.uri} size={64} />
                <View style={styles.photoBadge}>
                  <Ionicons name={photo ? 'checkmark' : 'add'} size={16} color={colors.text.inverse} />
                </View>
              </View>
              <View style={styles.photoTextWrap}>
                <Text style={styles.photoTitle}>{photo ? 'صورة الحساب جاهزة' : 'أضف صورتك'}</Text>
                <Text style={styles.photoSub}>
                  {photo ? 'تقدر تبدّلها من الملف الشخصي فأي وقت' : 'صورة من المعرض — كتبان فكل منشور وتعليق'}
                </Text>
              </View>
            </Pressable>
          )}

          {mode === 'register' && (
            <View style={styles.field}>
              <Ionicons name="person-outline" size={18} color={colors.text.secondary} />
              <TextInput
                value={name}
                onChangeText={setName}
                placeholder="الاسم الكامل"
                placeholderTextColor={colors.text.secondary}
                style={styles.input}
                textAlign="right"
                autoComplete="name"
              />
            </View>
          )}

          <View style={styles.field}>
            <Ionicons name="mail-outline" size={18} color={colors.text.secondary} />
            <TextInput
              value={email}
              onChangeText={setEmail}
              placeholder="البريد الإلكتروني"
              placeholderTextColor={colors.text.secondary}
              style={styles.input}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              textAlign="right"
            />
          </View>

          <View style={styles.field}>
            <Ionicons name="lock-closed-outline" size={18} color={colors.text.secondary} />
            <TextInput
              value={password}
              onChangeText={setPassword}
              placeholder="كلمة السر"
              placeholderTextColor={colors.text.secondary}
              style={styles.input}
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              textAlign="right"
            />
            <Pressable onPress={() => setShowPassword((v) => !v)} hitSlop={8} accessibilityLabel="إظهار كلمة السر">
              <Ionicons name={showPassword ? 'eye-off-outline' : 'eye-outline'} size={18} color={colors.text.secondary} />
            </Pressable>
          </View>

          {!!error && (
            <View style={styles.errorBox}>
              <Ionicons name="alert-circle" size={16} color={colors.semantic.danger} />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          <Pressable
            onPress={submit}
            disabled={!canSubmit || loading}
            accessibilityRole="button"
            style={({ pressed }) => [
              styles.submit,
              (!canSubmit || loading) && styles.submitDisabled,
              pressed && { opacity: 0.85 },
            ]}
          >
            {loading ? (
              <ActivityIndicator color={colors.text.inverse} />
            ) : (
              <Text style={styles.submitText}>{mode === 'login' ? 'تسجيل الدخول' : 'إنشاء الحساب'}</Text>
            )}
          </Pressable>

          <View style={styles.demoBtn}>
            <Ionicons name="shield-checkmark-outline" size={15} color={colors.brand.primary} />
            <Text style={styles.demoText}>حسابك خاصك أنت — ما كاينش حسابات تجريبية</Text>
          </View>
        </View>

        <Text style={styles.hint}>
          سجّل حسابك، رفع صورتك، ومن بعد بدا نشر — كل منشور كيبان للحسابات الحقيقية اللي كتتبعهم.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  photoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingVertical: space.xs,
  },
  photoWrap: { position: 'relative' },
  photoBadge: {
    position: 'absolute',
    bottom: 0,
    left: '50%',
    marginLeft: -12,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: colors.brand.primary,
    borderWidth: 2,
    borderColor: colors.bg.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  photoTextWrap: { flex: 1, minWidth: 0 },
  photoTitle: { ...type.bodyStrong, color: colors.text.primary },
  photoSub: { ...type.caption, color: colors.text.secondary, marginTop: 1 },
  content: { paddingHorizontal: space.lg, gap: space.sm },
  brand: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.xs },
  logo: {
    width: 52,
    height: 52,
    borderRadius: 16,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow.card,
  },
  logoText: { color: colors.text.inverse, fontSize: 30, fontWeight: '800' },
  brandName: { ...type.display, color: colors.brand.primary },
  tagline: { ...type.bodySm, color: colors.text.secondary, textAlign: 'center', marginBottom: space.xs },
  card: {
    backgroundColor: colors.bg.surface,
    borderRadius: radius.lg,
    padding: space.md,
    gap: space.sm,
    ...shadow.card,
  },
  tabs: { flexDirection: 'row', backgroundColor: colors.bg.canvas, borderRadius: radius.pill, padding: 3 },
  tab: { flex: 1, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.pill },
  tabActive: { backgroundColor: colors.bg.surface, ...shadow.card },
  tabText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  tabTextActive: { color: colors.brand.primary },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    height: 46,
    borderWidth: 1,
    borderColor: colors.border.divider,
    borderRadius: radius.sm,
    paddingHorizontal: space.sm,
    backgroundColor: colors.bg.canvas,
  },
  input: { flex: 1, ...type.body, color: colors.text.primary, paddingVertical: 0 },
  errorBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#FDECEF',
    borderRadius: radius.sm,
    padding: space.xs,
  },
  errorText: { ...type.bodySm, color: '#8C1C2B', flex: 1 },
  submit: {
    height: 48,
    borderRadius: radius.sm,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  submitDisabled: { backgroundColor: colors.border.subtle },
  submitText: { ...type.bodyStrong, color: colors.text.inverse },
  demoBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5, paddingVertical: space.xs },
  demoText: { ...type.caption, color: colors.brand.primary },
  hint: { ...type.caption, color: colors.text.tertiary, textAlign: 'center', marginTop: space.xs },
  mono: { fontVariant: ['tabular-nums'] },
});

export default AuthScreen;
