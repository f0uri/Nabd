const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const loader = require('./helpers/load-source.cjs');
const renderUi = require('./helpers/render-ui.cjs');
const { nodes, text } = renderUi;
const { filterFeed } = loader()('utils/feedFilters.ts');

const posts = [
  { id: 'text', author: { isFriend: true } },
  { id: 'legacy-photo', author: {}, media: { uri: 'photo.jpg' } },
  { id: 'photo', author: { isFriend: false }, media: { kind: 'image' } },
  { id: 'video', author: { isFriend: true }, media: { kind: 'video' } },
];
const ids = (rows) => Array.from(rows, (post) => post.id);

test('filters use real friendship/media metadata, include legacy photos and never mutate/reorder shared posts', () => {
  const original = JSON.stringify(posts);
  assert.equal(filterFeed(posts, 'all'), posts);
  assert.deepEqual(ids(filterFeed(posts, 'friends')), ['text', 'video']);
  assert.deepEqual(ids(filterFeed(posts, 'photos')), ['legacy-photo', 'photo']);
  assert.deepEqual(ids(filterFeed(posts, 'videos')), ['video']);
  assert.equal(filterFeed([], 'photos').length, 0);
  assert.equal(JSON.stringify(posts), original);
});

test('each filter chip is a real selectable 44dp target, not a decorative category', () => {
  let chosen;
  const render = renderUi('components/FeedFilters.tsx');
  const tree = render({ value: 'photos', onChange: (key) => { chosen = key; } });
  const tabs = nodes(tree).filter((node) => node.props?.accessibilityRole === 'tab');
  assert.equal(tabs.length, 4);
  assert.equal(tabs.filter((node) => node.props.accessibilityState.selected).length, 1);
  for (const tab of tabs) {
    assert.ok(tab.props.accessibilityLabel);
    assert.ok(tab.props.style({ pressed: false })[0].minHeight >= 44);
  }
  tabs[3].props.onPress();
  assert.equal(chosen, 'videos');
  assert.equal(text(tabs[2]).trim(), 'الصور');
  render.unmount();
});

function feedFixture(initialPosts = posts) {
  let pages = 0;
  const noop = () => {};
  const state = {
    posts: initialPosts, nextCursor: 'another-page', loading: false, refreshing: false,
    loadingMore: false, load: noop, refresh: noop, loadMore: () => { pages++; },
  };
  const story = { stories: [], hydrate: noop };
  const visual = Object.fromEntries(['CommentsSheet', 'StoryTray', 'StoryViewer', 'Composer', 'PostCard', 'FeedSkeleton']
    .map((name) => [`../components/organisms/${name}`, { __esModule: true, default: name }]));
  const render = renderUi('screens/FeedScreen.tsx', { mocks: {
    ...visual,
    '@shopify/flash-list': { FlashList: 'FlashList' },
    'expo-image-picker': {},
    '../api/mockApi': { CURRENT_USER: { id: 'test-user' } },
    '../store/useFeedStore': { useFeedStore: (select) => select(state) },
    '../store/useAuthStore': { useAuthStore: (select) => select({ user: null }) },
    '../store/useChatStore': { useChatStore: (select) => select({}) },
    '../store/useStoryStore': { useStoryStore: (select) => select(story) },
    '../store/useUiStore': { useUiStore: (select) => select({}) },
  } });
  return { render, state, get pages() { return pages; } };
}

test('an empty filtered page still offers explicit pagination and reset; no unbounded auto-fetch loop', () => {
  const fixture = feedFixture([posts[0]]);
  let tree = fixture.render();
  const filters = nodes(tree).find((node) => node.type?.name === 'FeedFilters');
  filters.props.onChange('videos');
  tree = fixture.render();
  let list = nodes(tree).find((node) => node.type === 'FlashList');
  assert.equal(list.props.data.length, 0);
  assert.equal(list.props.onEndReached, undefined);
  assert.match(text(list.props.ListEmptyComponent), /التصفية.*المنشورات المحمّلة/);
  const more = nodes(list.props.ListFooterComponent).find((node) => node.type === 'Pressable');
  more.props.onPress();
  assert.equal(fixture.pages, 1);
  const reset = nodes(list.props.ListEmptyComponent).find((node) => /عرض الكل/.test(text(node)) && node.type === 'Pressable');
  reset.props.onPress();
  tree = fixture.render();
  list = nodes(tree).find((node) => node.type === 'FlashList');
  assert.equal(list.props.data, fixture.state.posts);
  assert.equal(list.props.onEndReached, fixture.state.loadMore);
  assert.ok(list.props.contentContainerStyle.paddingBottom >= 100);
  fixture.render.unmount();
});

test('filter changes update actual list data, and filtered pagination disappears once the source is exhausted', () => {
  const fixture = feedFixture();
  let tree = fixture.render();
  nodes(tree).find((node) => node.type?.name === 'FeedFilters').props.onChange('photos');
  fixture.state.nextCursor = null;
  tree = fixture.render();
  const list = nodes(tree).find((node) => node.type === 'FlashList');
  assert.deepEqual(ids(list.props.data), ['legacy-photo', 'photo']);
  assert.ok(!nodes(list.props.ListFooterComponent).some((node) => node.type === 'Pressable'));
  fixture.render.unmount();
});

test('native blur opts into the supported Android backend and responds to reduced-transparency changes', async () => {
  let onChange, removed = 0;
  const render = renderUi('components/GlassDock.tsx', { mocks: {
    'react-native': {
      Platform: { OS: 'android' }, View: 'View',
      StyleSheet: { create: (styles) => styles, absoluteFillObject: { position: 'absolute' } },
      AccessibilityInfo: {
        isReduceTransparencyEnabled: async () => false,
        addEventListener: (name, listener) => {
          assert.equal(name, 'reduceTransparencyChanged'); onChange = listener;
          return { remove: () => { removed++; } };
        },
      },
    },
  } });
  assert.ok(!nodes(render()).some((node) => node.type === 'BlurView'), 'start with a safe opaque fallback');
  await Promise.resolve();
  const blur = nodes(render()).find((node) => node.type === 'BlurView');
  assert.ok(blur);
  assert.equal(blur.props.experimentalBlurMethod, 'dimezisBlurView');
  onChange(true);
  assert.ok(!nodes(render()).some((node) => node.type === 'BlurView'));
  render.unmount();
  assert.equal(removed, 1);
});

test('dock content clearance accounts for gesture navigation and larger accessibility fonts', () => {
  const inset = (bottom, fontScale) => loader({ mocks: {
    'react-native': {
      Platform: { OS: 'android', select: (values) => values.android },
      useWindowDimensions: () => ({ fontScale }),
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom }) },
  } })('components/useDockInset.ts').default();
  assert.equal(inset(0, 1), 100);
  assert.equal(inset(48, 1), 140);
  assert.ok(inset(48, 2) > inset(48, 1));
});

test('video cards do not instantiate or auto-play a player before the user opens the video', () => {
  const render = renderUi('components/organisms/FeedVideo.tsx');
  const media = { uri: 'https://example.invalid/video.mp4', kind: 'video', aspectRatio: 16 / 9 };
  let tree = render({ media });
  assert.ok(!nodes(tree).some((node) => node.type?.name === 'Player'));
  nodes(tree).find((node) => node.type === 'Pressable').props.onPress();
  tree = render({ media });
  assert.ok(nodes(tree).some((node) => node.type?.name === 'Player'));
  render.unmount();
});

test('new APK keeps the application identity, uses a new version code and fingerprint compatibility', () => {
  const config = require('../app.json').expo;
  assert.equal(config.android.package, 'app.nabd.social');
  assert.equal(config.version, '1.2.0');
  assert.equal(config.android.versionCode, 3);
  assert.equal(config.userInterfaceStyle, 'dark');
  assert.deepEqual(require('../app.config.js')().runtimeVersion, { policy: 'fingerprint' });
  assert.match(fs.readFileSync(path.join(__dirname, '../App.tsx'), 'utf8'), /barStyle="light-content"/);
});
