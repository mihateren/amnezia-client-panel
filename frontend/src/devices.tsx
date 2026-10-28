import { useVpnMigration } from "./vpn-migration";
import { useEffect, useRef } from "react";
import { UserPicker } from "./user-picker";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  Check,
  Pause,
  ArrowDown,
  ArrowUp,
  Plus,
  Monitor,
  Laptop,
  Smartphone,
  Tablet,
  Router,
  Tv,
  Download,
  QrCode,
  Trash2,
  Pencil,
  BookOpen,
} from "lucide-react";
import { download, post, query, request, type Schema } from "./api";
import {
  Badge,
  bytes,
  date,
  Confirm,
  ErrorBox,
  Modal,
  Pagination,
  ResourceState,
  DevicesSkeleton,
  useResource,
} from "./ui";

type DeviceIcon = "DESKTOP" | "LAPTOP" | "PHONE" | "TABLET" | "ROUTER" | "TV";
type Device = Schema<"Device"> & {
  icon?: DeviceIcon;
  owner?: Schema<"AdminUser">;
};
const icons = {
  DESKTOP: Monitor,
  LAPTOP: Laptop,
  PHONE: Smartphone,
  TABLET: Tablet,
  ROUTER: Router,
  TV: Tv,
};
const iconLabels: Record<DeviceIcon, string> = {
  DESKTOP: "Компьютер",
  LAPTOP: "Ноутбук",
  PHONE: "Телефон",
  TABLET: "Планшет",
  ROUTER: "Роутер",
  TV: "Телевизор",
};
function DeviceSymbol({ icon }: { icon?: DeviceIcon }) {
  const Icon = icons[icon || "LAPTOP"] || Laptop;
  return <Icon aria-hidden="true" />;
}
function IconPicker({
  value,
  onChange,
}: {
  value: DeviceIcon;
  onChange: (icon: DeviceIcon) => void;
}) {
  return (
    <fieldset className="icon-picker">
      <legend>Значок</legend>
      <div className="icon-options">
        {(Object.keys(icons) as DeviceIcon[]).map((icon) => (
          <button
            className="icon-choice"
            key={icon}
            type="button"
            aria-pressed={value === icon}
            onClick={() => onChange(icon)}
          >
            <DeviceSymbol icon={icon} />
            <span>{iconLabels[icon]}</span>
          </button>
        ))}
      </div>
    </fieldset>
  );
}
export function Devices({ admin = false }: { admin?: boolean }) {
  const migration = useVpnMigration();
  const servers = useResource<Schema<"PublicVpnServer">[]>(
    "/vpn-servers",
    15000,
  );
  const [serverId, setServerId] = useState("");
  const chosenServer = servers.data?.find((s) => s.id === serverId);
  const blocked = !chosenServer?.enabled;
  useEffect(() => {
    if (!serverId && servers.data?.length)
      setServerId((servers.data.find((s) => s.enabled) || servers.data[0]).id);
  }, [serverId, servers.data]);
  const [page, setPage] = useState(0);
  const [owner, setOwner] = useState("");
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<Device>();
  const [editing, setEditing] = useState<Device>();
  const [deleting, setDeleting] = useState<Device>();
  const base = admin ? "/admin/devices" : "/devices";
  const r = useResource<Schema<"AdminDevicePage">>(
    base +
      "?" +
      query({
        page,
        size: 12,
        userId: admin ? owner : undefined,
        serverId: !admin ? serverId : undefined,
      }),
  );
  const previousMigration = useRef(migration.data);
  useEffect(() => {
    const previous = previousMigration.current;
    const current = migration.data;
    previousMigration.current = current;
    if (
      previous &&
      current &&
      (previous.generation !== current.generation ||
        previous.maintenance !== current.maintenance)
    ) {
      void r.reload();
    }
  }, [migration.data, r.reload]);
  return (
    <>
      <div className="page-heading">
        <h1>{admin ? "Все устройства" : "Мои устройства"}</h1>
        <div className="device-toolbar">
          <Link className="button-link" to="/guide">
            <BookOpen size={17} />
            Как подключиться
          </Link>
          {!admin && (
            <button
              className="primary"
              disabled={blocked}
              onClick={() => setCreate(true)}
            >
              <Plus size={17} />
              Добавить устройство
            </button>
          )}
        </div>
      </div>
      {!admin && (
        <section className="server-selector" aria-label="Выбор VPN-сервера">
          <div className="server-selector-heading">
            <h2>Сервер подключения</h2>
            <p>Выберите, где создать новое подключение.</p>
          </div>
          <ResourceState
            {...servers}
            loading={!servers.data && servers.loading}
            empty={!!servers.data && !servers.data.length}
            emptyTitle="Пока нет VPN-серверов"
            emptyDescription="Администратор ещё не добавил сервер. Обратитесь к нему, чтобы подключиться."
          />
          <div className="server-options">
            {servers.data?.map((server) => (
              <button
                className="server-option"
                data-available={server.enabled}
                type="button"
                key={server.id}
                aria-pressed={serverId === server.id}
                onClick={() => {
                  setServerId(server.id);
                  setPage(0);
                }}
              >
                <span className="server-option-title">
                  <strong>{server.name}</strong>
                  <span className="server-selection-mark" aria-hidden="true">
                    {serverId === server.id && (
                      <Check size={15} strokeWidth={2.5} />
                    )}
                  </span>
                </span>
                <span className="server-option-status">
                  <span className="server-status-dot" aria-hidden="true" />
                  {server.enabled
                    ? "Доступен для подключения"
                    : "Новые подключения отключены"}
                </span>
              </button>
            ))}
          </div>
          {chosenServer && !chosenServer.enabled && (
            <div className="server-paused-notice" role="status">
              <span className="server-paused-icon" aria-hidden="true">
                <Pause size={18} />
              </span>
              <div>
                <h3>Новые подключения отключены</h3>
                <p>
                  Выберите другой сервер для нового устройства. Уже созданные
                  конфигурации остаются доступны.
                </p>
              </div>
            </div>
          )}
        </section>
      )}
      {admin && (
        <div className="filters">
          <UserPicker
            searchable
            value={owner}
            onChange={(id) => {
              setOwner(id);
              setPage(0);
            }}
          />
        </div>
      )}
      {!r.data && r.loading && <DevicesSkeleton />}
      <ResourceState
        {...r}
        loading={false}
        empty={!r.loading && !!r.data && !r.data.content.length}
        emptyTitle="Устройств пока нет"
        emptyDescription={
          admin
            ? "Выберите другого пользователя или сбросьте фильтр."
            : "Добавьте устройство, чтобы подключить VPN."
        }
      />
      <div className="device-grid">
        {r.data?.content.map((d) => (
          <article className="device-card" key={d.id}>
            <span className="device-icon">
              <DeviceSymbol icon={d.icon} />
            </span>
            <h2>
              <button className="text-button" onClick={() => setSelected(d)}>
                {d.name}
              </button>
            </h2>
            {(d.status === "OUTDATED" ||
              (!d.serverId &&
                migration.data &&
                d.generation < migration.data.generation)) && (
              <p className="outdated-label">
                <AlertTriangle size={16} aria-hidden="true" />
                Устарел после переезда
              </p>
            )}
            <p className="device-owner">
              {d.serverName || "Ранее созданное подключение"} ·{" "}
              {d.clientType === "AMNEZIA_WG" ? "AmneziaWG" : "Amnezia VPN"}
            </p>
            {admin && (
              <p className="device-owner">
                {d.owner?.internalName || d.owner?.nickname || "—"}
              </p>
            )}
            <div className="device-actions">
              <button
                className={
                  admin
                    ? "connect-button browse-button"
                    : "primary connect-button"
                }
                onClick={() => setSelected(d)}
              >
                {admin || d.status === "OUTDATED" ? "Открыть" : "Подключить"}
              </button>
              <button
                className="icon-button"
                aria-label={"Изменить " + d.name}
                onClick={() => setEditing(d)}
              >
                <Pencil size={17} />
              </button>
              <button
                className="icon-button destructive"
                disabled={!d.serverId && !!migration.data?.maintenance}
                aria-label={"Удалить " + d.name}
                onClick={() => setDeleting(d)}
              >
                <Trash2 size={17} />
              </button>
            </div>
          </article>
        ))}
      </div>
      {!!r.data?.content.length && (
        <Pagination {...r.data} onChange={setPage} />
      )}
      {create && !blocked && (
        <CreateDevice
          server={chosenServer!}
          onClose={() => setCreate(false)}
          onDone={(d) => {
            setCreate(false);
            setSelected(d);
            void r.reload();
          }}
        />
      )}
      {selected && (
        <DeviceDetails
          id={selected.id}
          admin={admin}
          onClose={() => setSelected(undefined)}
        />
      )}
      {editing && (
        <EditDevice
          device={editing}
          onClose={() => setEditing(undefined)}
          onDone={r.reload}
        />
      )}
      {deleting && (
        <Confirm
          title={"Удалить «" + deleting.name + "»?"}
          description={
            deleting.status === "OUTDATED"
              ? "Старая запись будет удалена из панели. Удалите профиль из AmneziaVPN самостоятельно."
              : "Конфигурация этого устройства перестанет работать."
          }
          action={() => request(base + "/" + deleting.id, { method: "DELETE" })}
          onClose={() => setDeleting(undefined)}
          onDone={() => {
            void r.reload();
            void migration.reload();
          }}
        />
      )}
    </>
  );
}
function CreateDevice({
  server,
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: (device: Device) => void;
  server: Schema<"PublicVpnServer">;
}) {
  const [clientType, setClientType] =
    useState<Schema<"ConnectionClientType">>();
  const [icon, setIcon] = useState<DeviceIcon>("LAPTOP");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = String(
      new FormData(event.currentTarget).get("name") || "",
    ).trim();
    if (!name || !clientType) return;
    setBusy(true);
    setError(undefined);
    try {
      onDone(
        await post<Device>("/devices", {
          name,
          icon,
          serverId: server.id,
          clientType,
        }),
      );
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Новое устройство"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <p>
        Сервер: <strong>{server.name}</strong>
      </p>
      <fieldset className="client-picker" disabled={busy}>
        <legend>В каком приложении будете подключаться?</legend>
        <button
          type="button"
          aria-pressed={clientType === "AMNEZIA_VPN"}
          onClick={() => setClientType("AMNEZIA_VPN")}
        >
          <strong>Amnezia VPN</strong>
          <span>Файл .vpn</span>
        </button>
        <button
          type="button"
          aria-pressed={clientType === "AMNEZIA_WG"}
          onClick={() => setClientType("AMNEZIA_WG")}
        >
          <strong>AmneziaWG</strong>
          <span>Файл .conf и QR-код</span>
        </button>
      </fieldset>
      {clientType && (
        <form onSubmit={submit}>
          <label>
            Название
            <input
              name="name"
              required
              maxLength={100}
              placeholder="Например, мой телефон"
              autoFocus
            />
          </label>
          <IconPicker value={icon} onChange={setIcon} />
          <ErrorBox error={error} />
          <button className="primary wide" disabled={busy}>
            {busy ? "Создаём…" : "Создать устройство"}
          </button>
        </form>
      )}
    </Modal>
  );
}
function EditDevice({
  device,
  onClose,
  onDone,
}: {
  device: Device;
  onClose: () => void;
  onDone: () => void;
}) {
  const [icon, setIcon] = useState<DeviceIcon>(device.icon || "LAPTOP");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = String(
      new FormData(event.currentTarget).get("name") || "",
    ).trim();
    if (!name) return;
    setBusy(true);
    setError(undefined);
    try {
      await request<Device>("/devices/" + device.id, {
        method: "PATCH",
        body: JSON.stringify({ name, icon }),
      });
      onDone();
      onClose();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Изменить устройство"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form onSubmit={submit}>
        <label>
          Название
          <input
            name="name"
            required
            maxLength={100}
            defaultValue={device.name}
            autoFocus
          />
        </label>
        <IconPicker value={icon} onChange={setIcon} />
        <ErrorBox error={error} />
        <div className="device-edit-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Отмена
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Сохраняем…" : "Сохранить"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
export function DeviceDetails({
  id,
  admin,
  onClose,
}: {
  id: string;
  admin: boolean;
  onClose: () => void;
}) {
  const migration = useVpnMigration();
  const r = useResource<Device>((admin ? "/admin" : "") + "/devices/" + id);
  const outdated =
    r.data?.status === "OUTDATED" ||
    !!(
      r.data &&
      !r.data.serverId &&
      migration.data &&
      r.data.generation < migration.data.generation
    );
  const unavailable =
    outdated || (!r.data?.serverId && !!migration.data?.maintenance);
  const [qr, setQr] = useState<string>();
  useEffect(
    () => () => {
      if (qr) URL.revokeObjectURL(qr);
    },
    [qr],
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(undefined);
    try {
      await action();
    } catch (error) {
      setError(error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title={r.data?.name || "Устройство"} onClose={onClose}>
      <ResourceState {...r} />
      {r.data && (
        <>
          {outdated && (
            <div className="outdated-notice">
              <p className="outdated-label">
                <AlertTriangle size={16} aria-hidden="true" />
                Устарел после переезда
              </p>
              <p>Создайте новое устройство и удалите эту запись из панели.</p>
            </div>
          )}
          {admin && (
            <div className="admin-device-details">
              {!outdated && <Badge value={r.data.status} />}
              <dl className="device-facts">
                <div>
                  <dt>Владелец</dt>
                  <dd>
                    {r.data.owner?.internalName ||
                      r.data.owner?.nickname ||
                      "—"}
                  </dd>
                </div>
                <div>
                  <dt>Тип устройства</dt>
                  <dd>{iconLabels[r.data.icon || "LAPTOP"]}</dd>
                </div>
                <div>
                  <dt>Адрес в VPN</dt>
                  <dd>{r.data.vpnIp}</dd>
                </div>
                <div>
                  <dt>Создано</dt>
                  <dd>{date(r.data.createdAt)}</dd>
                </div>
                <div>
                  <dt>Последнее подключение</dt>
                  <dd>
                    {outdated || r.data.status === "UNKNOWN"
                      ? "—"
                      : date(r.data.lastHandshakeAt)}
                  </dd>
                </div>
              </dl>
              <section
                className="device-traffic"
                aria-label="Трафик устройства"
              >
                <h3>Трафик устройства</h3>
                <div className="device-traffic-grid">
                  <div>
                    <span>
                      <ArrowDown size={16} aria-hidden="true" />
                      Получено
                    </span>
                    <strong>
                      {bytes(
                        outdated || r.data.status === "UNKNOWN"
                          ? undefined
                          : r.data.txBytes,
                      )}
                    </strong>
                  </div>
                  <div>
                    <span>
                      <ArrowUp size={16} aria-hidden="true" />
                      Отправлено
                    </span>
                    <strong>
                      {bytes(
                        outdated || r.data.status === "UNKNOWN"
                          ? undefined
                          : r.data.rxBytes,
                      )}
                    </strong>
                  </div>
                </div>
                <p>
                  {outdated
                    ? "Статистика старого сервера недоступна."
                    : r.data.status === "UNKNOWN"
                      ? "Не удалось получить статистику с VPN-сервера."
                      : "С точки зрения устройства. Счётчики могут сбрасываться при перезапуске VPN."}
                </p>
              </section>
            </div>
          )}
          {!admin && !unavailable && (
            <>
              <p>
                {r.data.serverName} ·{" "}
                {r.data.clientType === "AMNEZIA_WG"
                  ? "AmneziaWG"
                  : "Amnezia VPN"}
              </p>
              <p>
                Импортируйте файл в выбранное приложение
                {r.data.clientType === "AMNEZIA_WG"
                  ? " или отсканируйте QR-код с другого устройства"
                  : ""}
                .
              </p>
              <div className="connection-actions">
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      const file = await request<Blob>(
                        "/devices/" + id + "/config/file",
                        {},
                        "blob",
                      );
                      download(
                        file,
                        "amnezia-" +
                          id +
                          (r.data!.clientType === "AMNEZIA_WG"
                            ? ".conf"
                            : ".vpn"),
                      );
                    })
                  }
                >
                  <Download />
                  Скачать{" "}
                  {r.data.clientType === "AMNEZIA_WG" ? ".conf" : ".vpn"} файл
                </button>
                {r.data.clientType === "AMNEZIA_WG" && (
                  <button
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        const file = await request<Blob>(
                          "/devices/" + id + "/config/qr",
                          {},
                          "blob",
                        );
                        setQr(URL.createObjectURL(file));
                      })
                    }
                  >
                    <QrCode />
                    Сканировать QR-код
                  </button>
                )}
              </div>
              {qr && (
                <div className="qr-series">
                  <p>В AmneziaWG нажмите + → «Сканировать QR-код».</p>
                  <img className="qr" src={qr} alt="QR-код AmneziaWG" />
                </div>
              )}
              <Link className="connection-guide" to="/guide" onClick={onClose}>
                <BookOpen size={17} />
                Гайд со скриншотами
              </Link>
              <ErrorBox error={error} />
            </>
          )}
        </>
      )}
    </Modal>
  );
}
