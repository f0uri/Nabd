import { create } from 'zustand';
import type { TabKey } from '../types';

/**
 * حالة التنقل العامة (Zustand) — باش أي مكوّن (زيادة على الشريط العلوي)
 * يقدر يبدّل التبويب ولا يفتح الملف الشخصي بلا ما نمرّرو props فكل بلاصة.
 */
interface UiState {
  tab: TabKey;
  profileOpen: boolean;
  adminOpen: boolean;
  setTab: (tab: TabKey) => void;
  openProfile: () => void;
  openAdmin: () => void;
  closeAdmin: () => void;
  closeProfile: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  tab: 'feed',
  profileOpen: false,
  adminOpen: false,
  setTab: (tab) => set({ tab }),
  openProfile: () => set({ profileOpen: true }),
  openAdmin: () => set({ adminOpen: true }),
  closeAdmin: () => set({ adminOpen: false }),
  closeProfile: () => set({ profileOpen: false }),
}));

export default useUiStore;
