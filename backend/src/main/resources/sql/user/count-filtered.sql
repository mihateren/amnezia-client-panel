SELECT COUNT(*) FROM users
WHERE (:status IS NULL OR status = :status)
  AND (nickname LIKE :search ESCAPE '\' OR internal_name LIKE :search ESCAPE '\')
