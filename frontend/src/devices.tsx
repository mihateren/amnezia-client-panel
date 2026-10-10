import { useVpnMigration } from "./vpn-migration";
import { useEffect, useRef } from "react";
import { UserPicker } from "./user-picker";
import QRCode from "qrcode";
import { amneziaLink, amneziaQrChunks } from "./amnezia";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
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
  FileKey,
  Trash2,
  Pencil,
  BookOpen,
  ChevronLeft,
  ChevronRight,
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
  const blocked = !migration.data || migration.data.maintenance;
  const [page, setPage] = useState(0);
  const [owner, setOwner] = useState("");
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<Device>();
  const [editing, setEditing] = useState<Device>();
  const [deleting, setDeleting] = useState<Device>();
  const base = admin ? "/admin/devices" : "/devices";
  const r = useResource<Schema<"AdminDevicePage">>(
    base + "?" + query({ page, size: 12, userId: admin ? owner : undefined }),
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
              (migration.data && d.generation < migration.data.generation)) && (
              <p className="outdated-label">
                <AlertTriangle size={16} aria-hidden="true" />
                Устарел после переезда
              </p>
            )}
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
                disabled={blocked}
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
      {deleting && !blocked && (
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
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: (device: Device) => void;
}) {
  const [icon, setIcon] = useState<DeviceIcon>("LAPTOP");
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
      onDone(await post<Device>("/devices", { name, icon }));
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
      migration.data &&
      r.data.generation < migration.data.generation
    );
  const unavailable = !migration.data || migration.data.maintenance || outdated;
  const [nativeQr, setNativeQr] = useState<string[]>([]);
  const [qrIndex, setQrIndex] = useState(0);
  const [copied, setCopied] = useState(false);
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
  async function accessKey() {
    const text = await request<string>(
      "/devices/" + id + "/config?format=TEXT",
      {},
      "text",
    );
    return amneziaLink(text, r.data!.name, r.data!.publicKey);
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
              <div className="connection-actions">
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      download(
                        new Blob([await accessKey()], { type: "text/plain" }),
                        "amnezia-" + id + ".conf",
                      );
                    })
                  }
                >
                  <Download />
                  Скачать .conf файл
                </button>
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      const chunks = amneziaQrChunks(await accessKey());
                      const images = await Promise.all(
                        chunks.map(async (chunk) => {
                          const svg = await QRCode.toString(chunk, {
                            type: "svg",
                            margin: 4,
                            errorCorrectionLevel: "L",
                          });
                          return (
                            "data:image/svg+xml;charset=utf-8," +
                            encodeURIComponent(svg)
                          );
                        }),
                      );
                      setNativeQr(images);
                      setQrIndex(0);
                    })
                  }
                >
                  <QrCode />
                  Сканировать QR-код
                </button>
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      await navigator.clipboard.writeText(await accessKey());
                      setCopied(true);
                    })
                  }
                >
                  <FileKey />
                  Скопировать ключ доступа
                </button>
              </div>
              {copied && <p role="status">Ключ скопирован</p>}
              {nativeQr.length > 0 && (
                <div className="qr-series">
                  <p>
                    {nativeQr.length > 1
                      ? "Просканируйте все коды по порядку в AmneziaVPN."
                      : "Откройте сканер в AmneziaVPN на другом устройстве."}
                  </p>
                  <img
                    className="qr"
                    src={nativeQr[qrIndex]}
                    alt={
                      "QR-код AmneziaVPN " +
                      (qrIndex + 1) +
                      " из " +
                      nativeQr.length
                    }
                  />
                  {nativeQr.length > 1 && (
                    <div className="qr-navigation">
                      <button
                        aria-label="Предыдущий QR-код"
                        disabled={qrIndex === 0}
                        onClick={() => setQrIndex(qrIndex - 1)}
                      >
                        <ChevronLeft size={18} />
                      </button>
                      <span>
                        Код {qrIndex + 1} из {nativeQr.length}
                      </span>
                      <button
                        aria-label="Следующий QR-код"
                        disabled={qrIndex + 1 === nativeQr.length}
                        onClick={() => setQrIndex(qrIndex + 1)}
                      >
                        <ChevronRight size={18} />
                      </button>
                    </div>
                  )}
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
