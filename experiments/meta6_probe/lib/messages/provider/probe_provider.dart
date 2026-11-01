import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config.dart';
import '../data/background.dart';
import '../data/local_store.dart';
import '../data/message.dart';
import '../data/sync_service.dart';

class ProbeState {
  final List<ProbeMessage> messages;
  final String status;
  final bool busy;
  const ProbeState({this.messages = const [], this.status = 'ابدأ باختبار الشبكة.', this.busy = false});
}
class ProbeController extends StateNotifier<ProbeState> {
  final LocalStore store;
  final ConfigStore configStore;
  final SyncService sync;
  Timer? _timer;
  bool _active = true;
  ProbeController(this.store, this.configStore, this.sync) : super(const ProbeState());
  Future<void> refresh() async {
    final config = await configStore.load();
    final messages = await store.messages(config.pageId);
    final status = await store.value('status') ?? state.status;
    if (mounted) state = ProbeState(messages: messages, status: status, busy: state.busy);
  }
  Future<void> poll() async {
    if (state.busy) return;
    state = ProbeState(messages: state.messages, status: state.status, busy: true);
    await sync.poll();
    if (!mounted) return;
    state = ProbeState(messages: state.messages, status: state.status);
    await refresh();
  }
  Future<bool> send(String text) async {
    if (state.busy) return false;
    state = ProbeState(messages: state.messages, status: state.status, busy: true);
    final result = await sync.send(text);
    if (!mounted) return false;
    state = ProbeState(messages: state.messages, status: result);
    await refresh();
    return result.startsWith('تم الإرسال:');
  }
  Future<void> configured() async {
    await sync.resetBackoff();
    await refresh();
    final config = await configStore.load();
    await scheduleBackground(config.background);
    if (_active) start();
  }
  void start() {
    _active = true;
    _timer?.cancel();
    unawaited(refresh());
    _timer = Timer.periodic(foregroundInterval, (_) => unawaited(poll()));
  }
  void pause() { _active = false; _timer?.cancel(); }
  @override
  void dispose() { _timer?.cancel(); super.dispose(); }
}
final probeProvider = StateNotifierProvider<ProbeController, ProbeState>(
  (_) => throw StateError('Probe services must be initialized before rendering'));
