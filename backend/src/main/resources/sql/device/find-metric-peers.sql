SELECT d.id, d.name, d.client_public_key, u.internal_name AS user_name
FROM devices d
JOIN users u ON u.id = d.user_id
ORDER BY d.id
