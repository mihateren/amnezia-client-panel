import { useEffect, useRef, useState } from "react";
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
  BookOpen,
  LayoutDashboard,
  Users as UsersIcon,
  Monitor,
  ScrollText,
  Menu,
  X,
  Settings,
  ChevronRight,
  Sun,
  Moon,
} from "lucide-react";
import { AccountPage, AuthPage, AuthProvider, useAuth } from "./auth";
import { Audit, Dashboard, Users, Vpn } from "./admin";
import { Devices } from "./devices";
import { Guide } from "./guide";
import { Brand } from "./brand";
import { ThemeProvider, useTheme } from "./theme";
import "./styles.css";
import { VpnMigrationProvider } from "./vpn-migration";

function Layout() {
  const { user } = useAuth();
  const { theme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const location = useLocation();
  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);
  useEffect(() => {
    if (!open) return;
    closeButton.current?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        menuButton.current?.focus();
      }
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [open]);
  if (!user) return <Navigate to="/login" replace />;
  const admin = user.role === "ADMIN";
  const links = admin
    ? ([
        [LayoutDashboard, "Обзор сети", "/"],
        [UsersIcon, "Пользователи", "/users"],
        [Monitor, "Все устройства", "/devices"],
        [Activity, "VPN-сервер", "/vpn"],
        [ScrollText, "Журнал событий", "/audit"],
      ] as const)
    : [];
  return (
    <div className="app">
      <button
        ref={menuButton}
        className="mobile-toggle"
        aria-label="Открыть меню"
        aria-expanded={open}
        aria-controls="sidebar"
        onClick={() => setOpen(true)}
      >
        <Menu size={20} />
      </button>
      {open && (
        <button
          className="nav-backdrop"
          aria-label="Закрыть меню навигации"
          onClick={() => setOpen(false)}
        />
      )}
      <aside id="sidebar" className={`sidebar ${open ? "open" : ""}`}>
        <Link
          className="brand"
          to={admin ? "/" : "/my-devices"}
          aria-label="Amnezia Control Panel: главная"
        >
          <Brand />
        </Link>
        <button
          ref={closeButton}
          className="mobile-close"
          aria-label="Закрыть меню"
          onClick={() => {
            setOpen(false);
            menuButton.current?.focus();
          }}
        >
          <X size={20} />
        </button>
        <nav aria-label="Основная навигация">
          {links.map(([Icon, title, path]) => (
            <NavLink key={path} to={path} end>
              <Icon size={19} />
              {title}
            </NavLink>
          ))}
          <NavLink to="/my-devices">
            <Monitor size={19} />
            Мои устройства
          </NavLink>
          <NavLink to="/guide">
            <BookOpen size={19} />
            Инструкции
          </NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-theme" role="group" aria-label="Оформление">
            {(
              [
                ["light", "Светлое", Sun],
                ["dark", "Тёмное", Moon],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                type="button"
                aria-label={label}
                title={label}
                aria-pressed={theme === value}
                onClick={() => setTheme(value)}
              >
                <Icon size={18} />
              </button>
            ))}
          </div>
          <NavLink
            to="/profile"
            className="profile"
            aria-label="Открыть профиль"
          >
            <Settings size={20} />
            <div>
              <strong>{user.nickname}</strong>
              <small>{admin ? "Администратор" : "Пользователь"}</small>
            </div>
            <ChevronRight size={17} />
          </NavLink>
        </div>
      </aside>
      <div className="workspace">
        <main>
          <VpnMigrationProvider>
            <Outlet />
          </VpnMigrationProvider>
        </main>
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
  <ThemeProvider>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage register />} />
          <Route element={<Layout />}>
            <Route index element={<Home />} />
            <Route path="my-devices" element={<Devices />} />
            <Route path="profile" element={<AccountPage />} />
            <Route path="guide" element={<Guide />} />
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
    </BrowserRouter>
  </ThemeProvider>,
);
