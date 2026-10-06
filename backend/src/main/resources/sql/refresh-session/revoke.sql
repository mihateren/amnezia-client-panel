UPDATE refresh_sessions SET revoked_at = :now WHERE token_hash = :tokenHash AND revoked_at IS NULL
