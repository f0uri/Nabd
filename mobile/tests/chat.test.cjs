const { test } = require('node:test');
const assert = require('node:assert/strict');
const load = require('./helpers/load-source.cjs');
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
const message = (id, senderId = 'other', read = false) => ({
  id,
  senderId,
  read,
  conversationId: 'c',
  text: id,
  createdAt: '2026-11-01T12:00:00Z',
});
function fixture(overrides = {}) {
  const calls = { messages: 0, reads: 0, toasts: [] };
  const api = {
    conversations: async () => [],
    messages: async () => {
      calls.messages++;
      return {
        messages: [message('incoming'), message('outgoing', 'me')],
        nextCursor: null,
      };
    },
    markConversationRead: async () => {
      calls.reads++;
      return true;
    },
    unreadMessagesCount: async () => 0,
    sendMessage: async () => message('saved', 'me'),
    ...overrides,
  };
  const realtime = {
    subscribe() {},
    unsubscribe() {},
    connect() {},
    disconnect() {},
    onEvent: () => () => {},
    onStatus: () => () => {},
  };
  const store = load({
    mocks: {
      '../api': { dataApi: api },
      '../api/client': { normalizeMessage: (m) => m },
      '../api/realtime': { realtime },
      './useAuthStore': {
        useAuthStore: { getState: () => ({ user: { id: 'me', name: 'Me' } }) },
      },
      './useFeedStore': {
        useFeedStore: {
          getState: () => ({ showToast: (...args) => calls.toasts.push(args) }),
        },
      },
    },
  })('store/useChatStore.ts').useChatStore;
  return { store, state: () => store.getState(), calls };
}
test('thread refresh merges arrivals without dropping older history or optimistic messages; own sent messages are not marked read', async () => {
  const { state, store } = fixture();
  store.setState({
    threads: { c: [message('old'), message('tmp', 'me')] },
    nextCursor: { c: 'old-cursor' },
  });
  await state().openConversation('c');
  assert.deepEqual(
    Array.from(state().threads.c, (m) => m.id),
    ['old', 'tmp', 'incoming', 'outgoing'],
  );
  assert.equal(state().threads.c.find((m) => m.id === 'incoming').read, true);
  assert.equal(state().threads.c.find((m) => m.id === 'outgoing').read, false);
  assert.equal(state().nextCursor.c, 'old-cursor');
});
test('overlapping thread polls coalesce and stale results after reset do not leak to another session', async () => {
  const wait = deferred();
  const f = fixture({
    messages: () => {
      f.calls.messages++;
      return wait.promise;
    },
  });
  const first = f.state().openConversation('c');
  await f.state().openConversation('c');
  assert.equal(f.calls.messages, 1);
  f.state().reset();
  wait.resolve({ messages: [message('private')], nextCursor: null });
  await first;
  assert.equal(Object.keys(f.state().threads).length, 0);
  assert.equal(f.calls.reads, 0);
  assert.equal(f.state().activeId, null);
});
test('search results arriving out of order cannot overwrite the current query', async () => {
  const first = deferred(),
    second = deferred();
  const f = fixture({
    conversations: (term) =>
      term === 'first' ? first.promise : second.promise,
  });
  const a = f.state().loadConversations('first'),
    b = f.state().loadConversations('second');
  second.resolve([{ id: 'second' }]);
  await b;
  first.resolve([{ id: 'first' }]);
  await a;
  assert.equal(f.state().conversations[0].id, 'second');
});
test('thread failures are explicit and retryable, not presented as an empty conversation', async () => {
  let fail = true;
  const f = fixture({
    messages: async () => {
      if (fail) throw Error('secret provider detail');
      return { messages: [], nextCursor: null };
    },
  });
  await f.state().openConversation('c');
  assert.match(f.state().threadError, /تعذّر/);
  assert.ok(!f.state().threadError.includes('secret'));
  assert.equal(f.state().threadLoading, false);
  fail = false;
  await f.state().openConversation('c');
  assert.equal(f.state().threadError, null);
});
test('a failed send removes the optimistic bubble, reports a safe error, and leaves no fake success', async () => {
  const f = fixture({
    sendMessage: async () => {
      throw Object.assign(Error('private URL'), { code: 'TELEGRAM_CONFLICT' });
    },
  });
  assert.equal(await f.state().send('c', 'hello'), false);
  assert.equal(f.state().threads.c.length, 0);
  assert.equal(f.state().sending, false);
  assert.match(f.calls.toasts[0][0], /تغيّرت البيانات/);
  assert.ok(!f.calls.toasts[0][0].includes('private'));
});
test('send acknowledgements deduplicate a message already observed by polling and cannot repopulate reset stores', async () => {
  const wait = deferred();
  const f = fixture({ sendMessage: () => wait.promise });
  const sending = f.state().send('c', 'hello');
  f.store.setState({
    threads: { c: [...f.state().threads.c, message('saved', 'me')] },
  });
  wait.resolve(message('saved', 'me'));
  assert.equal(await sending, true);
  assert.equal(f.state().threads.c.length, 1);
  const late = deferred();
  const next = fixture({ sendMessage: () => late.promise });
  const pending = next.state().send('c', 'x');
  next.state().reset();
  late.resolve(message('saved', 'me'));
  assert.equal(await pending, false);
  assert.equal(Object.keys(next.state().threads).length, 0);
});
test('message screen offers retry after failure and a real friend-selection action when empty', async () => {
  const renderUi = require('./helpers/render-ui.cjs');
  let tab,
    closed = 0,
    retries = 0;
  const model = {
    conversations: [],
    loading: false,
    refreshing: false,
    error: 'تعذّر الاتصال',
    typing: {},
    loadConversations: () => {
      retries++;
    },
    refresh: () => {},
    openWith: async () => null,
    loadUnread: () => {},
  };
  const render = renderUi('screens/ChatScreen.tsx', {
    mocks: {
      './ConversationScreen': { __esModule: true, default: 'Conversation' },
      '../store/useChatStore': { useChatStore: (select) => select(model) },
      '../store/useAuthStore': {
        useAuthStore: (select) => select({ user: { id: 'me' } }),
      },
      '../store/useUiStore': {
        useUiStore: (select) =>
          select({
            setTab: (value) => {
              tab = value;
            },
          }),
      },
    },
  });
  const props = {
    visible: true,
    onClose: () => {
      closed++;
    },
  };
  let screen = render(props);
  let empty = renderUi.nodes(screen).find((n) => n.type === 'FlatList')
    .props.ListEmptyComponent;
  assert.match(renderUi.text(empty), /إعادة المحاولة/);
  renderUi
    .nodes(empty)
    .find((n) => n.type === 'Pressable')
    .props.onPress();
  assert.equal(retries, 1);
  model.error = null;
  screen = render(props);
  empty = renderUi.nodes(screen).find((n) => n.type === 'FlatList')
    .props.ListEmptyComponent;
  const choose = renderUi.nodes(empty).find((n) => n.type === 'Pressable');
  assert.equal(renderUi.text(choose), 'اختيار صديق');
  choose.props.onPress();
  assert.equal(tab, 'friends');
  assert.equal(closed, 1);
  render.unmount();
});
test('a conversation-open error survives an unrelated successful inbox refresh and clears on a successful retry', async () => {
  let fail = true;
  const f = fixture({
    startConversation: async () => {
      if (fail) throw Error('offline');
      return { id: 'c', other: { name: 'Partner' } };
    },
  });
  await f.state().openWith('partner');
  await f.state().loadConversations();
  assert.match(f.state().openError, /نفتحو/);
  assert.equal(f.state().error, null);
  fail = false;
  assert.equal((await f.state().openWith('partner')).id, 'c');
  assert.equal(f.state().openError, null);
});
test('late edit/delete/reaction acknowledgements do not restore data after switching sessions', async () => {
  for (const [method, apiMethod] of [
    ['editMessage', 'editMessage'],
    ['deleteMessage', 'deleteMessage'],
    ['reactMessage', 'reactToMessage'],
  ]) {
    const wait = deferred(),
      f = fixture({ [apiMethod]: () => wait.promise });
    f.store.setState({ threads: { c: [message('existing', 'me')] } });
    const action = f
      .state()
      [method]('existing', method === 'reactMessage' ? 'love' : 'updated');
    f.state().reset();
    wait.resolve(message('existing', 'me'));
    assert.equal(await action, false);
    assert.equal(Object.keys(f.state().threads).length, 0);
  }
});
