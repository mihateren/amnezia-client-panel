SELECT s.tbl_name AS table_name, SUM(d.pgsize) AS allocated_bytes, SUM(d.payload) AS payload_bytes
FROM dbstat('main', 1) d
JOIN sqlite_schema s ON s.name = d.name
WHERE s.type IN ('table', 'index') AND s.tbl_name NOT LIKE 'sqlite_%'
GROUP BY s.tbl_name
ORDER BY s.tbl_name
