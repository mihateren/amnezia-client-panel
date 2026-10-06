DELETE FROM invitations WHERE created_by = :createdBy AND used_at IS NULL
