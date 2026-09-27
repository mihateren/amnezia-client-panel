INSERT INTO audit_log (id, event_type, result, actor_user_id, object_type, object_id, created_at, correlation_id)
VALUES (:id, :eventType, :result, :actorUserId, :objectType, :objectId, :createdAt, :correlationId)
