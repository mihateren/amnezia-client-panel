import { useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import {
  Activity,
  LayoutDashboard,
  Users as UsersIcon,
  Monitor,
  ShieldCheck,
  ScrollText,
  LogOut,
  Menu,
  X,
} from "lucide-react";
import { AuthPage, AuthProvider, useAuth } from "./auth";
import { Audit, Dashboard, Users, Vpn } from "./admin";
import { Devices } from "./devices";
import { ErrorBox } from "./ui";
import "./styles.css";
function Layout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace />;
  const admin = user.role === "ADMIN";
  const links = admin
    ? [
        [LayoutDashboard, "Обзор сети", "/"],
        [UsersIcon, "Пользователи", "/users"],
        [Monitor, "Все устройства", "/devices"],
        [Activity, "VPN-сервер", "/vpn"],
        [ScrollText, "Журнал событий", "/audit"],
      ]
    : [];
  return (
    <div className="app">
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <Link className="brand" to="/">
          <ShieldCheck />
          amnezia<span>CONTROL PANEL</span>
        </Link>
        <button
          className="mobile-close"
          aria-label="Закрыть меню"
          onClick={() => setOpen(false)}
        >
          <X />
        </button>
        <span className="nav-label">
          {admin ? "УПРАВЛЕНИЕ" : "ЛИЧНЫЙ КАБИНЕТ"}
        </span>
        <nav>
          {links.map(([Icon, title, path]) => {
            const I = Icon as typeof Monitor;
            return (
              <NavLink
                key={String(path)}
                to={String(path)}
                end
                onClick={() => setOpen(false)}
              >
                <I size={19} />
                {String(title)}
              </NavLink>
            );
          })}
          <NavLink to="/my-devices" onClick={() => setOpen(false)}>
            <Monitor size={19} />
            Мои устройства
          </NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="privacy">
            <ShieldCheck size={19} />
            <div>
              Ваша приватная сеть<small>На базе AmneziaWG</small>
            </div>
          </div>
          <div className="profile">
            <span className="avatar">
              {user.nickname.slice(0, 2).toUpperCase()}
            </span>
            <div>
              <strong>{user.nickname}</strong>
              <small>{admin ? "Администратор" : "Пользователь"}</small>
            </div>
            <button
              aria-label="Выйти"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                try {
                  await logout();
                } catch (e) {
                  setError(e);
                } finally {
                  setBusy(false);
                }
              }}
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button
            className="mobile-toggle"
            aria-label="Открыть меню"
            onClick={() => setOpen(true)}
          >
            <Menu />
          </button>
          <span>
            Панель управления <span className="slash">/</span>{" "}
            <strong>
              {location.pathname === "/my-devices"
                ? "Личный кабинет"
                : admin
                  ? "Администрирование"
                  : "Личный кабинет"}
            </strong>
          </span>
          <span className="topbar-label">
            <ShieldCheck size={15} />
            AMNEZIA WG
          </span>
        </header>
        <main>
          <ErrorBox error={error} />
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>Amnezia Control Panel</span>
          <span>Ваша сеть. Ваш контроль.</span>
        </footer>
      </div>
    </div>
  );
}
function AdminOnly() {
  return useAuth().user?.role === "ADMIN" ? (
    <Outlet />
  ) : (
    <Navigate to="/my-devices" replace />
  );
}
function Home() {
  return useAuth().user?.role === "ADMIN" ? (
    <Dashboard />
  ) : (
    <Navigate to="/my-devices" replace />
  );
}
createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<AuthPage />} />
        <Route path="/register" element={<AuthPage register />} />
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="my-devices" element={<Devices />} />
          <Route element={<AdminOnly />}>
            <Route path="users" element={<Users />} />
            <Route path="devices" element={<Devices admin />} />
            <Route path="vpn" element={<Vpn />} />
            <Route path="audit" element={<Audit />} />
          </Route>
          <Route
            path="*"
            element={
              <div className="empty">
                <h1>Страница не найдена</h1>
                <Link to="/">Вернуться в панель</Link>
              </div>
            }
          />
        </Route>
      </Routes>
    </AuthProvider>
  </BrowserRouter>,
);
