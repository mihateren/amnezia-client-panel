SELECT * FROM audit_log
WHERE (:eventType IS NULL OR event_type = :eventType)
  AND (:actorUserId IS NULL OR actor_user_id = :actorUserId)
  AND (:result IS NULL OR result = :result)
  AND (:from IS NULL OR julianday(created_at) >= julianday(:from))
  AND (:to IS NULL OR julianday(created_at) <= julianday(:to))
ORDER BY created_at DESC, id
LIMIT :limit OFFSET :offset
