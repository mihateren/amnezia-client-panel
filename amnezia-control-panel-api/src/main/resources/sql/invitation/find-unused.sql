SELECT * FROM invitations WHERE token_hash = :tokenHash AND used_at IS NULL
