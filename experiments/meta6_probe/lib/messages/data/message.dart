class ProbeMessage {
  final String id;
  final String pageId;
  final String text;
  final String fromId;
  final String fromName;
  final String created;
  const ProbeMessage({required this.id, required this.pageId, required this.text,
    required this.fromId, required this.fromName, required this.created});
  bool get outgoing => fromId == pageId;
  Map<String, Object?> get row => {'message_id': id, 'page_id': pageId,
    'body': text, 'from_id': fromId, 'from_name': fromName, 'created': created,
    'notified': outgoing ? 1 : 0};
  factory ProbeMessage.fromRow(Map<String, Object?> row) => ProbeMessage(
    id: row['message_id'] as String, pageId: row['page_id'] as String,
    text: row['body'] as String, fromId: row['from_id'] as String,
    fromName: row['from_name'] as String, created: row['created'] as String);
}
