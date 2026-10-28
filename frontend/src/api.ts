import type { components } from "./schema";
export type Schema<K extends keyof components["schemas"]> =
  components["schemas"][K];
let token: string | null = null;
let generation = 0;
let refreshing: Promise<void> | null = null;
export function setToken(value: string | null) {
  generation++;
  token = value;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public correlationId?: string,
  ) {
    super(message);
  }
}
const messages: Record<string, string> = {
  UNAUTHORIZED: "Сессия завершилась. Войдите снова.",
  FORBIDDEN: "Недостаточно прав для этого действия.",
  NOT_FOUND:
    "Не удалось найти данные. Возможно, запись уже удалена или ссылка истекла.",
  VALIDATION_ERROR: "Проверьте заполненные поля и повторите попытку.",
  DEVICE_LIMIT: "Достигнут лимит: 5 устройств. Удалите ненужное устройство.",
  NICKNAME_TAKEN: "Этот никнейм уже занят. Выберите другой.",
  ADMIN_PROTECTED: "Нельзя отключить или удалить администратора.",
  VPN_UNAVAILABLE:
    "VPN-сервер недоступен. Повторите позже или выберите другой сервер.",
  SSH_OPERATION_FAILED:
    "Не удалось выполнить действие на VPN-сервере. Повторите позже или обратитесь к администратору.",
  SSH_AUTH_FAILED:
    "Не удалось войти на сервер. Проверьте логин и пароль сервера.",
  CONFIG_CHANGED: "Настройки сервера изменились. Повторите действие.",
  PEER_EXISTS: "Подключение уже существует. Обновите список устройств.",
  VPN_IP_EXHAUSTED:
    "На сервере закончились свободные адреса. Выберите другой сервер.",
  CURRENT_PASSWORD_INVALID: "Текущий пароль неверен. Проверьте его и повторите попытку.",
  RATE_LIMITED: "Слишком много попыток. Подождите немного и попробуйте снова.",
};
async function failure(response: Response, path = ""): Promise<never> {
  const data = await response.json().catch(() => ({}));
  throw new ApiError(
    response.status,
    data.code || "HTTP_ERROR",
    path === "/auth/login" && response.status === 401
      ? "Не удалось войти. Проверьте пароль. Если он верный, уточните у администратора, активен ли аккаунт."
      : messages[data.code] ||
          (typeof data.message === "string" && /[а-яё]/i.test(data.message)
            ? data.message
            : response.status === 429
              ? "Слишком много попыток. Повторите чуть позже."
              : response.status >= 500
                ? "Сервис временно недоступен. Повторите позже."
                : "Не удалось выполнить действие. Обновите страницу и повторите попытку."),
    data.correlationId,
  );
}
export async function refresh() {
  if (!refreshing) {
    const current = generation;
    refreshing = (async () => {
      const response = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
      }).catch(() => {
        throw new ApiError(
          0,
          "NETWORK",
          "Нет связи с сервером. Проверьте подключение и повторите попытку.",
        );
      });
      if (!response.ok) await failure(response);
      const data: Schema<"TokenResponse"> = await response.json();
      if (current === generation) token = data.accessToken;
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
  kind: "json" | "text" | "blob" = "json",
  retry = true,
): Promise<T> {
  const publicEndpoint =
    path === "/auth/login" ||
    path === "/auth/refresh" ||
    path.startsWith("/invitations/");
  const sentToken = token;
  const sentGeneration = generation;
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      ...options,
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(!publicEndpoint && token
          ? { Authorization: `Bearer ${token}` }
          : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new ApiError(
      0,
      "NETWORK",
      "Нет связи с сервером. Проверьте подключение и повторите попытку.",
    );
  }
  if (response.status === 401 && retry && !publicEndpoint) {
    try {
      if (token === sentToken) await refresh();
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status === 401 &&
        sentGeneration === generation
      ) {
        setToken(null);
        window.dispatchEvent(new Event("session-expired"));
      }
      throw error;
    }
    return request(path, options, kind, false);
  }
  if (!response.ok) {
    if (response.status === 401 && (path === "/auth/me" || !retry))
      window.dispatchEvent(new Event("session-expired"));
    await failure(response, path);
  }
  if (response.status === 204) return undefined as T;
  return (
    kind === "blob"
      ? response.blob()
      : kind === "text"
        ? response.text()
        : response.json()
  ) as Promise<T>;
}
export const post = <T>(path: string, body?: unknown) =>
  request<T>(path, {
    method: "POST",
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
export const query = (values: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== "") params.set(k, String(v));
  });
  return params.toString();
};
export function invitationUrl(url: string) {
  const value = new URL(url);
  const token = value.pathname.split("/").filter(Boolean).at(-1);
  return `${value.origin}/register#${encodeURIComponent(token || "")}`;
}
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
