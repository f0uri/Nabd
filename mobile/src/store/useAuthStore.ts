import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { dataApi } from '../api';
import { setAuthToken } from '../api/client';
import type { LocalFile } from '../api/client';
import type { Author } from '../types';

const TOKEN_KEY = 'nabd.token';
const USER_KEY = 'nabd.user';

/** ترجمة رموز أخطاء الخادم إلى رسائل عربية واضحة */
const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'البريد أو كلمة السر ماشي صحيحة',
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
  register: (name: string, email: string, password: string, file?: LocalFile) => Promise<boolean>;
  logout: () => Promise<void>;
  updateProfile: (input: { name?: string; bio?: string; file?: LocalFile }) => Promise<boolean>;
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
    try {
      const [[, token], [, rawUser]] = await AsyncStorage.multiGet([TOKEN_KEY, USER_KEY]);
      if (!token) {
        set({ hydrated: true });
        return;
      }

      setAuthToken(token);
      const cached = rawUser ? (JSON.parse(rawUser) as Author) : null;
      set({ token, user: cached });

      // تحديث بيانات المستخدم من الخادم (وإن فشل نبقى على النسخة المحفوظة)
      try {
        const fresh = await dataApi.me();
        if (fresh) {
          set({ user: fresh });
          await AsyncStorage.setItem(USER_KEY, JSON.stringify(fresh));
        }
      } catch {
        /* نستعمل النسخة المحفوظة */
      }
    } catch {
      /* تخزين تالف — نبدأ من جديد */
    } finally {
      set({ hydrated: true });
    }
  },

  login: async (email, password) => {
    set({ loading: true, error: null });
    try {
      const payload = await dataApi.login(email.trim(), password);
      setAuthToken(payload.token);
      await AsyncStorage.multiSet([
        [TOKEN_KEY, payload.token],
        [USER_KEY, JSON.stringify(payload.user)],
      ]);
      set({ user: payload.user, token: payload.token, loading: false, error: null });
      return true;
    } catch (error) {
      set({ loading: false, error: authErrorMessage(error) });
      return false;
    }
  },

  register: async (name, email, password, file) => {
    set({ loading: true, error: null });
    try {
      const payload = await dataApi.register(name.trim(), email.trim(), password, file);
      setAuthToken(payload.token);
      await AsyncStorage.multiSet([
        [TOKEN_KEY, payload.token],
        [USER_KEY, JSON.stringify(payload.user)],
      ]);
      set({ user: payload.user, token: payload.token, loading: false, error: null });
      return true;
    } catch (error) {
      set({ loading: false, error: authErrorMessage(error) });
      return false;
    }
  },

  logout: async () => {
    setAuthToken(null);
    await AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
    set({ user: null, token: null, error: null });
  },

  updateProfile: async (input) => {
    const current = get().user;
    if (!current) return false;
    set({ loading: true, error: null });
    try {
      const updated = await dataApi.updateProfile(input);
      const merged = { ...current, ...updated };
      set({ user: merged, loading: false });
      await AsyncStorage.setItem(USER_KEY, JSON.stringify(merged));
      return true;
    } catch (error) {
      set({ loading: false, error: authErrorMessage(error) });
      return false;
    }
  },

  clearError: () => set({ error: null }),
}));
