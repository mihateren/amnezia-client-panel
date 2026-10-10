UPDATE users SET password_hash = :passwordHash, updated_at = :now WHERE id = :userId;
