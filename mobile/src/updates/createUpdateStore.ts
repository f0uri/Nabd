import { create } from 'zustand';

export type UpdatePhase =
  | 'idle'
  | 'checking'
  | 'available'
  | 'downloading'
  | 'ready'
  | 'applying'
  | 'error';
type Step = 'check' | 'download';
export interface UpdateDiagnostics {
  runtimeVersion: string | null;
  channel: string | null;
  updateId: string | null;
  embedded: boolean;
  emergency: boolean;
  dataSource: string;
}
export interface UpdateAdapter {
  diagnostics?: UpdateDiagnostics;
  enabled: boolean;
  check: () => Promise<{
    isAvailable: boolean;
    reason?: string;
    isRollBackToEmbedded?: boolean;
    manifest?: { id?: string };
  }>;
  fetch: () => Promise<{ isNew: boolean; isRollBackToEmbedded?: boolean }>;
  reload: () => Promise<void>;
}
interface UpdateState {
  enabled: boolean;
  phase: UpdatePhase;
  visible: boolean;
  updateId: string | null;
  lastCheckedAt: number | null;
  message: string | null;
  errorStep: Step | null;
  noUpdateReason: string | null;
  diagnostics: UpdateDiagnostics | null;
  check: (manual?: boolean) => Promise<void>;
  download: () => Promise<void>;
  apply: () => Promise<void>;
  dismiss: () => void;
}
const CHECK_INTERVAL = 15 * 60 * 1000;

/** Injectable native adapter makes consent, failure and concurrency behavior testable without a device. */
export function createUpdateStore(adapter: UpdateAdapter, now = Date.now) {
  let checking = false;
  let manualRequested = false;
  let downloading = false;
  let applying = false;
  let dismissedId: string | null = null;
  return create<UpdateState>((set, get) => ({
    enabled: adapter.enabled,
    diagnostics: adapter.diagnostics ?? null,
    noUpdateReason: null,
    phase: 'idle',
    visible: false,
    updateId: null,
    lastCheckedAt: null,
    message: null,
    errorStep: null,
    check: async (manual = false) => {
      if (!adapter.enabled) {
        if (manual)
          set({
            visible: true,
            phase: 'idle',
            message:
              'التحديث داخل التطبيق غير مفعّل في هذه النسخة. يلزم تثبيت نسخة تدعم التحديثات أولًا.',
          });
        return;
      }
      if (checking) {
        if (manual) {
          manualRequested = true;
          set({ visible: true });
        }
        return;
      }
      if (downloading || applying) return;
      if (get().phase === 'ready') {
        if (manual) set({ visible: true });
        return;
      }
      const last = get().lastCheckedAt;
      if (!manual && last !== null && now() - last < CHECK_INTERVAL) return;
      checking = true;
      manualRequested = manual;
      const previous = get().phase;
      set({
        noUpdateReason: null,
        phase: 'checking',
        lastCheckedAt: now(),
        message: null,
        errorStep: null,
        ...(manual ? { visible: true } : {}),
      });
      try {
        const result = await adapter.check();
        if (result.isAvailable || result.isRollBackToEmbedded) {
          const id = result.isRollBackToEmbedded
            ? 'embedded-rollback'
            : (result.manifest?.id ?? 'available-update');
          set({
            phase: 'available',
            updateId: id,
            visible: manualRequested || id !== dismissedId,
            message: null,
          });
        } else {
          const reasons: Record<string, string> = {
            updatePreviouslyFailed:
              'التحديث متوفر ولكن سبق فشل تشغيله على هذا الجهاز؛ ما غاديش نفرضو تشغيله من جديد. احتفظ بنسختك الحالية وتواصل معنا بمعلومات النسخة أسفله.',
            updateRejectedBySelectionPolicy:
              'وصل تحديث ولكن النسخة الحالية رفضاته حسب سياسة التوافق؛ ماشي تأكيد أنك في أحدث إصدار.',
            rollbackRejectedBySelectionPolicy:
              'وصل طلب رجوع لنسخة سابقة ولكنه غير مناسب لهذه النسخة.',
            rollbackNoEmbeddedConfiguration:
              'طلب الرجوع غير ممكن لأن النسخة المدمجة غير متاحة.',
          };
          const known =
            result.reason &&
            (Object.prototype.hasOwnProperty.call(reasons, result.reason) ||
              result.reason === 'noUpdateAvailableOnServer')
              ? result.reason
              : 'unknown';
          set({
            phase: 'idle',
            updateId: null,
            visible: manualRequested,
            noUpdateReason: known,
            message:
              reasons[known] ??
              'ما لقايناش تحديث متوافق مع هاد النسخة والقناة. هادشي ما كيعنيش أنك في أحدث إصدار؛ إلا تبدلات بصمة البناء ممكن تحتاج APK جديد. معلومات النسخة كاينة أسفله.',
          });
        }
      } catch {
        // Do not interrupt normal use or expose native/network details on an automatic check failure.
        set({
          phase: manualRequested
            ? 'error'
            : previous === 'available'
              ? 'available'
              : 'idle',
          ...(manualRequested
            ? {
                visible: true,
                errorStep: 'check',
                message:
                  'تعذّر البحث عن تحديث. تحقق من الإنترنت وحاول مجددًا؛ يمكنك متابعة استعمال التطبيق.',
              }
            : { message: null }),
        });
      } finally {
        checking = false;
        manualRequested = false;
      }
    },
    download: async () => {
      if (!adapter.enabled || checking || downloading || applying) return;
      if (
        get().phase !== 'available' &&
        !(get().phase === 'error' && get().errorStep === 'download')
      )
        return;
      downloading = true;
      set({
        phase: 'downloading',
        visible: true,
        message: null,
        errorStep: null,
      });
      try {
        const result = await adapter.fetch();
        if (result.isNew || result.isRollBackToEmbedded) {
          set({ phase: 'ready', message: null });
        } else {
          set({
            phase: 'idle',
            updateId: null,
            message:
              'لم يعد هناك تحديث جديد متاح. يمكنك متابعة استعمال التطبيق.',
          });
        }
      } catch {
        set({
          phase: 'error',
          errorStep: 'download',
          message:
            'لم يكتمل تنزيل التحديث. نسختك الحالية لم تتغير؛ حاول مرة أخرى.',
        });
      } finally {
        downloading = false;
      }
    },
    apply: async () => {
      if (!adapter.enabled || applying || get().phase !== 'ready') return;
      applying = true;
      set({ phase: 'applying', visible: true, message: null });
      try {
        // No restart until download completed AND the user explicitly chose to restart.
        await adapter.reload();
      } catch {
        applying = false;
        set({
          phase: 'ready',
          message:
            'تعذّرت إعادة تشغيل التطبيق. يمكنك المحاولة مجددًا أو إغلاقه وفتحه لاحقًا.',
        });
      }
    },
    dismiss: () => {
      if (['checking', 'downloading', 'applying'].includes(get().phase)) return;
      dismissedId = get().updateId;
      set({ visible: false });
    },
  }));
}
