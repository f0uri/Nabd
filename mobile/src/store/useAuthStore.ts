import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { dataApi, authStoragePrefix } from '../api';
import { normalizeAuthor, setAuthToken } from '../api/client';
import type { LocalFile } from '../api/client';
import type { Author } from '../types';

let authGeneration = 0;

const TOKEN_KEY = `${authStoragePrefix ?? 'nabd'}.token`;
const USER_KEY = `${authStoragePrefix ?? 'nabd'}.user`;

/** ترجمة رموز أخطاء الخادم إلى رسائل عربية واضحة */
const ERROR_MESSAGES: Record<string, string> = {
  TELEGRAM_INVALID_BOT:
    'هوية البوت ما مطابْقاش الإعداد، تحقق من إعدادات البناء',
  TELEGRAM_INVALID_CHAT: 'معرف المحادثة غير صالح أو ما مطابقش الإعداد',
  TELEGRAM_PRIVATE_CHAT_REQUIRED: 'خاص قناة أو محادثة خاصة، ماشي وجهة عامة',
  TELEGRAM_ALREADY_INITIALIZED:
    'المحادثة فيها نسخة مثبتة من قبل، عاود الاتصال بلا تهيئة',
  TELEGRAM_CONFIG_REQUIRED: 'خاص إعداد بوت ومحادثة خاصة بنبض قبل التشغيل',
  TELEGRAM_REQUEST_FAILED:
    'تعذر الاتصال بتلغرام — تحقق من الشبكة وصلاحيات البوت',
  TELEGRAM_READ_FAILED: 'تعذرت قراءة نسخة تلغرام، ما تم مسح حتى حساب',
  TELEGRAM_WRITE_FAILED:
    'ما تأكدش حفظ التغيير فتلغرام، عاود القراءة قبل المحاولة',
  TELEGRAM_VAULT_MISSING:
    'ما كايناش نسخة مثبتة — خاص تهيئة صريحة من شاشة إعداد تلغرام',
  TELEGRAM_INVALID_VAULT:
    'النسخة المثبتة غير صالحة أو ماشي ديال هاد الإعداد، ما تمش تعويضها',
  TELEGRAM_FOREIGN_PIN: 'الرسالة المثبتة ماشي نسخة نبض — ما غاديش نعوضوها',
  TELEGRAM_CONFLICT:
    'جهاز آخر بدّل النسخة أثناء الحفظ؛ حدّث البيانات قبل إعادة المحاولة',
  TELEGRAM_SESSION_REQUIRED: 'انتهت جلسة تلغرام، سجّل الدخول من جديد',
  TELEGRAM_PASSWORD_LENGTH: 'كلمة السر خاصها بين 8 و128 حرف',
  TELEGRAM_IMAGE_ONLY:
    'الوضع المؤقت كيدعم غير صور JPEG وPNG وWebP محلية، ماشي الفيديو',
  TELEGRAM_IMAGE_TOO_LARGE:
    'الصورة كبيرة — استعمل صورة مضغوطة أقل من 128 كيلوبايت',
  TELEGRAM_VAULT_FULL:
    'نسخة تلغرام وصلت للحد ديال 2 ميغابايت؛ خاص الانتقال لخادم قبل المزيد',
  TELEGRAM_PUBLIC_ONLY:
    'الوضع المؤقت كيدعم غير المنشورات العامة، الخصوصية ماشي مضمونة',
  TELEGRAM_FEATURE_UNAVAILABLE:
    'هاد الميزة ما متوفراش فالوضع المؤقت ديال تلغرام',
  UPLOAD_TOO_LARGE: 'الملف كبير — الحد الحالي للرفع 18 ميغابايت',
  TELEGRAM_SENDDOCUMENT_FAILED: 'تعذر رفع الصورة إلى تلغرام، عاود المحاولة',
  INVALID_USERNAME:
    'اليوزر خاصو يبدأ بحرف ويحتوي على 3 حتى 40 حرف إنجليزي أو رقم أو _',
  USERNAME_TAKEN: 'هاد اليوزر مستعمل، اختار واحد آخر',
  USERNAME_UNAVAILABLE: 'خاص تحديث الخادم باش تقدر تبدّل اليوزر',
  PROFILE_REQUIRES_SERVER:
    'حفظ الملف والصورة خاصو اتصال بالخادم الحقيقي، ماشي البيانات التجريبية',
  INVALID_CREDENTIALS: 'بيانات تسجيل الدخول غير صحيحة',
  SESSION_SAVE_FAILED:
    'تم التحقق من الحساب لكن تعذر حفظ الجلسة. يرجى تسجيل الدخول مجدداً',
  PASSWORD_DERIVATION_FAILED:
    'تعذر التحقق من كلمة المرور، يرجى المحاولة مجدداً',
  EMAIL_TAKEN: 'هاد البريد مستعمل من قبل',
  INVALID_EMAIL: 'البريد الإلكتروني ماشي صحيح',
  PASSWORD_TOO_SHORT: 'كلمة السر خاصها 6 حروف على الأقل',
  NAME_TOO_SHORT: 'الاسم قصير بزاف',
  VALIDATION: 'تحقق من المعلومات المدخلة',
  NETWORK_ERROR: 'ما قدرناش نتصلو بالخادم — تحقق من الإنترنت',
  TIMEOUT: 'الخادم بطيء، عاود المحاولة',
  UNAUTHENTICATED: 'خاصك تسجل الدخول من جديد',
};

export const authErrorMessage = (error: unknown): string => {
  const code = (error as { code?: string; message?: string })?.code ?? '';
  if (ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
  const message = (error as Error)?.message ?? '';
  if (ERROR_MESSAGES[message]) return ERROR_MESSAGES[message];
  return 'وقع خطأ غير متوقع، عاود المحاولة';
};

interface AuthState {
  user: Author | null;
  token: string | null;
  /** هل قرأنا التوكن من التخزين؟ (حتى لا تظهر شاشة الدخول وميضاً) */
  hydrated: boolean;
  loading: boolean;
  error: string | null;

  hydrate: () => Promise<void>;
  login: (email: string, password: string) => Promise<boolean>;
  register: (
    name: string,
    email: string,
    password: string,
    file?: LocalFile,
  ) => Promise<boolean>;
  logout: () => Promise<void>;
  updateProfile: (input: {
    name?: string;
    bio?: string;
    username?: string;
    file?: LocalFile;
  }) => Promise<boolean>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: null,
  hydrated: false,
  loading: false,
  error: null,

  /** استرجاع الجلسة عند فتح التطبيق */
  hydrate: async () => {
    if (get().loading) return;
    const generation = authGeneration;
    try {
      const [[, token], [, rawUser]] = await AsyncStorage.multiGet([
        TOKEN_KEY,
        USER_KEY,
      ]);
      if (generation !== authGeneration) return;
      if (!token) {
        set({ hydrated: true });
        return;
      }

      const cached = rawUser
        ? normalizeAuthor(JSON.parse(rawUser) as Author)
        : null;
      setAuthToken(token);
      set({ token, user: cached, hydrated: !!cached });

      // تحديث بيانات المستخدم من الخادم (وإن فشل نبقى على النسخة المحفوظة)
      try {
        const fresh = await dataApi.me();
        if (generation !== authGeneration) return;
        if (fresh) {
          set({ user: fresh });
          await AsyncStorage.setItem(USER_KEY, JSON.stringify(fresh));
        } else {
          setAuthToken(null);
          set({ user: null, token: null });
          await AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
        }
      } catch (error) {
        if (generation !== authGeneration) return;
        if (
          (error as Error)?.message === 'TELEGRAM_SESSION_REQUIRED' ||
          (error as { code?: string })?.code === 'TELEGRAM_SESSION_REQUIRED'
        ) {
          setAuthToken(null);
          set({ user: null, token: null });
          await AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
        }
        // Network errors keep the cache, but writes must still validate the session.
      }
    } catch {
      /* تخزين تالف — نبدأ من جديد */
    } finally {
      if (generation === authGeneration) set({ hydrated: true });
    }
  },

  login: async (email, password) => {
    if (get().loading) return false;
    const generation = ++authGeneration;
    set({ loading: true, error: null });
    try {
      const payload = await dataApi.login(email.trim(), password);
      if (generation !== authGeneration) return false;
      setAuthToken(payload.token);
      await AsyncStorage.multiSet([
        [TOKEN_KEY, payload.token],
        [USER_KEY, JSON.stringify(payload.user)],
      ]).catch(() => {
        throw new Error('SESSION_SAVE_FAILED');
      });
      if (generation !== authGeneration) return false;
      set({
        user: payload.user,
        token: payload.token,
        hydrated: true,
        loading: false,
        error: null,
      });
      return true;
    } catch (error) {
      if (generation === authGeneration) {
        setAuthToken(get().token);
        set({ loading: false, error: authErrorMessage(error) });
      }
      return false;
    }
  },

  register: async (name, email, password, file) => {
    if (get().loading) return false;
    const generation = ++authGeneration;
    set({ loading: true, error: null });
    try {
      const payload = await dataApi.register(
        name.trim(),
        email.trim(),
        password,
        file,
      );
      if (generation !== authGeneration) return false;
      setAuthToken(payload.token);
      await AsyncStorage.multiSet([
        [TOKEN_KEY, payload.token],
        [USER_KEY, JSON.stringify(payload.user)],
      ]).catch(() => {
        throw new Error('SESSION_SAVE_FAILED');
      });
      if (generation !== authGeneration) return false;
      set({
        user: payload.user,
        token: payload.token,
        hydrated: true,
        loading: false,
        error: null,
      });
      return true;
    } catch (error) {
      if (generation === authGeneration) {
        setAuthToken(get().token);
        set({ loading: false, error: authErrorMessage(error) });
      }
      return false;
    }
  },

  logout: async () => {
    const generation = ++authGeneration;
    set({ loading: true });
    // Revocation is best effort offline. Local sign-out always works.
    await dataApi.logout?.().catch(() => undefined);
    if (generation !== authGeneration) return;
    setAuthToken(null);
    let error = null;
    try {
      await AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
    } catch {
      error =
        'تعذر مسح الجلسة المحفوظة على الجهاز. يرجى إعادة المحاولة قبل إغلاق التطبيق';
    }
    if (generation === authGeneration)
      set({ user: null, token: null, loading: false, error });
  },

  updateProfile: async (input) => {
    if (get().loading) return false;
    const generation = authGeneration;
    const current = get().user;
    if (!current) return false;
    set({ loading: true, error: null });
    try {
      const updated = await dataApi.updateProfile(input);
      if (generation !== authGeneration) return false;
      const merged = { ...current, ...updated };
      set({ user: merged, loading: false });
      await AsyncStorage.setItem(USER_KEY, JSON.stringify(merged));
      return true;
    } catch (error) {
      if (generation === authGeneration)
        set({ loading: false, error: authErrorMessage(error) });
      return false;
    }
  },

  clearError: () => set({ error: null }),
}));
