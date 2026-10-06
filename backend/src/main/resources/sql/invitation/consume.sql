UPDATE invitations SET used_at = :now, used_by = :userId WHERE id = :id AND used_at IS NULL
