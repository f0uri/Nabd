import { colors, control, glass } from '../theme';
import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Keyboard,
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
import { LinearGradient } from 'expo-linear-gradient';
import Svg, { Circle, Path } from 'react-native-svg';

type Mode = 'login' | 'register';
function Field({
  label,
  icon,
  trailing,
  message,
  ltr,
  ...props
}: TextInputProps & {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>['name'];
  trailing?: React.ReactNode;
  message?: string;
  ltr?: boolean;
}) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={styles.fieldGroup}>
      <Text style={styles.label}>{label}</Text>
      <View
        style={[
          styles.field,
          focused && styles.fieldFocused,
          !!message && styles.fieldInvalid,
        ]}
      >
        <Ionicons
          name={icon}
          size={20}
          color={message ? colors.semantic.danger : focused ? colors.brand.primary : colors.text.secondary}
        />
        <TextInput
          {...props}
          accessibilityLabel={label}
          placeholderTextColor="#98A5B7"
          style={[styles.input, ltr && { writingDirection: 'ltr' }]}
          textAlign={ltr ? 'left' : 'right'}
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
      {!!message && (
        <Text
          style={[styles.caption, { color: colors.semantic.danger }]}
          accessibilityLiveRegion="polite"
        >
          {message}
        </Text>
      )}
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
  const [focusedField, setFocusedField] = useState<string | null>(null);
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const touch = (key: string) => {
    setTouched((value) => ({ ...value, [key]: true }));
    setFocusedField(null);
  };
  const gaze = focusedField
    ? 1 -
      2 *
        Math.min(
          1,
          (focusedField === 'name'
            ? name.length
            : focusedField === 'identity'
              ? identity.length
              : password.length) / (focusedField === 'identity' ? 26 : 18),
        )
    : 0;
  const score = password
    ? Number(password.length >= 8) +
      Number(/[A-Z]/.test(password) && /[a-z]/.test(password)) +
      Number(/\d/.test(password)) +
      Number(/[^A-Za-z0-9]/.test(password))
    : 0;
  const strengthLabel = ['ضعيفة', 'ضعيفة', 'متوسطة', 'جيدة', 'قوية'][score];
  const strengthColor = [colors.semantic.danger, colors.semantic.danger, '#E8A53E', colors.brand.primary, '#1FA971'][
    score
  ];
  useEffect(() => {
    const show = Keyboard?.addListener('keyboardDidShow', () =>
      setKeyboardOpen(true),
    );
    const hide = Keyboard?.addListener('keyboardDidHide', () =>
      setKeyboardOpen(false),
    );
    return () => {
      show?.remove();
      hide?.remove();
    };
  }, []);
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
    Keyboard?.dismiss();
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
    setFocusedField(null);
    setTouched({});
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
      nativeID="nabd-full-auth-v2"
      testID="nabd-midnight-auth-v1"
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        showsVerticalScrollIndicator={false}
      >
        <LinearGradient
          colors={[colors.brand.primary, '#32121D']}
          start={{ x: 1, y: 0 }}
          end={{ x: 0, y: 1 }}
          style={styles.hero}
        >
          <View
            pointerEvents="none"
            style={StyleSheet.absoluteFill}
            accessible={false}
            importantForAccessibility="no-hide-descendants"
          >
            <Svg
              width="100%"
              height="100%"
              viewBox="0 0 400 400"
              preserveAspectRatio="none"
            >
              <Circle cx="380" cy="32" r="192" fill="#2A7BEF" opacity="0.55" />
              <Circle cx="8" cy="380" r="96" fill="white" opacity="0.06" />
              <Path
                d="M0 256H56L72 242L88 274L104 216L120 268L136 256H256L272 242L288 274L304 216L320 268L336 256H400"
                fill="none"
                stroke="white"
                strokeOpacity="0.12"
                strokeWidth="2"
                strokeLinejoin="round"
              />
            </Svg>
          </View>
          <View
            style={[
              styles.heroContent,
              { paddingTop: insets.top + (keyboardOpen ? 8 : 18) },
            ]}
          >
            {!keyboardOpen && (
              <>
                <View style={styles.brand}>
                  <BrandMark size={52} />
                  <Text style={styles.brandName}>نبض</Text>
                </View>
                <Text style={styles.heading} accessibilityRole="header">
                  {registration ? 'بدايتك هنا.' : 'أهلاً بعودتك.'}
                </Text>
                <Text style={styles.heroCaption}>
                  {registration
                    ? 'أنشئ حساباً وابدأ التواصل.'
                    : 'مساحتك، وأصدقاؤك، وكل ما يهمك.'}
                </Text>
              </>
            )}
            <View style={styles.mascot}>
              <AuthMascot
                covered={passwordFocused && !showPassword}
                size={keyboardOpen ? 112 : 150}
                look={gaze}
                busy={loading}
                unhappy={!!error}
              />
            </View>
          </View>
        </LinearGradient>
        <View nativeID="nabd-login-responsive-v1" style={styles.formArea}>
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
              <>
                <Pressable
                  onPress={pickPhoto}
                  disabled={loading || picking}
                  accessibilityRole="button"
                  accessibilityLabel="اختيار صورة الحساب"
                  style={styles.photoRow}
                >
                  <View style={styles.avatarFrame}>
                    <Avatar
                      initial={name.trim().charAt(0) || 'ن'}
                      color={colors.brand.dark}
                      avatarUrl={photo?.uri}
                      size={68}
                    />
                    <View style={styles.photoBadge}>
                      {picking ? (
                        <ActivityIndicator size="small" color="white" />
                      ) : (
                        <Ionicons name="camera" size={15} color="white" />
                      )}
                    </View>
                  </View>
                  <View style={styles.photoCopy}>
                    <Text style={styles.label}>صورة الحساب</Text>
                    <Text style={styles.caption}>
                      اختياري، يمكنك إضافتها لاحقاً
                    </Text>
                  </View>
                </Pressable>
                {photo && (
                  <Pressable
                    onPress={() => setPhoto(null)}
                    disabled={loading || picking}
                    accessibilityRole="button"
                    accessibilityLabel="إزالة الصورة المختارة"
                  >
                    <Text style={styles.link}>إزالة الصورة</Text>
                  </Pressable>
                )}
                <Field
                  label="الاسم الكامل"
                  icon="person-outline"
                  value={name}
                  onChangeText={setName}
                  placeholder="كيف تحب أن نناديك؟"
                  autoComplete="name"
                  textContentType="name"
                  maxLength={40}
                  editable={!loading && !picking}
                  onFocus={() => setFocusedField('name')}
                  onBlur={() => touch('name')}
                  message={
                    touched.name && name.trim().length < 2
                      ? 'اكتب اسمك الكامل'
                      : undefined
                  }
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
              placeholder="name@example.com"
              keyboardType={registration ? 'email-address' : 'default'}
              autoCapitalize="none"
              maxLength={254}
              autoComplete={registration ? 'email' : 'username'}
              textContentType={registration ? 'emailAddress' : 'username'}
              editable={!loading && !picking}
              ltr
              onFocus={() => setFocusedField('identity')}
              onBlur={() => touch('identity')}
              message={
                touched.identity &&
                (!identity.trim() || (registration && !validEmail))
                  ? 'تحقق من البريد أو اسم المستخدم'
                  : undefined
              }
            />
            <Field
              label="كلمة المرور"
              icon="lock-closed-outline"
              value={password}
              onChangeText={setPassword}
              placeholder="••••••••"
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              maxLength={128}
              editable={!loading && !picking}
              autoComplete={registration ? 'new-password' : 'current-password'}
              textContentType={registration ? 'newPassword' : 'password'}
              returnKeyType="go"
              onFocus={() => {
                setPasswordFocused(true);
                setFocusedField('password');
              }}
              onBlur={() => {
                setPasswordFocused(false);
                touch('password');
              }}
              onSubmitEditing={submit}
              message={
                touched.password &&
                (!password || (registration && password.length < 8))
                  ? 'كلمة المرور مطلوبة، 8 أحرف على الأقل للحساب الجديد'
                  : undefined
              }
              trailing={
                <Pressable
                  onPress={() => setShowPassword((v) => !v)}
                  disabled={loading || picking}
                  style={styles.eye}
                  accessibilityRole="button"
                  accessibilityLabel={
                    showPassword ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'
                  }
                >
                  <Ionicons
                    name={showPassword ? 'eye-off-outline' : 'eye-outline'}
                    size={22}
                    color={colors.text.secondary}
                  />
                </Pressable>
              }
            />
            {registration ? (
              <View
                style={styles.strength}
                accessibilityLabel={`قوة كلمة المرور: ${strengthLabel}`}
              >
                <View style={styles.strengthBars}>
                  {[1, 2, 3, 4].map((i) => (
                    <View
                      key={i}
                      style={[
                        styles.strengthBar,
                        {
                          backgroundColor:
                            i <= score ? strengthColor : glass.border,
                        },
                      ]}
                    />
                  ))}
                </View>
                <Text
                  style={[
                    styles.caption,
                    { color: password ? strengthColor : colors.text.secondary },
                  ]}
                >
                  {password
                    ? `قوة كلمة المرور: ${strengthLabel}`
                    : '8 أحرف على الأقل، والأفضل مزيج من الحروف والأرقام'}
                </Text>
              </View>
            ) : (
              <Pressable
                onPress={() =>
                  Alert.alert(
                    'استعادة كلمة المرور',
                    'استعادة كلمة المرور غير متوفرة حالياً. لم نغيّر كلمة مرور حسابك.',
                  )
                }
                disabled={loading || picking}
                accessibilityRole="button"
                style={styles.forgot}
              >
                <Text style={styles.link}>نسيت كلمة المرور؟</Text>
              </Pressable>
            )}
            {!!error && (
              <View
                style={styles.errorBox}
                accessibilityRole="alert"
                accessibilityLiveRegion="assertive"
              >
                <Ionicons
                  name="alert-circle-outline"
                  size={20}
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
              <LinearGradient
                colors={[colors.brand.primary, colors.brand.pressed]}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
                style={styles.buttonFill}
              >
                {loading && <ActivityIndicator color="white" size="small" />}
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
                  <Ionicons name="arrow-back" size={21} color="white" />
                )}
              </LinearGradient>
            </Pressable>
            {slow && (
              <Text style={styles.waiting} accessibilityLiveRegion="polite">
                طلبك قيد التنفيذ. لا حاجة للضغط مجدداً.
              </Text>
            )}
          </View>
          <Pressable
            onPress={() => switchMode(registration ? 'login' : 'register')}
            disabled={loading || picking}
            style={[styles.switchLink, { paddingBottom: insets.bottom + 28 }]}
            accessibilityRole="button"
          >
            <Text style={styles.switchText}>
              {registration ? 'لديك حساب بالفعل؟ ' : 'جديد على نبض؟ '}
              <Text style={styles.link}>
                {registration ? 'تسجيل الدخول' : 'إنشاء حساب'}
              </Text>
            </Text>
          </Pressable>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  content: { flexGrow: 1, paddingBottom: 8 },
  hero: {
    borderBottomLeftRadius: 40,
    borderBottomRightRadius: 40,
    overflow: 'hidden',
  },
  heroContent: {
    paddingHorizontal: 24,
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
  },
  brand: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
    alignSelf: 'flex-start',
    marginBottom: 20,
  },
  brandName: { fontSize: 28, fontWeight: '800', color: 'white' },
  heading: {
    fontSize: 32,
    lineHeight: 42,
    fontWeight: '800',
    color: 'white',
    textAlign: 'center',
  },
  heroCaption: {
    fontSize: 15.5,
    lineHeight: 24,
    fontWeight: '500',
    color: '#FFFFFFD1',
    textAlign: 'center',
    marginTop: 6,
  },
  mascot: { alignItems: 'center', marginTop: 6 },
  formArea: {
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
    marginTop: -36,
  },
  card: {
    marginHorizontal: 20,
    padding: 20,
    paddingBottom: 22,
    borderRadius: 32,
    backgroundColor: glass.surface,
    gap: 14,
    shadowColor: '#32121D',
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.14,
    shadowRadius: 25,
    elevation: 6,
  },
  tabs: {
    flexDirection: 'row',
    backgroundColor: control.surface,
    padding: 6,
    borderRadius: 18,
    marginBottom: 6,
    minHeight: 56,
  },
  tab: {
    flex: 1,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 13,
  },
  tabActive: {
    backgroundColor: glass.selected,
    shadowColor: '#32121D',
    shadowOpacity: 0.09,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },
  tabText: { fontSize: 16, fontWeight: '500', color: colors.text.secondary },
  tabTextActive: { fontWeight: '800', color: colors.brand.dark },
  photoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginBottom: 4,
  },
  avatarFrame: {
    padding: 4,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: glass.stroke,
    borderRadius: 23,
  },
  photoBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 27,
    height: 27,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: 'white',
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  photoCopy: { flex: 1, gap: 4 },
  caption: {
    fontSize: 13,
    lineHeight: 20,
    color: colors.text.secondary,
    textAlign: 'right',
  },
  fieldGroup: { gap: 7 },
  label: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.text.primary,
    textAlign: 'right',
  },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 56,
    borderWidth: 1.3,
    borderColor: glass.border,
    borderRadius: 16,
    paddingHorizontal: 14,
    backgroundColor: control.surface,
  },
  fieldFocused: { borderColor: colors.brand.primary, backgroundColor: colors.brand.soft },
  fieldInvalid: { borderColor: colors.semantic.danger, backgroundColor: colors.statusSurface.danger },
  input: {
    flex: 1,
    minWidth: 0,
    fontSize: 15.5,
    color: colors.text.primary,
    paddingVertical: 14,
  },
  eye: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  usernamePreview: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 2,
    backgroundColor: control.surface,
    borderRadius: 12,
  },
  handle: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.brand.dark,
    textAlign: 'left',
    writingDirection: 'ltr',
  },
  strength: { gap: 7 },
  strengthBars: { flexDirection: 'row', gap: 5 },
  strengthBar: { height: 4, borderRadius: 4, flex: 1 },
  forgot: {
    alignSelf: 'flex-end',
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 6,
    marginTop: -8,
  },
  errorBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.statusSurface.danger,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  errorText: {
    flex: 1,
    fontSize: 13.5,
    lineHeight: 21,
    fontWeight: '600',
    color: colors.semantic.danger,
    textAlign: 'right',
  },
  submit: { minHeight: 56, borderRadius: 16, overflow: 'hidden', marginTop: 4 },
  buttonFill: {
    minHeight: 56,
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    paddingVertical: 14,
  },
  submitDisabled: { opacity: 0.45 },
  pressed: { opacity: 0.85 },
  submitText: { fontSize: 16, fontWeight: '800', color: 'white' },
  waiting: {
    fontSize: 13,
    lineHeight: 21,
    color: colors.text.secondary,
    textAlign: 'center',
  },
  switchLink: {
    paddingTop: 20,
    paddingHorizontal: 20,
    minHeight: 56,
    alignItems: 'center',
  },
  switchText: {
    fontSize: 15,
    lineHeight: 24,
    color: colors.text.secondary,
    textAlign: 'center',
  },
  link: { fontSize: 14, color: colors.brand.dark, fontWeight: '700' },
});
export default AuthScreen;
