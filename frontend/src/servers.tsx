import { useState, type FormEvent } from "react";
import { Plus, Pencil, Server } from "lucide-react";
import { post, request, type Schema } from "./api";
import { Badge, ErrorBox, Modal, ResourceState, useResource } from "./ui";

type VpnServer = Schema<"ManagedVpnServer">;
export function Servers() {
  const r = useResource<VpnServer[]>("/admin/vpn-servers");
  const [editing, setEditing] = useState<VpnServer | "new">();
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<unknown>();
  async function toggle(server: VpnServer) {
    setBusy(server.id);
    setError(undefined);
    try {
      await request("/admin/vpn-servers/" + server.id, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !server.enabled }),
      });
      await r.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(undefined);
    }
  }
  return (
    <>
      <div className="page-heading">
        <h1>VPN-серверы</h1>
        <button className="primary" onClick={() => setEditing("new")}>
          <Plus size={18} />
          Добавить сервер
        </button>
      </div>
      <p>
        Добавьте сервер с установленной Amnezia. Название будет видно
        пользователям при выборе подключения.
      </p>
      <ResourceState
        {...r}
        empty={!!r.data && !r.data.length}
        emptyTitle="Серверов пока нет"
        emptyDescription="Укажите IP, логин и пароль. Настройки Amnezia определятся автоматически."
      />
      <ErrorBox error={error} />
      <div className="device-grid">
        {r.data?.map((server) => (
          <article className="device-card server-card" key={server.id}>
            <span className="device-icon">
              <Server />
            </span>
            <h2>{server.name}</h2>
            <Badge value={server.enabled ? "ACTIVE" : "DISABLED"} />
            <p>
              {server.ip}:{server.sshPort} · {server.username}
            </p>
            <p>
              {server.enabled
                ? "Пользователи могут создавать конфигурации."
                : "Новые конфигурации отключены. Существующие подключения продолжают работать."}
            </p>
            <div className="device-actions">
              <button disabled={!!busy} onClick={() => void toggle(server)}>
                {busy === server.id
                  ? "Сохраняем…"
                  : server.enabled
                    ? "Отключить новые подключения"
                    : "Разрешить новые подключения"}
              </button>
              <button
                aria-label={"Изменить " + server.name}
                onClick={() => setEditing(server)}
              >
                <Pencil size={17} />
              </button>
            </div>
          </article>
        ))}
      </div>
      {editing && (
        <ServerForm
          server={editing === "new" ? undefined : editing}
          onClose={() => setEditing(undefined)}
          onDone={() => {
            setEditing(undefined);
            void r.reload();
          }}
        />
      )}
    </>
  );
}

function ServerForm({
  server,
  onClose,
  onDone,
}: {
  server?: VpnServer;
  onClose: () => void;
  onDone: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(undefined);
    const name = String(data.get("name")).trim();
    const password = String(data.get("password") || "");
    try {
      if (server)
        await request("/admin/vpn-servers/" + server.id, {
          method: "PATCH",
          body: JSON.stringify({ name, ...(password ? { password } : {}) }),
        });
      else
        await post("/admin/vpn-servers", {
          name,
          ip: String(data.get("ip")).trim(),
          username: String(data.get("username")).trim(),
          sshPort: Number(data.get("sshPort")),
          password,
        });
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={server ? "Изменить сервер" : "Новый VPN-сервер"}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form onSubmit={submit}>
        <fieldset disabled={busy} className="server-form">
          <label>
            Название для пользователей
            <input
              name="name"
              required
              maxLength={100}
              defaultValue={server?.name}
              placeholder="Например, Рекомендуемый"
              autoFocus
            />
          </label>
          {!server && (
            <>
              <label>
                IP-адрес
                <input
                  name="ip"
                  required
                  maxLength={45}
                  placeholder="203.0.113.10"
                />
              </label>
              <label>
                Порт SSH
                <input
                  name="sshPort"
                  type="number"
                  required
                  min={1}
                  max={65535}
                  defaultValue={22}
                />
              </label>
              <label>
                Логин
                <input
                  name="username"
                  required
                  maxLength={64}
                  defaultValue="root"
                  autoComplete="off"
                />
              </label>
            </>
          )}
          <label>
            {server ? "Новый пароль сервера (необязательно)" : "Пароль сервера"}
            <input
              name="password"
              type="password"
              required={!server}
              maxLength={1024}
              autoComplete="new-password"
            />
          </label>
          {server && <p>Оставьте пароль пустым, чтобы сохранить текущий.</p>}
          <ErrorBox error={error} />
          <button className="primary wide" disabled={busy}>
            {busy
              ? "Проверяем и сохраняем…"
              : server
                ? "Сохранить"
                : "Добавить сервер"}
          </button>
        </fieldset>
      </form>
    </Modal>
  );
}
