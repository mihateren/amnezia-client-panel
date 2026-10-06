import {
  createContext,
  useContext,
  useEffect,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { ShieldCheck, ArrowRight } from "lucide-react";
import { post, refresh, request, setToken, type Schema } from "./api";
import { ErrorBox } from "./ui";
const Context = createContext<{
  user: Schema<"CurrentUser"> | null;
  login: (data: Schema<"AuthResponse">) => void;
  logout: () => Promise<void>;
}>({ user: null, login: () => {}, logout: async () => {} });
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
          Amnezia<span>Восстанавливаем сессию…</span>
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
      <section className="auth-story">
        <div className="brand">
          <ShieldCheck />
          amnezia<span>CONTROL PANEL</span>
        </div>
        <div>
          <span className="eyebrow">ВАША СЕТЬ. ВАШИ ПРАВИЛА.</span>
          <h1>
            Свобода быть
            <br />
            на связи.
          </h1>
          <p>
            Личный доступ к VPN.
            <br />
            Все устройства — под вашим контролем.
          </p>
          <div className="orbit">
            <ShieldCheck size={80} />
          </div>
        </div>
        <small>AmneziaWG · Приватное подключение</small>
      </section>
      <section className="auth-form">
        <div className="auth-card">
          <span className="eyebrow">AMNEZIA / ЛИЧНЫЙ КАБИНЕТ</span>
          <h1>{register ? "Добро пожаловать" : "С возвращением"}</h1>
          <p>
            {register
              ? "Создайте аккаунт по приглашению администратора."
              : "Войдите, чтобы управлять своей сетью."}
          </p>
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
            <ShieldCheck size={18} />
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
