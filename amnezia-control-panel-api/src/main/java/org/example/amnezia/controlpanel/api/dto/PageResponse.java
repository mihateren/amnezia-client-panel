package org.example.amnezia.controlpanel.api.dto;

import java.util.List;
import org.example.amnezia.controlpanel.api.exception.ApiException;

public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {
  public PageResponse {
    content = List.copyOf(content);
  }

  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw ApiException.invalid();
  }

  public static <T> PageResponse<T> of(List<T> content, int page, int size, long count) {
    validate(page, size);
    return new PageResponse<>(
        content, page, size, count, (int) Math.min(Integer.MAX_VALUE, (count + size - 1) / size));
  }

  public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
    validate(page, size);
    int start = (int) Math.min(all.size(), (long) page * size);
    return of(all.subList(start, Math.min(all.size(), start + size)), page, size, all.size());
  }
}
