SELECT * FROM users
WHERE (:status IS NULL OR status = :status)
  AND (nickname LIKE :search ESCAPE '\' OR internal_name LIKE :search ESCAPE '\')
ORDER BY created_at DESC, id
LIMIT :limit OFFSET :offset
