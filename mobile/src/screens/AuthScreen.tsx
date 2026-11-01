import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import type { TextInputProps } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from '../components/atoms/Avatar';
import BrandMark from '../components/BrandMark';
import AuthMascot from '../components/AuthMascot';
import { useAuthStore } from '../store/useAuthStore';
import { usernameBase } from '../utils/usernames';
import type { LocalFile } from '../api/client';
import { colors, glass, radius, shadow, space, type } from '../theme';

type Mode = 'login' | 'register';
function Field({
  label,
  icon,
  trailing,
  ...props
}: TextInputProps & {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>['name'];
  trailing?: React.ReactNode;
}) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={styles.fieldGroup}>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.field, focused && styles.fieldFocused]}>
        <Ionicons
          name={icon}
          size={20}
          color={focused ? colors.brand.primary : colors.text.secondary}
        />
        <TextInput
          {...props}
          accessibilityLabel={label}
          placeholderTextColor={colors.text.tertiary}
          style={styles.input}
          textAlign="right"
          autoCorrect={false}
          onFocus={(event) => {
            setFocused(true);
            props.onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            props.onBlur?.(event);
          }}
        />
        {trailing}
      </View>
    </View>
  );
}

export const AuthScreen: React.FC = () => {
  const insets = useSafeAreaInsets();
  const { login, register, loading, error, clearError } = useAuthStore();
  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [identity, setIdentity] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [photo, setPhoto] = useState<LocalFile | null>(null);
  const [slow, setSlow] = useState(false);
  const [picking, setPicking] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const registration = mode === 'register';
  const validEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identity.trim());
  const canSubmit =
    !loading &&
    !picking &&
    (registration
      ? name.trim().length >= 2 && validEmail && password.length >= 8
      : !!identity.trim() && password.length > 0);

  useEffect(() => {
    setSlow(false);
    if (!loading) return;
    const timer = setTimeout(() => setSlow(true), 8000);
    return () => clearTimeout(timer);
  }, [loading]);

  const submit = async () => {
    if (!canSubmit) return;
    clearError();
    if (registration)
      await register(name, identity, password, photo ?? undefined);
    else await login(identity, password);
  };
  const switchMode = (next: Mode) => {
    if (loading || picking) return;
    setMode(next);
    setPassword('');
    setShowPassword(false);
    setPasswordFocused(false);
    clearError();
  };
  const pickPhoto = async () => {
    if (loading || picking) return;
    setPicking(true);
    try {
      const permission =
        await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        Alert.alert(
          'إذن الوصول إلى الصور',
          'يمكنك السماح بالوصول من إعدادات الهاتف، أو إضافة صورتك لاحقاً.',
        );
        return;
      }
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
    } catch {
      Alert.alert(
        'تعذّر اختيار الصورة',
        'يرجى المحاولة مجدداً. يمكنك إنشاء حسابك دون صورة.',
      );
    } finally {
      setPicking(false);
    }
  };

  return (
    <KeyboardAvoidingView
      nativeID="nabd-login-responsive-v1"
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          styles.content,
          {
            paddingTop: insets.top + space.md,
            paddingBottom: insets.bottom + space.xl,
          },
        ]}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.hero}>
          <View style={styles.heroAccent} pointerEvents="none" />
          <View style={styles.brand}>
            <BrandMark size={52} />
            <Text style={styles.brandName}>نبض</Text>
          </View>
          <View style={styles.heroIntro}>
            <View style={styles.heroWords}>
              <Text style={styles.heading} accessibilityRole="header">
                {registration ? 'بدايتك هنا.' : 'أهلاً بعودتك.'}
              </Text>
              <Text style={styles.heroCaption}>
                {registration
                  ? 'أنشئ حساباً وابدأ التواصل.'
                  : 'مساحتك، وأصدقاؤك، وكل ما يهمك.'}
              </Text>
            </View>
            <AuthMascot covered={passwordFocused && !showPassword} />
          </View>
        </View>

        <View style={styles.card}>
          <View style={styles.tabs} accessibilityRole="tablist">
            {(['login', 'register'] as Mode[]).map((item) => (
              <Pressable
                key={item}
                onPress={() => switchMode(item)}
                disabled={loading || picking}
                style={[styles.tab, mode === item && styles.tabActive]}
                accessibilityRole="tab"
                accessibilityState={{
                  selected: mode === item,
                  disabled: loading || picking,
                }}
              >
                <Text
                  style={[
                    styles.tabText,
                    mode === item && styles.tabTextActive,
                  ]}
                >
                  {item === 'login' ? 'تسجيل الدخول' : 'حساب جديد'}
                </Text>
              </Pressable>
            ))}
          </View>

          {registration && (
            <Pressable
              onPress={pickPhoto}
              disabled={loading || picking}
              accessibilityRole="button"
              accessibilityLabel="اختيار صورة الحساب"
              style={styles.photoRow}
            >
              <View>
                <Avatar
                  initial={name.trim().charAt(0) || 'ن'}
                  color={colors.brand.primary}
                  avatarUrl={photo?.uri}
                  size={60}
                />
                <View style={styles.photoBadge}>
                  <Ionicons
                    name="camera"
                    size={14}
                    color={colors.text.inverse}
                  />
                </View>
              </View>
              <View style={styles.photoCopy}>
                <Text style={styles.label}>
                  {picking
                    ? 'جارٍ اختيار الصورة…'
                    : photo
                      ? 'تغيير الصورة'
                      : 'صورة الحساب'}
                </Text>
                <Text style={styles.caption}>
                  اختياري · يمكنك إضافتها لاحقاً
                </Text>
              </View>
              {photo && (
                <Ionicons
                  name="checkmark-circle"
                  size={22}
                  color={colors.semantic.success}
                />
              )}
            </Pressable>
          )}

          {registration && photo && (
            <Pressable
              onPress={() => setPhoto(null)}
              disabled={loading || picking}
              accessibilityRole="button"
              accessibilityLabel="إزالة الصورة المختارة"
            >
              <Text style={styles.link}>إزالة الصورة</Text>
            </Pressable>
          )}

          {registration && (
            <>
              <Field
                label="الاسم الكامل"
                icon="person-outline"
                value={name}
                onChangeText={setName}
                placeholder="كيف تحب أن نناديك؟"
                autoComplete="name"
                textContentType="name"
                maxLength={40}
                editable={!loading}
              />
              {!!name.trim() && (
                <View style={styles.usernamePreview}>
                  <Text style={styles.handle}>@{usernameBase(name)}</Text>
                  <Text style={styles.caption}>
                    اسم مقترح · قد يضاف رقم عند التكرار
                  </Text>
                </View>
              )}
            </>
          )}

          <Field
            label={
              registration
                ? 'البريد الإلكتروني'
                : 'البريد الإلكتروني أو اسم المستخدم'
            }
            icon={registration ? 'mail-outline' : 'at-outline'}
            value={identity}
            onChangeText={setIdentity}
            placeholder={
              registration ? 'name@example.com' : 'البريد أو @username'
            }
            keyboardType={registration ? 'email-address' : 'default'}
            autoCapitalize="none"
            maxLength={254}
            autoComplete={registration ? 'email' : 'username'}
            textContentType={registration ? 'emailAddress' : 'username'}
            editable={!loading}
          />

          <Field
            label="كلمة المرور"
            icon="lock-closed-outline"
            value={password}
            onChangeText={setPassword}
            placeholder={registration ? '8 أحرف على الأقل' : 'أدخل كلمة المرور'}
            secureTextEntry={!showPassword}
            autoCapitalize="none"
            maxLength={128}
            editable={!loading}
            autoComplete={registration ? 'new-password' : 'current-password'}
            textContentType={registration ? 'newPassword' : 'password'}
            returnKeyType="go"
            onFocus={() => setPasswordFocused(true)}
            onBlur={() => setPasswordFocused(false)}
            onSubmitEditing={submit}
            trailing={
              <Pressable
                onPress={() => setShowPassword((value) => !value)}
                disabled={loading}
                style={styles.eye}
                accessibilityRole="button"
                accessibilityLabel={
                  showPassword ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'
                }
              >
                <Ionicons
                  name={showPassword ? 'eye-off-outline' : 'eye-outline'}
                  size={21}
                  color={colors.text.secondary}
                />
              </Pressable>
            }
          />

          {!!error && (
            <View
              style={styles.errorBox}
              accessibilityRole="alert"
              accessibilityLiveRegion="assertive"
            >
              <Ionicons
                name="alert-circle-outline"
                size={19}
                color={colors.semantic.danger}
              />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          <Pressable
            onPress={submit}
            disabled={!canSubmit}
            accessibilityRole="button"
            accessibilityState={{ disabled: !canSubmit, busy: loading }}
            style={({ pressed }) => [
              styles.submit,
              !canSubmit && !loading && styles.submitDisabled,
              pressed && styles.pressed,
            ]}
          >
            {loading && (
              <ActivityIndicator color={colors.text.inverse} size="small" />
            )}
            <Text style={styles.submitText}>
              {loading
                ? registration
                  ? 'جارٍ إنشاء الحساب…'
                  : 'جارٍ تسجيل الدخول…'
                : registration
                  ? 'إنشاء حسابي'
                  : 'تسجيل الدخول'}
            </Text>
            {!loading && (
              <Ionicons
                name="arrow-back"
                size={20}
                color={colors.text.inverse}
              />
            )}
          </Pressable>
          {slow && (
            <Text style={styles.waiting} accessibilityLiveRegion="polite">
              التحقق يستغرق وقتاً أطول من المعتاد. طلبك قيد التنفيذ.
            </Text>
          )}
        </View>
        <Pressable
          onPress={() => switchMode(registration ? 'login' : 'register')}
          disabled={loading || picking}
          style={styles.switchLink}
          accessibilityRole="button"
        >
          <Text style={styles.switchText}>
            {registration ? 'لديك حساب بالفعل؟ ' : 'جديد على نبض؟ '}
            <Text style={styles.link}>
              {registration ? 'تسجيل الدخول' : 'إنشاء حساب'}
            </Text>
          </Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  content: {
    flexGrow: 1,
    paddingHorizontal: space.md,
    width: '100%',
    maxWidth: 520,
    alignSelf: 'center',
  },
  hero: {
    backgroundColor: colors.brand.dark,
    borderRadius: radius.lg,
    padding: space.xl,
    paddingBottom: space.huge,
    overflow: 'hidden',
  },
  heroAccent: {
    position: 'absolute',
    width: 240,
    height: 240,
    borderRadius: 120,
    backgroundColor: colors.brand.primary,
    left: -100,
    top: -100,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    marginBottom: space.lg,
  },
  brandName: { fontSize: 30, fontWeight: '800', color: colors.text.inverse },
  heroIntro: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  heroWords: { flex: 1 },
  heading: {
    fontSize: 29,
    fontWeight: '800',
    color: colors.text.inverse,
    textAlign: 'right',
  },
  heroCaption: {
    ...type.body,
    color: colors.text.inverse,
    textAlign: 'right',
    marginTop: space.xs,
  },
  card: {
    backgroundColor: glass.surface,
    borderWidth: 1,
    borderColor: glass.border,
    borderRadius: radius.lg,
    marginHorizontal: space.xs,
    marginTop: -space.xl,
    padding: space.lg,
    gap: space.md,
    ...shadow.card,
  },
  tabs: {
    flexDirection: 'row',
    borderRadius: radius.md,
    backgroundColor: colors.bg.canvas,
    padding: space.xxs,
    marginBottom: space.xxs,
  },
  tab: {
    flex: 1,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
  },
  tabActive: { backgroundColor: colors.bg.surface, ...shadow.card },
  tabText: { ...type.bodyStrong, color: colors.text.secondary },
  tabTextActive: { color: colors.brand.primary },
  photoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingBottom: space.xs,
  },
  photoBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 25,
    height: 25,
    borderRadius: 13,
    borderWidth: 2,
    borderColor: colors.bg.surface,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  photoCopy: { flex: 1, gap: space.xxs },
  caption: {
    ...type.caption,
    color: colors.text.secondary,
    textAlign: 'right',
  },
  fieldGroup: { gap: space.xs },
  label: { ...type.bodyStrong, color: colors.text.primary, textAlign: 'right' },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    minHeight: 54,
    borderWidth: 1,
    borderColor: colors.border.divider,
    borderRadius: radius.md,
    paddingHorizontal: space.sm,
    backgroundColor: colors.bg.surface,
  },
  fieldFocused: {
    borderColor: colors.border.focus,
    backgroundColor: colors.brand.soft,
  },
  input: {
    flex: 1,
    minWidth: 0,
    ...type.body,
    color: colors.text.primary,
    paddingVertical: space.sm,
  },
  eye: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  usernamePreview: {
    paddingHorizontal: space.sm,
    paddingVertical: space.xs,
    gap: space.xxs,
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.sm,
  },
  handle: {
    ...type.bodyStrong,
    color: colors.brand.primary,
    textAlign: 'left',
    writingDirection: 'ltr',
  },
  errorBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.sm,
    padding: space.sm,
  },
  errorText: {
    flex: 1,
    ...type.bodySm,
    color: colors.semantic.danger,
    textAlign: 'right',
  },
  submit: {
    minHeight: 54,
    flexDirection: 'row',
    gap: space.sm,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.md,
    backgroundColor: colors.brand.primary,
    marginTop: space.xxs,
  },
  submitDisabled: { opacity: 0.45 },
  pressed: { opacity: 0.85 },
  submitText: { ...type.bodyStrong, color: colors.text.inverse },
  waiting: {
    ...type.caption,
    color: colors.text.secondary,
    textAlign: 'center',
  },
  switchLink: { padding: space.lg, minHeight: 44, alignItems: 'center' },
  switchText: {
    ...type.bodySm,
    color: colors.text.secondary,
    textAlign: 'center',
  },
  link: { color: colors.brand.primary, fontWeight: '700' },
});
export default AuthScreen;
