import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CURRENT_USER } from '../api/mockApi';
import { useAuthStore } from './useAuthStore';
import type { Author, Story } from '../types';

const STORAGE_KEY = 'nabd.stories.v1';

/* بلا ستوري تجريبي — كل ستوري كتجي من حساب حقيقي كيرفعها بنفسو. */

const GRADIENTS: Array<[string, string]> = [
  ['#1877F2', '#0C63D4'],
  ['#F33E58', '#DD2A7B'],
  ['#11998E', '#38EF7D'],
  ['#F7971E', '#E9710F'],
];

interface StoryState {
  stories: Story[];
  hydrated: boolean;
  /** تحميل الستوري المحفوظة (اللي صنعهم المستخدم) */
  hydrate: () => Promise<void>;
  addStory: (input: { imageUri?: string | null; text?: string }) => Promise<void>;
  markSeen: (id: string) => void;
  markAllSeen: () => void;
  removeStory: (id: string) => Promise<void>;
}

const me = (): Author => useAuthStore.getState().user ?? CURRENT_USER;

export const useStoryStore = create<StoryState>((set, get) => ({
  stories: [],
  hydrated: false,

  hydrate: async () => {
    if (get().hydrated) return;
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEY);
      const mine: Story[] = raw ? JSON.parse(raw) : [];
      const valid = mine.filter((s) => s?.author?.id);
      set({ stories: [...valid, ...get().stories.filter((s) => !s.id.startsWith('s_mine'))], hydrated: true });
    } catch {
      set({ hydrated: true });
    }
  },

  addStory: async ({ imageUri, text }) => {
    const story: Story = {
      id: `s_mine_${Date.now()}`,
      author: { ...me(), isMe: true },
      imageUri: imageUri ?? null,
      text: text?.trim() || undefined,
      colors: GRADIENTS[Math.floor(Math.random() * GRADIENTS.length)],
      createdAt: 'الآن',
      seen: true,
    };
    const next = [story, ...get().stories];
    set({ stories: next });

    // حفظ ستوري المستخدم فقط (الباقي افتراضي)
    const mineOnly = next.filter((s) => s.id.startsWith('s_mine'));
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(mineOnly));
    } catch {
      /* الحفظ محلي فقط — إذا فشل ما كنوقفوش التجربة */
    }
  },

  markSeen: (id) =>
    set({ stories: get().stories.map((s) => (s.id === id ? { ...s, seen: true } : s)) }),

  markAllSeen: () => set({ stories: get().stories.map((s) => ({ ...s, seen: true })) }),

  removeStory: async (id) => {
    const next = get().stories.filter((s) => s.id !== id);
    set({ stories: next });
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next.filter((s) => s.id.startsWith('s_mine'))));
    } catch {
      /* تجاهل */
    }
  },
}));

export default useStoryStore;
