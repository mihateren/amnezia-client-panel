SELECT page_count * page_size AS allocated_bytes
FROM pragma_page_count(), pragma_page_size()
