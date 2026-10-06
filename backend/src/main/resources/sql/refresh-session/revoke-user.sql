UPDATE refresh_sessions SET revoked_at = :now WHERE user_id = :userId AND revoked_at IS NULL
