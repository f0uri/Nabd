import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';

const netTargets = <String>[
  'https://graph.facebook.com', 'https://graph.instagram.com',
  'https://www.facebook.com', 'https://i.instagram.com',
  'https://web.whatsapp.com', 'https://fcm.googleapis.com',
  'https://www.google.com', 'tcp://mtalk.google.com:5228',
];
const probeTimeout = Duration(seconds: 8);

class NetResult {
  final String target;
  final bool reachable;
  final int elapsedMs;
  final int? httpCode;
  final String? error;
  final String? redirectHost;
  const NetResult(this.target, this.reachable, this.elapsedMs,
      {this.httpCode, this.error, this.redirectHost});
  String get plain => '${reachable ? "OK" : "FAIL"} $target '
      '${httpCode == null ? error ?? "TCP_CONNECTED" : "HTTP $httpCode"} '
      '${elapsedMs}ms${redirectHost == null ? "" : " redirectHost=$redirectHost"}';
}

class NetTestService {
  final Dio dio;
  NetTestService({Dio? client}) : dio = client ?? Dio(BaseOptions(
    connectTimeout: probeTimeout, receiveTimeout: probeTimeout,
    sendTimeout: probeTimeout));

  Future<NetResult> http(String target) async {
    final watch = Stopwatch()..start();
    final cancel = CancelToken();
    try {
      final response = await dio.get<ResponseBody>(target,
        cancelToken: cancel,
        options: Options(responseType: ResponseType.stream, followRedirects: false,
          validateStatus: (_) => true)).timeout(probeTimeout, onTimeout: () {
            cancel.cancel('probe deadline');
            throw TimeoutException('probe deadline');
          });
      final subscription = response.data?.stream.listen((_) {});
      await subscription?.cancel(); // Read headers only, not Facebook/WhatsApp bodies.
      final location = response.headers.value('location');
      return NetResult(target, true, watch.elapsedMilliseconds,
        httpCode: response.statusCode,
        redirectHost: location == null ? null : Uri.tryParse(location)?.host);
    } on TimeoutException {
      return NetResult(target, false, watch.elapsedMilliseconds, error: 'timeout');
    } on DioException catch (error) {
      // No URLs containing credentials are ever copied/logged.
      if (error.response?.statusCode != null) {
        return NetResult(target, true, watch.elapsedMilliseconds,
          httpCode: error.response!.statusCode);
      }
      return NetResult(target, false, watch.elapsedMilliseconds,
        error: error.error is SocketException ? 'DNS/socket' : error.type.name);
    } catch (_) {
      return NetResult(target, false, watch.elapsedMilliseconds, error: 'transport');
    }
  }

  Future<NetResult> tcp() async {
    final watch = Stopwatch()..start();
    try {
      final socket = await Socket.connect('mtalk.google.com', 5228,
        timeout: probeTimeout);
      socket.destroy();
      return NetResult(netTargets.last, true, watch.elapsedMilliseconds);
    } on SocketException {
      return NetResult(netTargets.last, false, watch.elapsedMilliseconds,
        error: 'DNS/socket/timeout');
    } catch (_) {
      return NetResult(netTargets.last, false, watch.elapsedMilliseconds,
        error: 'connection');
    }
  }

  Future<List<NetResult>> run() async {
    // At most four concurrent probes; each request has a total eight-second cap.
    final results = <NetResult>[];
    for (var index = 0; index < netTargets.length - 1; index += 4) {
      results.addAll(await Future.wait(netTargets.take(netTargets.length - 1)
        .skip(index).take(4).map(http)));
    }
    results.add(await tcp());
    return results;
  }
}
