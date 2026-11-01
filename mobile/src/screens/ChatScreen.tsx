import React, { useCallback, useEffect, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from '../components/atoms/Avatar';
import SkeletonBlock from '../components/atoms/Skeleton';
import ConversationScreen from './ConversationScreen';
import { useChatStore } from '../store/useChatStore';
import { useAuthStore } from '../store/useAuthStore';
import { colors, radius, size, space, type } from '../theme';
import { CURRENT_USER } from '../api/mockApi';
import type { Conversation } from '../types';

interface ChatScreenProps {
  visible: boolean;
  onClose: () => void;
  /** مستخدم كنفتحو معاه المحادثة مباشرة (من الأصدقاء/الملف) */
  initialUserId?: string | null;
  onInitialHandled?: () => void;
}

/** تنسيق الوقت: اليوم = ساعة، غير اليوم = تاريخ قصير */
const formatTime = (iso: string): string => {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay
    ? date.toLocaleTimeString('ar-MA', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('ar-MA', { day: 'numeric', month: 'short' });
};

/**
 * شاشة الرسائل (Messenger) — قائمة المحادثات + فتح محادثة.
 */
export const ChatScreen: React.FC<ChatScreenProps> = ({ visible, onClose, initialUserId, onInitialHandled }) => {
  const insets = useSafeAreaInsets();
  const conversations = useChatStore((s) => s.conversations);
  const loading = useChatStore((s) => s.loading);
  const refreshing = useChatStore((s) => s.refreshing);
  const error = useChatStore((s) => s.error);
  const loadConversations = useChatStore((s) => s.loadConversations);
  const refresh = useChatStore((s) => s.refresh);
  const openWith = useChatStore((s) => s.openWith);
  const loadUnread = useChatStore((s) => s.loadUnread);
  const typing = useChatStore((s) => s.typing);
  const user = useAuthStore((s) => s.user) ?? CURRENT_USER;

  const [search, setSearch] = useState('');
  const [openId, setOpenId] = useState<string | null>(null);
  const [openName, setOpenName] = useState('');

  useEffect(() => {
    if (visible) loadConversations();
    else {
      // ملي كتتسد الشاشة كنعاودو نصّفّيو البحث
      setSearch('');
      setOpenId(null);
    }
  }, [visible, loadConversations]);

  // فتح محادثة مباشرة مع مستخدم (من شاشة الأصدقاء مثلاً)
  useEffect(() => {
    let alive = true;
    if (!visible || !initialUserId) return;
    (async () => {
      const conversation = await openWith(initialUserId);
      if (alive && conversation) {
        setOpenId(conversation.id);
        setOpenName(conversation.other.name);
      }
      onInitialHandled?.();
    })();
    return () => {
      alive = false;
    };
  }, [visible, initialUserId, openWith, onInitialHandled]);

  const openConversation = useCallback((conversation: Conversation) => {
    setOpenId(conversation.id);
    setOpenName(conversation.other.name);
  }, []);

  const renderRow = ({ item }: { item: Conversation }) => {
    const mine = item.lastMessage?.senderId === user.id;
    return (
      <Pressable
        onPress={() => openConversation(item)}
        accessibilityRole="button"
        accessibilityLabel={`محادثة مع ${item.other.name}`}
        style={({ pressed }) => [styles.row, item.unreadCount > 0 && styles.rowUnread, pressed && { opacity: 0.75 }]}
      >
        <View style={styles.avatarWrap}>
          <Avatar
            initial={item.other.initial}
            color={item.other.color}
            avatarUrl={item.other.avatarUrl}
            size={size.avatarMd + 6}
          />
          {item.unreadCount > 0 && <View style={styles.onlineDot} />}
        </View>

        <View style={styles.rowBody}>
          <Text style={styles.name} numberOfLines={1}>
            {item.other.name}
          </Text>
          {typing[item.id] ? (
            <Text style={[styles.preview, styles.previewTyping]} numberOfLines={1}>
              {typing[item.id]} كيكتب…
            </Text>
          ) : (
            <Text style={[styles.preview, item.unreadCount > 0 && styles.previewUnread]} numberOfLines={1}>
              {item.lastMessage
                ? item.lastMessage.deleted
                  ? 'رسالة محذوفة'
                  : `${mine ? 'أنت: ' : ''}${
                      item.lastMessage.text || (item.lastMessage.media ? 'صورة' : '')
                    }`
                : 'بدا المحادثة'}
            </Text>
          )}
        </View>

        <View style={styles.rowMeta}>
          <Text style={styles.time}>{item.lastMessage ? formatTime(item.lastMessage.createdAt) : ''}</Text>
          {item.unreadCount > 0 && (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{item.unreadCount}</Text>
            </View>
          )}
        </View>
      </Pressable>
    );
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose} statusBarTranslucent>
      <View style={[styles.root, { paddingTop: insets.top }]}>
        <View style={styles.header}>
          <Text style={styles.title}>الرسائل</Text>
          <Pressable onPress={onClose} hitSlop={12} accessibilityRole="button" accessibilityLabel="إغلاق">
            <Ionicons name="close" size={24} color={colors.text.secondary} />
          </Pressable>
        </View>

        <View style={styles.searchWrap}>
          <Ionicons name="search" size={16} color={colors.text.secondary} />
          <TextInput
            value={search}
            onChangeText={(value) => {
              setSearch(value);
              loadConversations(value);
            }}
            placeholder="بحث فالمحادثات"
            placeholderTextColor={colors.text.secondary}
            style={styles.searchInput}
            textAlign="right"
            accessibilityLabel="بحث فالمحادثات"
          />
        </View>

        {loading && conversations.length === 0 ? (
          <View style={styles.skeleton}>
            {[0, 1, 2, 3].map((i) => (
              <View key={i} style={styles.skeletonRow}>
                <SkeletonBlock width={46} height={46} radius={23} />
                <View style={{ flex: 1, gap: 6 }}>
                  <SkeletonBlock width="45%" height={12} radius={6} />
                  <SkeletonBlock width="75%" height={10} radius={6} />
                </View>
              </View>
            ))}
          </View>
        ) : (
          <FlatList
            data={conversations}
            keyExtractor={(item) => item.id}
            renderItem={renderRow}
            contentContainerStyle={{ paddingBottom: insets.bottom + space.lg, flexGrow: 1 }}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.brand.primary} />}
            ListEmptyComponent={
              <View style={styles.empty}>
                <View style={styles.emptyIcon}>
                  <Ionicons name="chatbubbles-outline" size={26} color={colors.brand.primary} />
                </View>
                <Text style={styles.emptyTitle}>{error ? 'ما قدرناش نحمّلو' : 'ما كايناش محادثات'}</Text>
                <Text style={styles.emptySub}>
                  {error
                    ? error
                    : 'اختر صديقاً لبدء محادثة.'}
                </Text>
              </View>
            }
          />
        )}

        <ConversationScreen
          visible={!!openId}
          conversationId={openId}
          title={openName}
          onClose={() => {
            setOpenId(null);
            // نحدّثو المعاينة والعدّادات من بعد ما تسد المحادثة
            refresh();
            loadUnread();
          }}
        />
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg.surface },
  header: {
    height: size.appBar,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.divider,
  },
  title: { ...type.headline, color: colors.text.primary },
  searchWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    margin: space.sm,
    height: 38,
    paddingHorizontal: space.sm,
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.pill ?? 999,
  },
  searchInput: { flex: 1, ...type.bodySm, color: colors.text.primary, paddingVertical: 0 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border.divider,
  },
  rowUnread: { backgroundColor: '#F7FAFF' },
  avatarWrap: { position: 'relative' },
  onlineDot: {
    position: 'absolute',
    bottom: 2,
    right: 2,
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: colors.brand.primary,
    borderWidth: 2,
    borderColor: colors.bg.surface,
  },
  rowBody: { flex: 1, minWidth: 0 },
  name: { ...type.bodyStrong, color: colors.text.primary },
  preview: { ...type.caption, color: colors.text.secondary, marginTop: 2 },
  previewTyping: { color: colors.brand.primary, fontStyle: 'italic' },
  previewUnread: { color: colors.text.primary, fontFamily: type.bodyStrong.fontFamily },
  rowMeta: { alignItems: 'flex-end', gap: 5 },
  time: { ...type.micro, color: colors.text.secondary },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { ...type.micro, color: colors.text.inverse, fontFamily: type.bodyStrong.fontFamily },
  skeleton: { padding: space.md, gap: space.md },
  skeletonRow: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.lg, gap: 6 },
  emptyIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space.xs,
  },
  emptyTitle: { ...type.bodyStrong, fontSize: 15, color: colors.text.primary },
  emptySub: { ...type.caption, color: colors.text.secondary, textAlign: 'center', lineHeight: 19 },
});

export default ChatScreen;
