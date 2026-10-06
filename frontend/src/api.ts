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
async function failure(response: Response): Promise<never> {
  const data = await response.json().catch(() => ({}));
  throw new ApiError(
    response.status,
    data.code || "HTTP_ERROR",
    data.message || "Не удалось выполнить запрос",
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
  const sentToken = token;
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      ...options,
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
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
  if (
    response.status === 401 &&
    retry &&
    (!path.startsWith("/auth/") || path === "/auth/logout") &&
    !path.startsWith("/invitations/")
  ) {
    try {
      if (token === sentToken) await refresh();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
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
    await failure(response);
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
