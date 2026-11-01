import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:meta6_probe/config.dart';
import 'package:meta6_probe/messages/data/graph_api.dart';
import 'package:meta6_probe/net_test/net_test_service.dart';

class FakeAdapter implements HttpClientAdapter {
  final Future<ResponseBody> Function(RequestOptions) respond;
  FakeAdapter(this.respond);
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) => respond(options);
  @override
  void close({bool force = false}) {}
}
Dio client(Future<ResponseBody> Function(RequestOptions) response) {
  final dio = Dio(BaseOptions(baseUrl: 'https://graph.facebook.com',
    followRedirects: false, validateStatus: (_) => true));
  dio.httpClientAdapter = FakeAdapter(response);
  return dio;
}
ResponseBody jsonBody(String data, int status) => ResponseBody.fromString(data,
  status, headers: {Headers.contentTypeHeader: [Headers.jsonContentType]});
const config = ProbeConfig(pageId: '123', token: 'synthetic_test_token', recipientId: '456');

void main() {
  test('all HTTP responses count as reachability, not only 200', () async {
    for (final code in [200, 301, 400, 401, 404, 429, 500]) {
      final service = NetTestService(client: client((_) async => ResponseBody.fromString('', code)));
      final result = await service.http('https://graph.facebook.com');
      expect(result.reachable, isTrue);
      expect(result.httpCode, code);
      expect(result.plain, isNot(contains('synthetic_test_token')));
    }
  });
  test('transport errors fail and never print request/credential details', () async {
    final service = NetTestService(client: client((options) async => throw DioException(
      requestOptions: options, type: DioExceptionType.connectionTimeout,
      message: 'synthetic_test_token')));
    final result = await service.http('https://graph.facebook.com');
    expect(result.reachable, isFalse);
    expect(result.plain, isNot(contains('synthetic_test_token')));
  });
  test('Graph request uses only pinned Meta origin and correct inbox fields', () async {
    final api = GraphApi(client: client((options) async {
      expect(options.uri.host, 'graph.facebook.com');
      expect(options.path, '/v21.0/123/conversations');
      expect(options.queryParameters['fields'], 'messages.limit(10){id,message,from,created_time}');
      return jsonBody('{"data":[{"messages":{"data":[{"id":"m1","message":"سلام","from":{"id":"456","name":"اختبار"},"created_time":"2026-11-01T10:00:00Z"}]}}]}', 200);
    }));
    final messages = await api.receive(config);
    expect(messages.single.id, 'm1');
    expect(messages.single.text, 'سلام');
    expect(messages.single.outgoing, isFalse);
  });
  test('token expiry, permissions and rate limit stay explicit failures', () async {
    for (final pair in [(401, 190, 'expired'), (403, 200, 'permission'), (429, 4, 'rate')]) {
      final api = GraphApi(client: client((_) async => jsonBody('{"error":{"code":${pair.$2}}}', pair.$1)));
      await expectLater(api.receive(config), throwsA(isA<GraphFailure>().having((e) => e.kind, 'kind', pair.$3)));
    }
  });
  test('sending reports success only with a real Graph message ID', () async {
    final api = GraphApi(client: client((options) async {
      expect(options.method, 'POST');
      expect(options.uri.host, 'graph.facebook.com');
      expect((options.data as Map)['recipient']['id'], '456');
      expect((options.data as Map)['messaging_type'], 'RESPONSE');
      return jsonBody('{"recipient_id":"456","message_id":"m_sent"}', 200);
    }));
    expect((await api.send(config, 'مرحبا')).id, 'm_sent');
    final bad = GraphApi(client: client((_) async => jsonBody('{}', 200)));
    await expectLater(bad.send(config, 'مرحبا'), throwsA(isA<GraphFailure>()));
  });
  test('paging cursors do not follow attacker-provided paging URLs', () async {
    var calls = 0;
    final api = GraphApi(client: client((options) async {
      expect(options.uri.host, 'graph.facebook.com');
      calls++;
      return jsonBody(calls == 1
        ? '{"data":[],"paging":{"next":"https://other.invalid/steal","cursors":{"after":"abc"}}}'
        : '{"data":[]}', 200);
    }));
    await api.receive(config);
    expect(calls, 2);
  });
}
