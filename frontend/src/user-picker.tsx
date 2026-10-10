import { useCallback, useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { query, request, type Schema } from "./api";
import { ErrorBox } from "./ui";

export function userOptionLabel(user: Schema<"AdminUser">) {
  const name = user.internalName?.trim();
  const label =
    name && name !== user.nickname
      ? `${user.nickname} — ${name}`
      : user.nickname;
  return user.status === "DISABLED" ? `${label} (отключён)` : label;
}

export function UserPicker({
  value,
  onChange,
  searchable = false,
}: {
  searchable?: boolean;
  value: string;
  onChange: (id: string) => void;
}) {
  const [search, setSearch] = useState("");
  const [users, setUsers] = useState<Schema<"AdminUser">[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((current) => current + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    async function load() {
      try {
        const collected = new Map<string, Schema<"AdminUser">>();
        for (let page = 0; !controller.signal.aborted; page++) {
          const result = await request<Schema<"UserPage">>(
            `/admin/users?${query({ page, size: 100 })}`,
            { signal: controller.signal },
          );
          if (controller.signal.aborted) return;
          result.content.forEach((user) => collected.set(user.id, user));
          if (page + 1 >= result.totalPages || result.content.length === 0)
            break;
        }
        if (!controller.signal.aborted) {
          setUsers(
            [...collected.values()].sort((a, b) =>
              userOptionLabel(a).localeCompare(userOptionLabel(b), "ru"),
            ),
          );
        }
      } catch (failure) {
        if (!controller.signal.aborted) setError(failure);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [revision]);
  return (
    <div
      className={
        searchable ? "user-picker searchable-user-picker" : "user-picker"
      }
    >
      {searchable && (
        <label>
          Поиск пользователя
          <input
            placeholder="Имя или никнейм"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              onChange("");
            }}
          />
        </label>
      )}
      <label>
        Пользователь
        <select
          aria-label="Пользователь"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={loading || !!error}
          aria-busy={loading}
        >
          <option value="">
            {loading
              ? "Загружаем пользователей…"
              : error
                ? "Не удалось загрузить пользователей"
                : "Все пользователи"}
          </option>
          {value && !users.some((user) => user.id === value) && (
            <option value={value}>Выбранный пользователь недоступен</option>
          )}
          {users
            .filter(
              (user) =>
                !searchable ||
                userOptionLabel(user)
                  .toLocaleLowerCase("ru")
                  .includes(search.trim().toLocaleLowerCase("ru")),
            )
            .map((user) => (
              <option key={user.id} value={user.id}>
                {userOptionLabel(user)}
              </option>
            ))}
        </select>
      </label>
      {searchable &&
        search &&
        !loading &&
        !error &&
        !users.some((user) =>
          userOptionLabel(user)
            .toLocaleLowerCase("ru")
            .includes(search.trim().toLocaleLowerCase("ru")),
        ) && (
          <small role="status">
            Пользователь не найден. Попробуйте другое имя.
          </small>
        )}
      {!loading && !error && users.length === 0 && (
        <small>Пользователей пока нет.</small>
      )}
      <ErrorBox error={error} />
      {!!error && (
        <button type="button" onClick={reload}>
          <RefreshCw size={16} />
          Повторить загрузку
        </button>
      )}
    </div>
  );
}
