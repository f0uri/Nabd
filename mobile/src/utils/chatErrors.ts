/** Allowlisted copy only: never echo provider errors, URLs or session tokens. */
export function chatError(error: unknown, fallback: string) {
  const code = (error as { code?: string } | null)?.code;
  switch (code) {
    case 'TELEGRAM_CONFLICT':
      return 'تغيّرت البيانات أثناء العملية. أعد المحاولة.';
    case 'TELEGRAM_IMAGE_TOO_LARGE':
      return 'الصورة كبيرة. اختر صورة أصغر من 128 كيلوبايت.';
    case 'TELEGRAM_IMAGE_ONLY':
      return 'يمكن إرسال صور JPEG أو PNG أو WebP فقط.';
    case 'TELEGRAM_VAULT_FULL':
      return 'مساحة البيانات ممتلئة. لم يتم حفظ الرسالة.';
    case 'TELEGRAM_SESSION_REQUIRED':
      return 'انتهت الجلسة. سجّل الدخول مجدداً.';
    case 'FORBIDDEN':
      return 'لا يمكن الوصول إلى هذه المحادثة أو تعديل هذه الرسالة.';
    case 'USER_NOT_FOUND':
      return 'هذا الحساب غير متاح للمحادثة.';
    case 'VALIDATION':
      return 'راجع الرسالة أو الرد المحدد ثم أعد المحاولة.';
    default:
      return fallback;
  }
}
