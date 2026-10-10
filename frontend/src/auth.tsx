import {
  createContext,
  useContext,
  useEffect,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { ArrowRight, LogOut } from "lucide-react";
import { Brand } from "./brand";
import { ApiError, post, refresh, request, setToken, type Schema } from "./api";
import { ErrorBox, Modal } from "./ui";
const Context = createContext<{
  user: Schema<"CurrentUser"> | null;
  login: (data: Schema<"AuthResponse">) => void;
  updateUser: (user: Schema<"CurrentUser">) => void;
  logout: () => Promise<void>;
}>({
  user: null,
  login: () => {},
  updateUser: () => {},
  logout: async () => {},
});
export const useAuth = () => useContext(Context);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Schema<"CurrentUser"> | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const expire = () => {
      setToken(null);
      setUser(null);
    };
    window.addEventListener("session-expired", expire);
    void refresh()
      .then(() => request<Schema<"CurrentUser">>("/auth/me"))
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setReady(true));
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  return (
    <Context.Provider
      value={{
        user,
        login: (data) => {
          setToken(data.accessToken);
          setUser(data.user);
        },
        updateUser: setUser,
        logout: async () => {
          await post("/auth/logout");
          setToken(null);
          setUser(null);
        },
      }}
    >
      {ready ? (
        children
      ) : (
        <div className="boot">
          <div className="brand">
            <Brand />
          </div>
          <span>Восстанавливаем сессию…</span>
        </div>
      )}
    </Context.Provider>
  );
}
export function AuthPage({ register = false }: { register?: boolean }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [token] = useState(() => {
    try {
      return decodeURIComponent(location.hash.slice(1));
    } catch {
      return "";
    }
  });
  const [valid, setValid] = useState(!register);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (register) {
      if (!token) {
        setError(
          new Error(
            "В ссылке отсутствует приглашение. Обратитесь к администратору.",
          ),
        );
        return;
      }
      void request<Schema<"InvitationValidationResponse">>(
        `/invitations/${encodeURIComponent(token)}`,
      )
        .then((v) => {
          setValid(v.valid);
          if (!v.valid) setError(new Error("Приглашение недействительно"));
        })
        .catch(setError);
    }
  }, [register, token]);
  if (auth.user && !register) return <Navigate to="/" replace />;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    const f = new FormData(e.currentTarget);
    if (register && f.get("password") !== f.get("repeat")) {
      setError(new Error("Пароли не совпадают"));
      setBusy(false);
      return;
    }
    try {
      const data = await post<Schema<"AuthResponse">>(
        register
          ? `/invitations/${encodeURIComponent(token)}/register`
          : "/auth/login",
        { nickname: f.get("nickname"), password: f.get("password") },
      );
      auth.login(data);
      navigate("/", { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <section className="auth-form">
        <div className="auth-card">
          <div className="brand">
            <Brand />
          </div>
          <h1>{register ? "Создать аккаунт" : "Вход"}</h1>
          <ErrorBox error={error} />
          <form onSubmit={submit}>
            <label>
              Никнейм
              <input
                name="nickname"
                autoComplete="username"
                minLength={3}
                maxLength={64}
                required
                autoFocus
                placeholder="Ваш никнейм"
              />
            </label>
            <label>
              Пароль
              <input
                name="password"
                type="password"
                autoComplete={register ? "new-password" : "current-password"}
                minLength={8}
                maxLength={256}
                required
                placeholder="Не менее 8 символов"
              />
            </label>
            {register && (
              <label>
                Повторите пароль
                <input
                  name="repeat"
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  maxLength={256}
                  required
                />
              </label>
            )}
            <button className="primary wide" disabled={busy || !valid}>
              {busy
                ? "Подождите…"
                : register
                  ? "Создать аккаунт"
                  : "Войти в панель"}
              <ArrowRight size={18} />
            </button>
          </form>
          <div className="auth-note">
            <span>
              {register
                ? "Приглашение можно использовать только один раз."
                : "Регистрация доступна только по приглашению. Для получения доступа обратитесь к администратору."}
            </span>
          </div>
        </div>
      </section>
    </div>
  );
}

function accountError(error: unknown) {
  if (!(error instanceof ApiError)) return error;
  const messages: Record<string, string> = {
    NICKNAME_TAKEN: "Этот никнейм уже занят. Выберите другой.",
    CURRENT_PASSWORD_INVALID:
      "Текущий пароль введён неверно. Попробуйте ещё раз.",
    VALIDATION_ERROR: "Проверьте заполненные поля и повторите попытку.",
    INTERNAL_ERROR: "Не удалось сохранить изменения. Попробуйте позже.",
  };
  const message = messages[error.code];
  return message
    ? new ApiError(error.status, error.code, message, error.correlationId)
    : error;
}

export function AccountPage() {
  const { user, logout, login, updateUser } = useAuth();
  const [nickname, setNickname] = useState(user?.nickname ?? "");
  const [editing, setEditing] = useState<"nickname" | "password" | null>(null);
  const [busy, setBusy] = useState<"nickname" | "password" | "logout" | null>(
    null,
  );
  const [nicknameError, setNicknameError] = useState<unknown>();
  const [passwordError, setPasswordError] = useState<unknown>();
  const [logoutError, setLogoutError] = useState<unknown>();
  const [nicknameSaved, setNicknameSaved] = useState(false);
  const [passwordSaved, setPasswordSaved] = useState(false);
  useEffect(() => setNickname(user?.nickname ?? ""), [user?.nickname]);

  async function saveNickname(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !user || nickname === user.nickname) return;
    setBusy("nickname");
    setNicknameError(undefined);
    setNicknameSaved(false);
    try {
      const changedUser = await request<Schema<"CurrentUser">>(
        "/auth/me/nickname",
        { method: "PATCH", body: JSON.stringify({ nickname }) },
      );
      updateUser(changedUser);
      setNicknameSaved(true);
      setEditing(null);
    } catch (error) {
      setNicknameError(accountError(error));
    } finally {
      setBusy(null);
    }
  }

  async function savePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const fields = new FormData(form);
    setPasswordError(undefined);
    setPasswordSaved(false);
    if (fields.get("newPassword") !== fields.get("repeatPassword")) {
      setPasswordError(new Error("Новые пароли не совпадают."));
      return;
    }
    setBusy("password");
    try {
      const data = await request<Schema<"AuthResponse">>("/auth/me/password", {
        method: "PATCH",
        body: JSON.stringify({
          currentPassword: fields.get("currentPassword"),
          newPassword: fields.get("newPassword"),
        }),
      });
      login(data);
      form.reset();
      setPasswordSaved(true);
      setEditing(null);
    } catch (error) {
      setPasswordError(accountError(error));
    } finally {
      setBusy(null);
    }
  }

  if (!user) return <Navigate to="/login" replace />;
  function openEditor(kind: "nickname" | "password") {
    setNickname(user!.nickname);
    setNicknameError(undefined);
    setPasswordError(undefined);
    setNicknameSaved(false);
    setPasswordSaved(false);
    setEditing(kind);
  }
  return (
    <>
      <div className="page-heading">
        <h1>Профиль</h1>
      </div>
      <section className="panel account-panel">
        <div className="account-identity">
          <span className="account-avatar" aria-hidden="true">
            {Array.from(user.nickname)[0]?.toLocaleUpperCase("ru")}
          </span>
          <div>
            <h2>{user.nickname}</h2>
            <span className="account-role">
              {user.role === "ADMIN" ? "Администратор" : "Пользователь"}
            </span>
          </div>
        </div>
        <div className="account-actions">
          <div className="account-setting-row">
            <div>
              <strong>Никнейм</strong>
              <small>{user.nickname}</small>
            </div>
            <button
              className="primary account-action-label"
              disabled={busy !== null}
              onClick={() => openEditor("nickname")}
            >
              Изменить никнейм
            </button>
          </div>
          <div className="account-setting-row">
            <div>
              <strong>Пароль</strong>
            </div>
            <button
              className="primary account-action-label"
              disabled={busy !== null}
              onClick={() => openEditor("password")}
            >
              Изменить пароль
            </button>
          </div>
        </div>
        {nicknameSaved && (
          <p className="account-success" role="status">
            Никнейм сохранён.
          </p>
        )}
        {passwordSaved && (
          <p className="account-success" role="status">
            Пароль изменён.
          </p>
        )}
        <div className="account-session">
          <ErrorBox error={logoutError} />
          <button
            className="account-logout"
            disabled={busy !== null}
            onClick={async () => {
              if (busy) return;
              setBusy("logout");
              setLogoutError(undefined);
              try {
                await logout();
              } catch (error) {
                setLogoutError(accountError(error));
              } finally {
                setBusy(null);
              }
            }}
          >
            <LogOut size={18} />
            {busy === "logout" ? "Выходим…" : "Выйти"}
          </button>
        </div>
      </section>
      {editing === "nickname" && (
        <Modal
          title="Изменить никнейм"
          onClose={() => {
            if (!busy) setEditing(null);
          }}
        >
          <p>Используется для входа. От 3 до 64 символов.</p>
          <form className="account-form" onSubmit={saveNickname}>
            <label>
              Никнейм
              <input
                name="nickname"
                autoComplete="username"
                minLength={3}
                maxLength={64}
                required
                disabled={busy !== null}
                value={nickname}
                onChange={(event) => {
                  setNickname(event.target.value);
                  setNicknameSaved(false);
                  setNicknameError(undefined);
                }}
              />
            </label>
            <ErrorBox error={nicknameError} />

            <button
              className="primary"
              disabled={busy !== null || nickname === user.nickname}
            >
              {busy === "nickname" ? "Сохраняем…" : "Сохранить никнейм"}
            </button>
          </form>
        </Modal>
      )}
      {editing === "password" && (
        <Modal
          title="Изменить пароль"
          onClose={() => {
            if (!busy) setEditing(null);
          }}
        >
          <p>Новый пароль — от 8 до 256 символов.</p>
          <form
            className="account-form"
            onSubmit={savePassword}
            onInput={() => {
              setPasswordSaved(false);
              setPasswordError(undefined);
            }}
          >
            <label>
              Текущий пароль
              <input
                name="currentPassword"
                type="password"
                autoComplete="current-password"
                minLength={8}
                maxLength={256}
                required
                disabled={busy !== null}
              />
            </label>
            <label>
              Новый пароль
              <input
                name="newPassword"
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={256}
                required
                disabled={busy !== null}
              />
            </label>
            <label>
              Повторите новый пароль
              <input
                name="repeatPassword"
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={256}
                required
                disabled={busy !== null}
              />
            </label>
            <ErrorBox error={passwordError} />

            <button className="primary" disabled={busy !== null}>
              {busy === "password" ? "Сохраняем…" : "Изменить пароль"}
            </button>
          </form>
        </Modal>
      )}
    </>
  );
}
