import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  FlatList,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from '../components/atoms/Avatar';
import { isTelegramDirect } from '../telegram/config';
import { useChatStore } from '../store/useChatStore';
import { useAuthStore } from '../store/useAuthStore';
import { useFeedStore } from '../store/useFeedStore';
import { colors, radius, size, space, type } from '../theme';
import { CURRENT_USER } from '../api/mockApi';
import type { LocalFile } from '../api/client';
import type { Message, ReactionKind } from '../types';

interface ConversationScreenProps {
  visible: boolean;
  conversationId: string | null;
  title: string;
  onClose: () => void;
}

type MciName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

/** أيقونات التفاعلات — متجهة (بلا إيموجي ف الواجهة) */
const REACTION_META: Record<ReactionKind, { icon: MciName; color: string; label: string }> = {
  like: { icon: 'thumb-up', color: colors.reaction.like, label: 'إعجاب' },
  love: { icon: 'heart', color: colors.reaction.love, label: 'حب' },
  haha: { icon: 'emoticon-happy-outline', color: colors.reaction.haha, label: 'ضحك' },
  wow: { icon: 'emoticon-neutral-outline', color: colors.reaction.wow, label: 'اندهاش' },
  sad: { icon: 'emoticon-sad-outline', color: colors.reaction.sad, label: 'حزن' },
  angry: { icon: 'emoticon-angry-outline', color: colors.reaction.angry, label: 'غضب' },
};

const REACTION_ORDER: ReactionKind[] = ['like', 'love', 'haha', 'wow', 'sad', 'angry'];

/** لغات الترجمة المتاحة */
const LANGS: { code: string; label: string }[] = [
  { code: 'ar', label: 'العربية' },
  { code: 'fr', label: 'الفرنسية' },
  { code: 'en', label: 'الإنجليزية' },
];

const langLabel = (code: string) => LANGS.find((l) => l.code === code)?.label ?? code;

/** الوقت: اليوم = ساعة، غير اليوم = تاريخ قصير */
const formatTime = (iso: string): string => {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toDateString() === new Date().toDateString()
    ? date.toLocaleTimeString('ar-MA', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('ar-MA', { day: 'numeric', month: 'short' });
};

/** الاحتياطي: تحديث دوري بطيء إلا ماكانش WebSocket متصل */
const FALLBACK_POLL_MS = isTelegramDirect ? 15000 : 8000;

/**
 * شاشة محادثة واحدة — فقاعات · رد · تعديل · حذف · تفاعل · ترجمة · لحظي.
 * الضغطة المطوّلة على أي رسالة كتفتح قائمة الإجراءات.
 */
export const ConversationScreen: React.FC<ConversationScreenProps> = ({ visible, conversationId, title, onClose }) => {
  const insets = useSafeAreaInsets();
  const user = useAuthStore((s) => s.user) ?? CURRENT_USER;

  const threads = useChatStore((s) => s.threads);
  const partner = useChatStore((s) => s.conversations.find((c) => c.id === conversationId)?.other);
  const activeId = useChatStore((s) => s.activeId);
  const threadError = useChatStore(s => s.threadError);
  const threadLoading = useChatStore(s => s.threadLoading);
  const openConversation = useChatStore((s) => s.openConversation);
  const closeConversation = useChatStore((s) => s.closeConversation);
  const send = useChatStore((s) => s.send);
  const editMessage = useChatStore((s) => s.editMessage);
  const deleteMessage = useChatStore((s) => s.deleteMessage);
  const reactMessage = useChatStore((s) => s.reactMessage);
  const translateMessage = useChatStore((s) => s.translateMessage);
  const clearTranslation = useChatStore((s) => s.clearTranslation);
  const sendTyping = useChatStore((s) => s.sendTyping);
  const translations = useChatStore((s) => s.translations);
  const typingName = useChatStore((s) => (conversationId ? s.typing[conversationId] ?? null : null));
  const live = useChatStore((s) => s.live);
  const loadThread = useChatStore((s) => s.loadThread);
  const nextCursor = useChatStore((s) => (conversationId ? s.nextCursor[conversationId] ?? null : null));
  const showToast = useFeedStore((s) => s.showToast);
  const draft = useChatStore((s) => s.draft);
  const setDraft = useChatStore((s) => s.setDraft);

  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [replyTo, setReplyTo] = useState<Message | null>(null);
  const [editing, setEditing] = useState<Message | null>(null);
  const [sheetMessage, setSheetMessage] = useState<Message | null>(null);
  const [langPicker, setLangPicker] = useState<Message | null>(null);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);

  const listRef = useRef<FlatList<Message>>(null);
  /** واش المستخدم قريب من آخر رسالة؟ (باش ما نجرّوش لتحت إلا بغا) */
  const stick = useRef(true);
  const inputRef = useRef<TextInput>(null);
  const messages = conversationId ? threads[conversationId] ?? [] : [];

  /* فتح المحادثة + احتياطي التحديث */
  useEffect(() => {
    if (!visible || !conversationId) return;
    openConversation(conversationId);

    const timer = setInterval(() => {
      if (AppState.currentState === 'active' && !useChatStore.getState().live) openConversation(conversationId);
    }, FALLBACK_POLL_MS);

    return () => {
      clearInterval(timer);
      closeConversation();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, conversationId]);

  /* مسودة المشاركة (منشور كتشاركو فـ رسالة) */
  useEffect(() => {
    if (visible && draft) {
      setText(draft);
      setDraft(null);
      setTimeout(() => inputRef.current?.focus(), 250);
    }
  }, [visible, draft, setDraft]);

  /* إغلاق القوائم ملي كتسد الشاشة */
  useEffect(() => {
    if (!visible) {
      setSheetMessage(null);
      setLangPicker(null);
      setReplyTo(null);
      setEditing(null);
      setText('');
    }
  }, [visible]);

  const submit = useCallback(async () => {
    const trimmed = text.trim();
    if (!trimmed || !conversationId || sending) return;

    setSending(true);
    setText('');

    if (editing) {
      const ok = await editMessage(editing.id, trimmed);
      setSending(false);
      setEditing(null);
      if (!ok) {
        showToast('ما قدرناش نعدّلو الرسالة', 'error');
        setText(trimmed);
      }
      return;
    }

    const parentId = replyTo?.id ?? null;
    setReplyTo(null);
    const ok = await send(conversationId, trimmed, parentId);
    setSending(false);
    if (!ok) {
      setText(trimmed);
      setReplyTo(replyTo);
    }
    stick.current = true;
    setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 120);
  }, [text, conversationId, sending, editing, replyTo, send, editMessage, showToast]);

  /** اختيار صورة من المعرض وإرسالها (بلا نص) */
  const pickAndSendImage = async () => {
    if (!conversationId || picking) return;

    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      showToast('خاصنا الإذن للوصول للصور', 'error');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85 });
    if (result.canceled || !result.assets?.length) return;

    const asset = result.assets[0];
    const file: LocalFile = {
      uri: asset.uri,
      name: asset.fileName ?? `chat_${Date.now()}.jpg`,
      type: asset.mimeType ?? 'image/jpeg',
    };

    setPicking(true);
    const parentId = replyTo?.id ?? null;
    setReplyTo(null);
    const ok = await send(conversationId, text.trim(), parentId, {
      file,
      localUri: asset.uri,
      aspectRatio: asset.width && asset.height ? asset.width / asset.height : 4 / 3,
    });
    setPicking(false);
    // The store reports a safe provider-specific error (e.g. oversized image).
    if (ok) setText('');
    else setReplyTo(replyTo);
    stick.current = true;
    setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 150);
  };

  const startReply = (message: Message) => {
    setEditing(null);
    setReplyTo(message);
    setSheetMessage(null);
    inputRef.current?.focus();
  };

  const startEdit = (message: Message) => {
    setReplyTo(null);
    setEditing(message);
    setText(message.text);
    setSheetMessage(null);
    inputRef.current?.focus();
  };

  const confirmDelete = async (message: Message) => {
    setSheetMessage(null);
    setBusy(true);
    const ok = await deleteMessage(message.id);
    setBusy(false);
    if (!ok) showToast('ما قدرناش نحيدو الرسالة', 'error');
  };

  const pickReaction = async (message: Message, kind: ReactionKind) => {
    setSheetMessage(null);
    const mine = (message.reactions ?? []).find((r) => r.mine)?.kind ?? null;
    const ok = await reactMessage(message.id, mine === kind ? null : kind);
    if (!ok) showToast('ما قدرناش نزيدو التفاعل', 'error');
  };

  const pickLanguage = async (message: Message, code: string) => {
    setLangPicker(null);
    setBusy(true);
    const result = await translateMessage(message.id, code);
    setBusy(false);
    if (!result) showToast('الترجمة ما خدمتش — تحقق من اتصال الخادم', 'error');
  };

  /* ------------------------------- الفقاعة ------------------------------- */

  const renderMessage = ({ item }: { item: Message }) => {
    const mine = item.senderId === user.id;
    const pending = item.id.startsWith('tmp_');
    const translation = translations[item.id];
    const reactions = item.reactions ?? [];

    return (
      <View style={[styles.msgRow, mine ? styles.msgRowMine : styles.msgRowOther]}>
        {!mine && (
          <Avatar
            initial={(partner?.name ?? title).charAt(0)}
            avatarUrl={partner?.avatarUrl ?? null}
            size={26}
          />
        )}

        <View style={styles.bubbleWrap}>
          <Pressable
            onLongPress={() => setSheetMessage(item)}
            delayLongPress={250}
            onPress={item.deleted ? undefined : () => setSheetMessage(item)}
            accessibilityRole="button"
            accessibilityLabel={`رسالة: ${item.deleted ? 'محذوفة' : item.text}. اضغط مطوّلاً للخيارات`}
            style={({ pressed }) => [
              styles.bubble,
              mine ? styles.bubbleMine : styles.bubbleOther,
              pending && styles.bubblePending,
              pressed && !pending && { opacity: 0.92 },
            ]}
          >
            {/* الرسالة المردود عليها */}
            {item.replyTo && !item.deleted && (
              <View style={[styles.quote, mine ? styles.quoteMine : styles.quoteOther]}>
                <View style={styles.quoteBar} />
                <View style={{ flex: 1 }}>
                  <Text style={[styles.quoteName, mine && styles.quoteTextMine]} numberOfLines={1}>
                    {item.replyTo.senderId === user.id ? 'أنت' : partner?.name ?? title}
                  </Text>
                  <Text style={[styles.quoteText, mine && styles.quoteTextMine]} numberOfLines={2}>
                    {item.replyTo.deleted ? 'رسالة محذوفة' : item.replyTo.text}
                  </Text>
                </View>
              </View>
            )}

            {item.media && !item.deleted && (
              <Image
                source={{ uri: item.media.uri }}
                style={[styles.msgImage, { aspectRatio: item.media.aspectRatio || 4 / 3 }]}
                resizeMode="cover"
                accessibilityLabel="صورة فالرسالة"
              />
            )}

            {item.deleted ? (
              <View style={styles.deletedRow}>
                <Ionicons name="ban-outline" size={13} color={colors.text.secondary} />
                <Text style={styles.deletedText}>تم حذف هذه الرسالة</Text>
              </View>
            ) : item.text ? (
              <Text style={[styles.msgText, mine && styles.msgTextMine]}>{item.text}</Text>
            ) : null}

            <View style={styles.msgMeta}>
              {item.edited && !item.deleted && (
                <Text style={[styles.editedLabel, mine && styles.timeMine]}>عُدِّلت</Text>
              )}
              <Text style={[styles.msgTime, mine && styles.timeMine]}>{formatTime(item.createdAt)}</Text>
              {mine && !pending && !item.deleted && (
                <Ionicons
                  name={item.read ? 'checkmark-done' : 'checkmark'}
                  size={12}
                  color={item.read ? '#A9D5FF' : 'rgba(255,255,255,0.8)'}
                />
              )}
            </View>
          </Pressable>

          {/* تفاعلات الرسالة */}
          {reactions.length > 0 && (
            <Pressable
              onPress={() => setSheetMessage(item)}
              style={[styles.reactionsPill, mine ? styles.pillMine : styles.pillOther]}
              accessibilityLabel={`${reactions.length} تفاعل`}
            >
              {reactions.slice(0, 3).map((reaction) => (
                <MaterialCommunityIcons
                  key={reaction.kind}
                  name={REACTION_META[reaction.kind].icon}
                  size={13}
                  color={REACTION_META[reaction.kind].color}
                />
              ))}
              <Text style={styles.reactionsCount}>{reactions.reduce((sum, r) => sum + r.count, 0)}</Text>
            </Pressable>
          )}

          {/* الترجمة */}
          {translation && (
            <View style={[styles.translationBox, mine ? styles.alignStart : styles.alignEnd]}>
              <View style={styles.translationHead}>
                <Ionicons name="language-outline" size={13} color={colors.brand.primary} />
                <Text style={styles.translationLabel}>ترجمة · {langLabel(translation.to)}</Text>
                <Pressable onPress={() => clearTranslation(item.id)} hitSlop={8} accessibilityLabel="إخفاء الترجمة">
                  <Ionicons name="close" size={13} color={colors.text.secondary} />
                </Pressable>
              </View>
              <Text style={styles.translationText}>{translation.text}</Text>
            </View>
          )}
        </View>
      </View>
    );
  };

  if (!visible) return null;

  const sheetMine = sheetMessage?.senderId === user.id;
  const sheetDeleted = Boolean(sheetMessage?.deleted);
  const sheetTranslated = sheetMessage ? Boolean(translations[sheetMessage.id]) : false;

  return (
    <View style={styles.overlay}>
      <KeyboardAvoidingView
        style={styles.root}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={0}
      >
        {/* الرأس */}
        <View style={[styles.header, { paddingTop: insets.top + 6 }]}>
          <Pressable onPress={onClose} hitSlop={12} accessibilityRole="button" accessibilityLabel="رجوع">
            <Ionicons name="chevron-forward" size={24} color={colors.text.primary} />
          </Pressable>

          <View style={styles.headerCenter}>
            <Avatar
              initial={(partner?.name ?? title).charAt(0)}
              avatarUrl={partner?.avatarUrl ?? null}
              size={30}
            />
            <View>
              <Text style={styles.headerName} numberOfLines={1}>
                {partner?.name ?? title}
              </Text>
              <View style={styles.statusRow}>
                <View style={[styles.statusDot, live ? styles.dotLive : styles.dotOffline]} />
                <Text style={styles.statusText}>
                  {typingName ? `${typingName} كيكتب…` : live ? 'متصل · لحظي' : isTelegramDirect ? 'تحديث دوري' : 'اتصال عادي'}
                </Text>
              </View>
            </View>
          </View>

          <Pressable
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="معلومات"
            onPress={() => showToast('الدردشة محفوظة فقاعدة البيانات · التحديث لحظي عبر WebSocket', 'info')}
          >
            <Ionicons name="information-circle-outline" size={22} color={colors.text.secondary} />
          </Pressable>
        </View>

        {/* تحميل الأقدم */}
        {nextCursor && (
          <Pressable
            onPress={() => conversationId && loadThread(conversationId, nextCursor)}
            style={styles.loadOlder}
          >
            <Ionicons name="arrow-up-circle-outline" size={15} color={colors.brand.primary} />
            <Text style={styles.loadOlderText}>تحميل الرسائل الأقدم</Text>
          </Pressable>
        )}

        {threadError && <Pressable accessibilityRole="button" accessibilityLabel="إعادة تحميل الرسائل" onPress={() => conversationId && openConversation(conversationId)} style={styles.loadOlder}><Text style={styles.emptyHint}>{threadError} · إعادة المحاولة</Text></Pressable>}
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={(item) => item.id}
          renderItem={renderMessage}
          contentContainerStyle={styles.listContent}
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => {
            if (stick.current) listRef.current?.scrollToEnd({ animated: false });
          }}
          onScroll={({ nativeEvent }) => {
            const distance =
              nativeEvent.contentSize.height - (nativeEvent.contentOffset.y + nativeEvent.layoutMeasurement.height);
            stick.current = distance < 120;
          }}
          scrollEventThrottle={64}
          ListEmptyComponent={
            <View style={styles.empty}>
              {threadLoading ? <ActivityIndicator color={colors.brand.primary} /> : !threadError && <>
                <Text style={styles.emptyText}>ما كايناش رسائل — كتب أول وحدة</Text>
                <Text style={styles.emptyHint}>{isTelegramDirect ? 'اضغط مطوّلاً للرد أو التعديل أو التفاعل أو الحذف' : 'ضغطة مطوّلة على أي رسالة كتفتح: رد · تعديل · ترجمة · تفاعل · حذف'}</Text>
              </>}
            </View>
          }
        />

        {/* «يكتب الآن…» */}
        {typingName && (
          <View style={styles.typingBar}>
            <View style={styles.typingDots}>
              <View style={styles.typingDot} />
              <View style={styles.typingDot} />
              <View style={styles.typingDot} />
            </View>
            <Text style={styles.typingText}>{typingName} كيكتب…</Text>
          </View>
        )}

        {/* شريط الرد / التعديل */}
        {(replyTo || editing) && (
          <View style={styles.contextBar}>
            <View style={styles.contextBody}>
              <Text style={styles.contextTitle}>
                {editing ? 'تعديل الرسالة' : `رد على ${replyTo?.senderId === user.id ? 'رسالتك' : partner?.name ?? title}`}
              </Text>
              <Text style={styles.contextText} numberOfLines={1}>
                {editing ? editing.text : replyTo?.text ?? ''}
              </Text>
            </View>
            <Pressable
              hitSlop={10}
              accessibilityLabel="إلغاء"
              onPress={() => {
                if (editing) setText('');
                setEditing(null);
                setReplyTo(null);
              }}
            >
              <Ionicons name="close" size={18} color={colors.text.secondary} />
            </Pressable>
          </View>
        )}

        {/* حقل الكتابة */}
        <View style={[styles.inputBar, { paddingBottom: Math.max(insets.bottom, space.sm) }]}>
          <Pressable
            onPress={pickAndSendImage}
            disabled={picking || !!editing}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="إرفاق صورة"
            style={({ pressed }) => [styles.attachBtn, pressed && { opacity: 0.6 }]}
          >
            {picking ? (
              <ActivityIndicator size="small" color={colors.brand.primary} />
            ) : (
              <Ionicons
                name="image-outline"
                size={22}
                color={editing ? colors.text.tertiary : colors.brand.primary}
              />
            )}
          </Pressable>

          <View style={[styles.inputWrap, editing && styles.inputWrapEditing]}>
            <TextInput
              ref={inputRef}
              value={text}
              onChangeText={(value) => {
                setText(value);
                if (!editing && conversationId) sendTyping(conversationId);
              }}
              placeholder={editing ? 'عدّل الرسالة…' : 'اكتب رسالة…'}
              placeholderTextColor={colors.text.secondary}
              style={styles.input}
              multiline
              maxLength={2000}
              accessibilityLabel="حقل كتابة رسالة"
            />
          </View>

          <Pressable
            onPress={submit}
            disabled={!text.trim() || sending}
            accessibilityRole="button"
            accessibilityLabel={editing ? 'حفظ التعديل' : 'إرسال'}
            style={({ pressed }) => [
              styles.sendBtn,
              (!text.trim() || sending) && styles.sendBtnDisabled,
              pressed && { opacity: 0.8 },
            ]}
          >
            <Ionicons
              name={editing ? 'checkmark' : 'arrow-up'}
              size={20}
              color={text.trim() ? colors.text.inverse : colors.text.tertiary}
            />
          </Pressable>
        </View>
      </KeyboardAvoidingView>

      {/* ============ قائمة الإجراءات (ضغطة مطوّلة) ============ */}
      {sheetMessage && (
        <View style={StyleSheet.absoluteFill}>
          <Pressable style={styles.backdrop} onPress={() => setSheetMessage(null)} accessibilityLabel="إغلاق" />

          <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, space.sm) }]}>
            <View style={styles.sheetGrabber} />

            {/* شريط التفاعلات */}
            {!sheetDeleted && (
              <View style={styles.reactionBar}>
                {REACTION_ORDER.map((kind) => {
                  const mineKind = (sheetMessage.reactions ?? []).find((r) => r.mine)?.kind ?? null;
                  const active = mineKind === kind;
                  return (
                    <Pressable
                      key={kind}
                      onPress={() => pickReaction(sheetMessage, kind)}
                      accessibilityRole="button"
                      accessibilityLabel={REACTION_META[kind].label}
                      style={({ pressed }) => [
                        styles.reactionBtn,
                        active && styles.reactionBtnActive,
                        pressed && { transform: [{ scale: 0.9 }] },
                      ]}
                    >
                      <MaterialCommunityIcons
                        name={REACTION_META[kind].icon}
                        size={22}
                        color={REACTION_META[kind].color}
                      />
                    </Pressable>
                  );
                })}
              </View>
            )}

            {!sheetDeleted && (
              <SheetRow
                icon="arrow-undo-outline"
                label="رد"
                hint="رد على هاد الرسالة"
                onPress={() => startReply(sheetMessage)}
              />
            )}

            {sheetMine && !sheetDeleted && (
              <SheetRow
                icon="create-outline"
                label="تعديل الرسالة"
                hint="بدّل النص وحيّد «عُدِّلت»"
                onPress={() => startEdit(sheetMessage)}
              />
            )}

            {!sheetDeleted && !isTelegramDirect && (
              <SheetRow
                icon={sheetTranslated ? 'eye-off-outline' : 'language-outline'}
                label={sheetTranslated ? 'إخفاء الترجمة' : 'ترجمة'}
                hint={sheetTranslated ? undefined : 'عربية · فرنسية · إنجليزية'}
                onPress={() => {
                  if (sheetTranslated) {
                    clearTranslation(sheetMessage.id);
                    setSheetMessage(null);
                  } else {
                    setLangPicker(sheetMessage);
                    setSheetMessage(null);
                  }
                }}
              />
            )}

            {sheetMine && !sheetDeleted && (
              <SheetRow
                icon="trash-outline"
                label="حذف الرسالة"
                hint="كتتمحى عند الجوج"
                danger
                onPress={() => confirmDelete(sheetMessage)}
              />
            )}

            <Pressable onPress={() => setSheetMessage(null)} style={styles.sheetCancel} accessibilityRole="button">
              <Text style={styles.sheetCancelText}>إلغاء</Text>
            </Pressable>
          </View>
        </View>
      )}

      {/* ============ اختيار لغة الترجمة ============ */}
      {langPicker && (
        <View style={StyleSheet.absoluteFill}>
          <Pressable style={styles.backdrop} onPress={() => setLangPicker(null)} accessibilityLabel="إغلاق" />
          <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, space.sm) }]}>
            <View style={styles.sheetGrabber} />
            <Text style={styles.sheetTitle}>ترجم الرسالة إلى</Text>
            <ScrollView scrollEnabled={false}>
              {LANGS.map((lang) => (
                <SheetRow
                  key={lang.code}
                  icon="language-outline"
                  label={lang.label}
                  onPress={() => pickLanguage(langPicker, lang.code)}
                />
              ))}
            </ScrollView>
            <Pressable onPress={() => setLangPicker(null)} style={styles.sheetCancel} accessibilityRole="button">
              <Text style={styles.sheetCancelText}>إلغاء</Text>
            </Pressable>
          </View>
        </View>
      )}

      {/* حجب اللمس أثناء عمليات الخادم */}
      {busy && <View style={styles.busyOverlay} pointerEvents="auto" />}
    </View>
  );
};

/* ------------------------------ صف فالقائمة ------------------------------ */

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const SheetRow: React.FC<{
  icon: IoniconName;
  label: string;
  hint?: string;
  danger?: boolean;
  onPress: () => void;
}> = ({ icon, label, hint, danger, onPress }) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
    style={({ pressed }) => [styles.sheetRow, pressed && { backgroundColor: colors.bg.canvas }]}
  >
    <View style={[styles.sheetIcon, danger && styles.sheetIconDanger]}>
      <Ionicons name={icon} size={19} color={danger ? colors.semantic.danger : colors.brand.primary} />
    </View>
    <View style={{ flex: 1 }}>
      <Text style={[styles.sheetLabel, danger && { color: colors.semantic.danger }]}>{label}</Text>
      {hint ? <Text style={styles.sheetHint}>{hint}</Text> : null}
    </View>
  </Pressable>
);

const styles = StyleSheet.create({
  overlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.bg.canvas, zIndex: 10, elevation: 10 },
  root: { flex: 1, backgroundColor: colors.bg.canvas },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    backgroundColor: colors.bg.surface,
    paddingHorizontal: space.md,
    paddingBottom: space.xs,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.divider,
  },
  headerCenter: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: space.xs, justifyContent: 'center' },
  headerName: { ...type.bodyStrong, color: colors.text.primary, maxWidth: 190 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 4, justifyContent: 'center' },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  dotLive: { backgroundColor: colors.semantic.success },
  dotOffline: { backgroundColor: colors.text.tertiary },
  statusText: { ...type.caption, color: colors.text.secondary },

  loadOlder: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingVertical: space.xs },
  loadOlderText: { ...type.micro, color: colors.brand.dark },

  listContent: { padding: space.md, paddingBottom: space.sm, flexGrow: 1, gap: 6 },
  msgRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 6 },
  msgRowMine: { justifyContent: 'flex-start' },
  msgRowOther: { justifyContent: 'flex-end' },
  bubbleWrap: { maxWidth: '82%', gap: 3 },

  bubble: { paddingHorizontal: space.sm, paddingVertical: 7, borderRadius: radius.lg },
  bubbleMine: { backgroundColor: colors.brand.primary, borderBottomStartRadius: radius.xs },
  bubbleOther: { backgroundColor: colors.bg.surface, borderBottomEndRadius: radius.xs },
  bubblePending: { opacity: 0.6 },
  msgImage: { width: 210, borderRadius: radius.md, marginBottom: 4, backgroundColor: colors.bg.skeletonBase },
  attachBtn: { width: 36, height: 40, alignItems: 'center', justifyContent: 'center' },
  msgText: { ...type.bodySm, color: colors.text.primary, lineHeight: 20 },
  msgTextMine: { color: colors.text.inverse },
  msgMeta: { flexDirection: 'row', alignItems: 'center', gap: 4, justifyContent: 'flex-start', marginTop: 2 },
  msgTime: { ...type.caption, color: colors.text.secondary },
  timeMine: { color: 'rgba(255,255,255,0.78)' },
  editedLabel: { ...type.caption, color: colors.text.secondary, fontStyle: 'italic' },

  quote: {
    flexDirection: 'row',
    gap: 6,
    paddingVertical: 4,
    paddingHorizontal: 6,
    borderRadius: radius.sm,
    marginBottom: 4,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  quoteMine: { backgroundColor: 'rgba(255,255,255,0.16)' },
  quoteOther: { backgroundColor: colors.bg.canvas },
  quoteBar: { width: 3, borderRadius: 2, backgroundColor: colors.brand.primary },
  quoteName: { ...type.caption, color: colors.brand.dark },
  quoteText: { ...type.caption, color: colors.text.secondary },
  quoteTextMine: { color: 'rgba(255,255,255,0.9)' },

  deletedRow: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  deletedText: { ...type.bodySm, color: colors.text.secondary, fontStyle: 'italic' },

  reactionsPill: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    backgroundColor: colors.bg.surface,
    borderRadius: radius.pill,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: size.hairline,
    borderColor: colors.border.subtle,
  },
  pillMine: { alignSelf: 'flex-start' },
  pillOther: { alignSelf: 'flex-end' },
  reactionsCount: { ...type.caption, color: colors.text.secondary, marginHorizontal: 2 },

  translationBox: {
    width: '100%',
    borderRadius: radius.md,
    borderWidth: size.hairline,
    borderColor: colors.border.subtle,
    backgroundColor: colors.bg.surface,
    padding: space.xs,
    gap: 3,
  },
  alignStart: { alignSelf: 'flex-start' },
  alignEnd: { alignSelf: 'flex-end' },
  translationHead: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  translationLabel: { ...type.caption, color: colors.brand.dark, flex: 1 },
  translationText: { ...type.bodySm, color: colors.text.primary },

  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: space.lg },
  emptyText: { ...type.caption, color: colors.text.secondary },
  emptyHint: { ...type.caption, color: colors.text.tertiary, textAlign: 'center' },

  typingBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: space.md,
    paddingVertical: 4,
    backgroundColor: colors.bg.canvas,
  },
  typingDots: { flexDirection: 'row', gap: 2 },
  typingDot: { width: 4, height: 4, borderRadius: 2, backgroundColor: colors.text.tertiary },
  typingText: { ...type.caption, color: colors.text.secondary },

  contextBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    backgroundColor: colors.bg.surface,
    borderTopWidth: size.hairline,
    borderTopColor: colors.border.divider,
    paddingHorizontal: space.md,
    paddingVertical: 6,
  },
  contextBody: { flex: 1, borderStartWidth: 3, borderStartColor: colors.brand.primary, paddingStart: 6 },
  contextTitle: { ...type.caption, color: colors.brand.dark },
  contextText: { ...type.caption, color: colors.text.secondary },

  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: space.xs,
    paddingHorizontal: space.md,
    paddingTop: space.sm,
    backgroundColor: colors.bg.surface,
    borderTopWidth: size.hairline,
    borderTopColor: colors.border.divider,
  },
  inputWrap: {
    flex: 1,
    minHeight: 40,
    maxHeight: 120,
    borderRadius: radius.pill,
    backgroundColor: colors.bg.canvas,
    paddingHorizontal: space.sm,
    justifyContent: 'center',
  },
  inputWrapEditing: { backgroundColor: colors.brand.soft, borderWidth: 1, borderColor: colors.brand.primary },
  input: { ...type.body, color: colors.text.primary, paddingVertical: 8, textAlign: 'right' },
  sendBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendBtnDisabled: { backgroundColor: colors.bg.skeletonBase },

  /* القائمة السفلية */
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.bg.overlay },
  sheet: {
    position: 'absolute',
    start: 0,
    end: 0,
    bottom: 0,
    backgroundColor: colors.bg.surface,
    borderTopStartRadius: radius.lg + 4,
    borderTopEndRadius: radius.lg + 4,
    paddingTop: space.xs,
    paddingHorizontal: space.md,
  },
  sheetGrabber: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border.divider,
    alignSelf: 'center',
    marginBottom: space.sm,
  },
  sheetTitle: { ...type.bodyStrong, color: colors.text.primary, marginBottom: space.xs },
  reactionBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.pill,
    padding: 6,
    marginBottom: space.xs,
  },
  reactionBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reactionBtnActive: { backgroundColor: colors.bg.surface, borderWidth: 1, borderColor: colors.brand.primary },
  sheetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingVertical: space.sm,
    borderRadius: radius.md,
  },
  sheetIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sheetIconDanger: { backgroundColor: colors.statusSurface.danger },
  sheetLabel: { ...type.body, color: colors.text.primary },
  sheetHint: { ...type.caption, color: colors.text.secondary },
  sheetCancel: {
    marginTop: space.xs,
    paddingVertical: space.sm,
    borderRadius: radius.md,
    backgroundColor: colors.bg.canvas,
    alignItems: 'center',
  },
  sheetCancelText: { ...type.bodyStrong, color: colors.text.primary },
  busyOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.45)' },
});

export default ConversationScreen;
