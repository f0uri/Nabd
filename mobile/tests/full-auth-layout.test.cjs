const { test } = require('node:test');
const assert = require('node:assert/strict');
const renderUi = require('./helpers/render-ui.cjs');
const { nodes, text } = renderUi;
function fixture({ registration = false, busy = false, error = null } = {}) {
  const calls = [];
  const render = renderUi('screens/AuthScreen.tsx', {
    initialState: { 0: registration ? 'register' : 'login' },
    mocks: {
      'expo-image-picker': {},
      '../store/useAuthStore': {
        useAuthStore: () => ({
          loading: busy,
          error,
          clearError: () => {},
          login: async (...args) => {
            calls.push(args);
            return false;
          },
          register: async () => false,
        }),
      },
    },
  });
  return { render, calls };
}
test('Midnight auth preserves the full responsive layout: full-width gradient, centered greeting, 150px vector and rounded overlapping form', () => {
  const { render } = fixture();
  const tree = render(),
    all = nodes(tree);
  assert.ok(all.some((x) => x.props?.nativeID === 'nabd-full-auth-v2'));
  const gradient = all.find(
    (x) =>
      x.type === 'LinearGradient' &&
      x.props?.colors?.[0] === '#D90E3B' &&
      x.props?.colors?.[1] === '#32121D',
  );
  assert.ok(gradient);
  assert.equal(gradient.props.style.borderBottomLeftRadius, 40);
  const title = all.find((x) => x.props?.accessibilityRole === 'header');
  assert.equal(title.props.style.textAlign, 'center');
  assert.match(text(title), /أهلاً بعودتك/);
  const mascot = all.find((x) => x.type?.name === 'AuthMascot');
  assert.equal(mascot.props.size, 150);
  const form = all.find(
    (x) => x.props?.nativeID === 'nabd-login-responsive-v1',
  );
  assert.equal(form.props.style.marginTop, -36);
  assert.ok(all.some((x) => x.props?.style?.borderRadius === 32));
  assert.match(text(tree), /نسيت كلمة المرور/);
  render.unmount();
});
test('registration contains actual optional avatar, name preview and live four-segment strength meter', () => {
  const { render } = fixture({ registration: true });
  let tree = render();
  const fields = () => nodes(tree).filter((x) => x.type?.name === 'Field');
  assert.equal(fields().length, 3);
  assert.match(text(tree), /اختياري، يمكنك إضافتها لاحقاً/);
  assert.ok(
    nodes(tree).some(
      (x) => x.props?.accessibilityLabel === 'اختيار صورة الحساب',
    ),
  );
  fields()
    .find((x) => x.props.label === 'الاسم الكامل')
    .props.onChangeText('علي حسن');
  fields()
    .find((x) => x.props.label === 'كلمة المرور')
    .props.onChangeText('StrongPassword12!');
  tree = render();
  assert.match(text(tree), /اسم مقترح/);
  assert.ok(
    nodes(tree).some(
      (x) => x.props?.accessibilityLabel === 'قوة كلمة المرور: قوية',
    ),
  );
  render.unmount();
});
test('keyboard contracts header to the supplied 112px character and restores it on dismissal', () => {
  const { render } = fixture();
  let tree = render();
  render.keyboard('keyboardDidShow');
  tree = render();
  assert.equal(
    nodes(tree).find((x) => x.type?.name === 'AuthMascot').props.size,
    112,
  );
  assert.ok(!nodes(tree).some((x) => x.props?.accessibilityRole === 'header'));
  render.keyboard('keyboardDidHide');
  tree = render();
  assert.equal(
    nodes(tree).find((x) => x.type?.name === 'AuthMascot').props.size,
    150,
  );
  render.unmount();
});
test('real auth failure stays an error; there is no fake success, extra delay or credential reset', async () => {
  const { render, calls } = fixture({ error: 'بيانات تسجيل الدخول غير صحيحة' });
  let tree = render();
  nodes(tree)
    .find((x) => x.props?.label === 'البريد الإلكتروني أو اسم المستخدم')
    .props.onChangeText('@existing_user');
  nodes(tree)
    .find((x) => x.props?.label === 'كلمة المرور')
    .props.onChangeText('real-password');
  tree = render();
  await nodes(tree)
    .find((x) => x.props?.accessibilityState?.busy === false)
    .props.onPress();
  tree = render();
  assert.deepEqual(calls, [['@existing_user', 'real-password']]);
  assert.match(text(tree), /بيانات تسجيل الدخول غير صحيحة/);
  assert.ok(!/تم تسجيل الدخول|تم بنجاح/.test(text(tree)));
  assert.equal(
    nodes(tree).find((x) => x.props?.label === 'كلمة المرور').props.value,
    'real-password',
  );
  render.unmount();
});
test('busy requests lock tabs, fields, avatar and duplicate submission in both modes', async () => {
  for (const registration of [true, false]) {
    const { render, calls } = fixture({ registration, busy: true });
    const tree = render(),
      all = nodes(tree);
    for (const field of all.filter((x) => x.type?.name === 'Field'))
      assert.equal(field.props.editable, false);
    for (const tab of all.filter((x) => x.props?.accessibilityRole === 'tab'))
      assert.equal(tab.props.disabled, true);
    const submit = all.find((x) => x.props?.accessibilityState?.busy === true);
    assert.equal(submit.props.disabled, true);
    await submit.props.onPress();
    assert.equal(calls.length, 0);
    render.unmount();
  }
});
