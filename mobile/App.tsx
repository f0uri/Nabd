import TelegramGate from './src/telegram/TelegramGate';
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, I18nManager, Pressable, StatusBar, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import TabBar from './src/components/TabBar';
import Toast from './src/components/Toast';
import UpdateNotice from './src/components/UpdateNotice';
import FeedScreen from './src/screens/FeedScreen';
import NotificationsScreen from './src/screens/NotificationsScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import FriendsScreen from './src/screens/FriendsScreen';
import ComposeScreen from './src/screens/ComposeScreen';
import ChatScreen from './src/screens/ChatScreen';
import SearchScreen from './src/screens/SearchScreen';
import ReelsScreen from './src/screens/ReelsScreen';
import AdminScreen from './src/screens/AdminScreen';
import MenuScreen from './src/screens/MenuScreen';
import AuthScreen from './src/screens/AuthScreen';
import { useAuthStore } from './src/store/useAuthStore';
import { useFeedStore } from './src/store/useFeedStore';
import { useChatStore } from './src/store/useChatStore';
import { useUiStore } from './src/store/useUiStore';
import { colors, space, type } from './src/theme';
import type { TabKey } from './src/types';

// دعم كامل للعربية RTL
I18nManager.allowRTL(true);
I18nManager.forceRTL(true);

/** الشاشة اللي كتظهر أثناء قراءة جلسة المستخدم من التخزين */
const Splash: React.FC = () => (
  <View style={styles.splash}>
    <View style={styles.logo}>
      <Text style={styles.logoText}>ن</Text>
    </View>
    <ActivityIndicator color={colors.brand.primary} />
  </View>
);

const Root: React.FC = () => {
  const [composeOpen, setComposeOpen] = useState(false);
  // التنقل: من الستور باش الشريط العلوي وأي شاشة يقدرو يبدّلو التبويب
  const tab = useUiStore((s) => s.tab);
  const setTab = useUiStore((s) => s.setTab);
  const openProfile = useUiStore((s) => s.openProfile);
  const adminOpen = useUiStore((s) => s.adminOpen);
  const isAdmin = useAuthStore((s) => s.user?.isAdmin === true);
  const openAdmin = useUiStore((s) => s.openAdmin);
  const closeAdmin = useUiStore((s) => s.closeAdmin);

  // شاشة الرسائل: حالة عامة ف الستور حتى AppBar ف أي شاشة يقدر يفتحها
  const chatOpen = useChatStore((s) => s.sheetOpen);
  const chatUserId = useChatStore((s) => s.pendingUserId);
  const closeChat = useChatStore((s) => s.closeChat);
  const consumePending = useChatStore((s) => s.consumePending);
  const openChat = useChatStore((s) => s.openChat);

  const loadUnread = useChatStore((s) => s.loadUnread);
  const chatInit = useChatStore((s) => s.init);
  const chatDispose = useChatStore((s) => s.dispose);

  // WebSocket: من بعد الدخول، وكيتسد ملي كيخرج المستخدم
  useEffect(() => {
    chatInit();
    return () => chatDispose();
  }, [chatInit, chatDispose]);
  const loadUnreadNotifications = useFeedStore((s) => s.loadUnreadNotifications);

  // شارات الشريط العلوي: تحديث كل 15 ثانية (خفيف، بلا ما نفتحو الشاشة)
  useEffect(() => {
    loadUnread();
    loadUnreadNotifications();
    const timer = setInterval(() => {
      loadUnread();
      loadUnreadNotifications();
    }, 15000);
    return () => clearInterval(timer);
  }, [loadUnread, loadUnreadNotifications]);

  const screen = (() => {
    switch (tab) {
      case 'feed':
        return <FeedScreen onCompose={() => setComposeOpen(true)} />;
      case 'profile':
        return <ProfileScreen onCompose={() => setComposeOpen(true)} />;
      case 'friends':
        return <FriendsScreen onMessage={(userId) => openChat(userId)} />;
      case 'reels':
        return <ReelsScreen />;
      case 'search':
        return <SearchScreen onMessage={(userId) => openChat(userId)} />;
      case 'notifications':
        return <NotificationsScreen />;
      case 'menu':
        return (
          <MenuScreen
            onOpenProfile={openProfile}
            onOpenAdmin={openAdmin}
            onOpenFriends={() => setTab('friends')}
            onOpenNotifications={() => setTab('notifications')}
            onOpenChat={() => openChat(null)}
          />
        );
      default:
        return <FeedScreen onCompose={() => setComposeOpen(true)} />;
    }
  })();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.body}>{screen}</View>
      <TabBar active={tab} onChange={setTab} />
      <Toast />
      <ComposeScreen visible={composeOpen} onClose={() => setComposeOpen(false)} />
      <ChatScreen
        visible={chatOpen}
        onClose={closeChat}
        initialUserId={chatUserId}
        onInitialHandled={consumePending}
      />

      {/* لوحة الأدمن كطبقة فوق التطبيق */}
      {adminOpen && isAdmin && (
        <View style={styles.profileOverlay}>
          <AdminScreen />
          <Pressable
            onPress={closeAdmin}
            accessibilityRole="button"
            accessibilityLabel="رجوع"
            style={styles.profileBack}
          >
            <Ionicons name="chevron-forward" size={22} color={colors.text.primary} />
          </Pressable>
        </View>
      )}
    </SafeAreaView>
  );
};

const Gate: React.FC = () => {
  const hydrated = useAuthStore((s) => s.hydrated);
  const user = useAuthStore((s) => s.user);
  const hydrate = useAuthStore((s) => s.hydrate);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  // كل ما تبدّل المستخدم: نعاودو نحمّلو الـ feed ديالو (وضع جديد = مصدر نظيف)
  const userId = user?.id;
  useEffect(() => {
    if (!userId) return;
    const feed = useFeedStore.getState();
    feed.reset();
    feed.load({ force: true });
  }, [userId]);

  if (!hydrated) return <Splash />;
  if (!user) return <AuthScreen />;
  return <Root />;
};

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" backgroundColor={colors.bg.surface} />
      <TelegramGate><Gate /></TelegramGate>
      <UpdateNotice />
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  profileOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.bg.canvas, zIndex: 20, elevation: 20 },
  profileBack: {
    position: 'absolute',
    top: space.xs,
    start: space.xs,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.92)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  safe: { flex: 1, backgroundColor: colors.bg.canvas },
  body: { flex: 1, backgroundColor: colors.bg.canvas },
  splash: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.md,
    backgroundColor: colors.bg.canvas,
  },
  logo: {
    width: 64,
    height: 64,
    borderRadius: 20,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoText: { ...type.display, fontSize: 36, color: colors.text.inverse },
});
