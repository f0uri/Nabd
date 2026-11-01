import 'dart:async';
import 'package:dio/dio.dart';
import '../../config.dart';
import 'message.dart';

class GraphFailure implements Exception {
  final String kind;
  final int? httpCode;
  final int? apiCode;
  const GraphFailure(this.kind, {this.httpCode, this.apiCode});
  String get safeStatus => (switch (kind) {
    'expired' => 'التوكن منتهي أو غير صالح: جدده من إعدادات الصفحة.',
    'permission' => 'صلاحيات الصفحة أو نافذة الرد لا تسمح بالعملية.',
    'rate' => 'حد الطلبات: المزامنة متوقفة مؤقتًا لتخفيف الطلبات.',
    'network' => 'تعذر الاتصال بـGraph: تأكد من الشبكة ونتيجة NetTest.',
    _ => 'استجابة Graph غير صالحة لهذه العملية.',
  }) + ' [HTTP ${httpCode ?? "-"} / API ${apiCode ?? "-"}]';
}

class GraphApi {
  final Dio dio;
  GraphApi({Dio? client}) : dio = client ?? Dio(BaseOptions(
    baseUrl: 'https://graph.facebook.com', connectTimeout: const Duration(seconds: 8),
    sendTimeout: const Duration(seconds: 8), receiveTimeout: const Duration(seconds: 8),
    followRedirects: false, validateStatus: (_) => true));

  Future<Map<String, dynamic>> _request(String path, ProbeConfig config,
      {Map<String, dynamic>? query, Map<String, dynamic>? body}) async {
    try {
      // Relative paths only. Never follow an API-provided URL to another host.
      if (!path.startsWith('/$graphVersion/')) throw const GraphFailure('response');
      final cancel = CancelToken();
      final response = await dio.request<Map<String, dynamic>>(path,
        queryParameters: query, data: body, cancelToken: cancel,
        options: Options(method: body == null ? 'GET' : 'POST',
          headers: {'Authorization': 'Bearer ${config.token}'}))
        .timeout(const Duration(seconds: 8), onTimeout: () {
          cancel.cancel('deadline');
          throw TimeoutException('deadline');
        });
      final data = response.data ?? {};
      final error = data['error'];
      final code = error is Map ? error['code'] as int? : null;
      if (error != null || (response.statusCode ?? 500) >= 300) {
        throw GraphFailure(code == 190 || response.statusCode == 401 ? 'expired'
          : [4, 17, 32, 613].contains(code) || response.statusCode == 429 ? 'rate'
          : [10, 200, 551].contains(code) || response.statusCode == 403 ? 'permission' : 'response',
          httpCode: response.statusCode, apiCode: code);
      }
      return data;
    } on GraphFailure { rethrow; }
    on TimeoutException { throw const GraphFailure('network'); }
    on DioException { throw const GraphFailure('network'); }
    catch (_) { throw const GraphFailure('response'); }
  }

  ProbeMessage? _message(dynamic raw, String pageId) {
    if (raw is! Map || raw['id'] is! String || raw['message'] is! String) return null;
    final sender = raw['from'] is Map ? raw['from'] as Map : const {};
    return ProbeMessage(id: raw['id'], pageId: pageId, text: raw['message'],
      fromId: '${sender['id'] ?? ""}', fromName: '${sender['name'] ?? "مشارك"}',
      created: DateTime.tryParse('${raw['created_time']}')?.toUtc().toIso8601String()
        ?? DateTime.now().toUtc().toIso8601String());
  }

  Future<List<ProbeMessage>> receive(ProbeConfig config) async {
    final result = <ProbeMessage>[];
    String? after;
    // Bounded pagination. This is a small text experiment, not a complete inbox backup.
    for (var page = 0; page < 3; page++) {
      final data = await _request('/$graphVersion/${config.pageId}/conversations', config,
        query: {'fields': 'messages.limit(10){id,message,from,created_time}',
          'limit': 25, if (after != null) 'after': after});
      if (data['data'] is! List) throw const GraphFailure('response');
      for (final conversation in data['data'] as List) {
        if (conversation is! Map) continue;
        final messages = conversation['messages'];
        if (messages is! Map || messages['data'] is! List) continue;
        for (final raw in messages['data'] as List) {
          final message = _message(raw, config.pageId);
          if (message != null) result.add(message);
        }
      }
      final paging = data['paging'];
      final cursors = paging is Map ? paging['cursors'] : null;
      if (paging is! Map || paging['next'] == null || cursors is! Map || cursors['after'] is! String) break;
      after = cursors['after'];
    }
    return result;
  }

  Future<ProbeMessage> send(ProbeConfig config, String text) async {
    if (!RegExp(r'^\d+$').hasMatch(config.recipientId) || text.trim().isEmpty || text.length > 2000) {
      throw const GraphFailure('response');
    }
    final data = await _request('/$graphVersion/${config.pageId}/messages', config,
      body: {'recipient': {'id': config.recipientId}, 'messaging_type': 'RESPONSE',
        'message': {'text': text.trim()}});
    if (data['message_id'] is! String || (data['message_id'] as String).trim().isEmpty) throw const GraphFailure('response');
    return ProbeMessage(id: data['message_id'], pageId: config.pageId, text: text.trim(),
      fromId: config.pageId, fromName: 'الصفحة التجريبية',
      created: DateTime.now().toUtc().toIso8601String());
  }
}
