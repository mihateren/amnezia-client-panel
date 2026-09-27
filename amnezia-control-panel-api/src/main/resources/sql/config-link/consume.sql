UPDATE config_links SET used_at = :now WHERE id = :id AND used_at IS NULL
