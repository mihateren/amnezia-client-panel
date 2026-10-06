import QRCode from "qrcode";
import { amneziaLink, amneziaQrChunks } from "./amnezia";
import { useState, type FormEvent } from "react";
import {
  Plus,
  Monitor,
  Download,
  QrCode,
  FileKey,
  Trash2,
  ArrowDownLeft,
  ArrowUpRight,
} from "lucide-react";
import { download, post, query, request, type Schema } from "./api";
import {
  Badge,
  bytes,
  Confirm,
  date,
  ErrorBox,
  Modal,
  Pagination,
  ResourceState,
  useResource,
} from "./ui";
type Device = Schema<"Device"> & { owner?: Schema<"AdminUser"> };
export function Devices({ admin = false }: { admin?: boolean }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [owner, setOwner] = useState("");
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<Device>();
  const [deleting, setDeleting] = useState<Device>();
  const base = admin ? "/admin/devices" : "/devices";
  const r = useResource<Schema<"AdminDevicePage">>(
    `${base}?${query({ page, size: 12, status: admin ? status : undefined, userId: admin ? owner : undefined })}`,
    30000,
  );
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">ПОДКЛЮЧЕНИЯ</span>
          <h1>{admin ? "Все устройства" : "Мои устройства"}</h1>
          <p>
            {admin
              ? "Подключения пользователей вашей сети."
              : "Добавьте устройство и импортируйте конфигурацию в AmneziaVPN."}
          </p>
        </div>
        {!admin && (
          <button className="primary" onClick={() => setCreate(true)}>
            <Plus size={18} />
            Добавить устройство
          </button>
        )}
      </header>
      {admin && (
        <div className="filters">
          <label>
            Статус
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Все статусы</option>
              {["ONLINE", "OFFLINE", "UNKNOWN"].map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </label>
          <label>
            ID пользователя
            <input
              value={owner}
              onChange={(e) => {
                setOwner(e.target.value);
                setPage(0);
              }}
              placeholder="UUID владельца"
            />
          </label>
        </div>
      )}
      <ResourceState {...r} empty={!r.data?.content.length} />
      <div className="device-grid">
        {r.data?.content.map((d) => (
          <article className="device-card" key={d.id}>
            <div className="card-top">
              <span className="device-icon">
                <Monitor />
              </span>
              <Badge value={d.status} />
            </div>
            <h2>
              <button className="text-button" onClick={() => setSelected(d)}>
                {d.name}
              </button>
            </h2>
            <span className="mono">{d.vpnIp}</span>
            {admin && <p>Владелец: {d.owner?.nickname || "—"}</p>}
            <div className="traffic">
              <span>
                <ArrowDownLeft size={16} />
                {bytes(d.rxBytes)}
                <small>Получено сервером</small>
              </span>
              <span>
                <ArrowUpRight size={16} />
                {bytes(d.txBytes)}
                <small>Отправлено сервером</small>
              </span>
            </div>
            <div className="card-bottom">
              <button onClick={() => setSelected(d)}>
                {admin ? "Подробнее" : "Подключить"}
                <span>↗</span>
              </button>
              <button
                aria-label={`Удалить ${d.name}`}
                onClick={() => setDeleting(d)}
              >
                <Trash2 size={17} />
              </button>
            </div>
          </article>
        ))}
      </div>
      {r.data && <Pagination {...r.data} onChange={setPage} />}
      <aside className="hint">
        <Shield />{" "}
        <div>
          <strong>Как подключиться?</strong>
          <p>
            Создайте устройство, скачайте файл конфигурации или отсканируйте
            QR-код в клиенте AmneziaVPN. Для каждого устройства используйте
            отдельную конфигурацию.
          </p>
        </div>
      </aside>
      {create && (
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
      {deleting && (
        <Confirm
          title={`Удалить «${deleting.name}»?`}
          description="Устройство потеряет доступ к VPN. Его конфигурация и ссылки импорта перестанут работать."
          action={() => request(`${base}/${deleting.id}`, { method: "DELETE" })}
          onClose={() => setDeleting(undefined)}
          onDone={r.reload}
        />
      )}
    </>
  );
}
function Shield() {
  return <Monitor size={24} />;
}
function CreateDevice({
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: (d: Device) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const name = String(new FormData(e.currentTarget).get("name")).trim();
    if (!name) return;
    setBusy(true);
    try {
      onDone(await post<Device>("/devices", { name }));
    } catch (e) {
      setError(e);
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
      <p>Дайте устройству понятное имя, чтобы легко найти его позже.</p>
      <form onSubmit={submit}>
        <label>
          Название
          <input
            name="name"
            required
            maxLength={100}
            placeholder="Например, MacBook Pro"
            autoFocus
          />
        </label>
        <ErrorBox error={error} />
        <button className="primary wide" disabled={busy}>
          {busy ? "Создаём подключение…" : "Создать устройство"}
        </button>
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
  const r = useResource<Device>(`${admin ? "/admin" : ""}/devices/${id}`);
  const [nativeQr, setNativeQr] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError(undefined);
    try {
      await fn();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  const base = `/devices/${id}/config`;
  async function accessKey() {
    const text = await request<string>(`${base}?format=TEXT`, {}, "text");
    return amneziaLink(text, r.data!.name, r.data!.publicKey);
  }

  return (
    <Modal title={r.data?.name || "Устройство"} onClose={onClose}>
      <ResourceState {...r} />
      {r.data && (
        <>
          <div className="detail-status">
            <Badge value={r.data.status} />
            <code>{r.data.vpnIp}</code>
          </div>
          <dl>
            <dt>Создано</dt>
            <dd>{date(r.data.createdAt)}</dd>
            <dt>Последнее рукопожатие</dt>
            <dd>{date(r.data.lastHandshakeAt)}</dd>
            <dt>Трафик RX / TX сервера</dt>
            <dd>
              {bytes(r.data.rxBytes)} / {bytes(r.data.txBytes)}
            </dd>
            <dt>Публичный ключ</dt>
            <dd className="mono break">{r.data.publicKey}</dd>
            {r.data.owner && (
              <>
                <dt>Владелец</dt>
                <dd>{r.data.owner.nickname}</dd>
              </>
            )}
          </dl>
          {!admin && (
            <>
              <div className="connection-help">
                <h3>Как подключиться</h3>
                <p>
                  Установите AmneziaVPN с поддержкой AWG 3.1 на устройство, для
                  которого создан этот профиль. Выберите один способ добавления
                  подключения:
                </p>
                <ol>
                  <li>
                    <strong>Через файл.</strong> Нажмите «Скачать .conf файл». В
                    AmneziaVPN нажмите «+» для добавления подключения, выберите
                    импорт из файла и откройте скачанный файл из папки
                    «Загрузки».
                  </li>
                  <li>
                    <strong>Через QR.</strong> Нажмите «Сканировать QR-код»
                    здесь. На другом устройстве откройте AmneziaVPN → «+» →
                    сканирование QR и наведите камеру на код на экране.
                    Используйте сканер внутри AmneziaVPN.
                  </li>
                  <li>
                    <strong>Через ключ.</strong> Нажмите «Скопировать ключ
                    доступа». В AmneziaVPN нажмите «+», выберите добавление по
                    ключу или из буфера обмена и вставьте скопированный ключ.
                  </li>
                </ol>
                <p>
                  После импорта выберите добавленное подключение и нажмите
                  «Подключиться». На одном устройстве используйте один профиль.
                </p>
                <small>
                  Файл, QR и ключ дают доступ к вашему VPN. Передавайте их
                  только владельцу устройства.
                </small>
              </div>
              <div className="connection-actions">
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      const key = await accessKey();
                      download(
                        new Blob([key], { type: "text/plain" }),
                        `amnezia-${id}.conf`,
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
                          return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
                        }),
                      );
                      setNativeQr(images);
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
                      const key = await accessKey();
                      await navigator.clipboard.writeText(key);
                      setCopied(true);
                    })
                  }
                >
                  <FileKey />
                  Скопировать ключ доступа
                </button>
              </div>
              {copied && (
                <p role="status">
                  Ключ скопирован. Вставьте его при добавлении подключения в
                  AmneziaVPN.
                </p>
              )}
              {nativeQr.length > 0 && (
                <div className="qr-series">
                  <p>
                    Откройте сканер QR в AmneziaVPN на другом устройстве и
                    наведите камеру на изображение.
                  </p>
                  {nativeQr.length > 1 && (
                    <p>
                      Просканируйте все {nativeQr.length} кода по порядку, не
                      закрывая сканер. Подключение добавится после последнего
                      кода.
                    </p>
                  )}
                  {nativeQr.map((image, index) => (
                    <figure key={index}>
                      <img
                        className="qr"
                        src={image}
                        alt={`QR-код AmneziaVPN ${index + 1} из ${nativeQr.length}`}
                      />
                      {nativeQr.length > 1 && (
                        <figcaption>
                          Код {index + 1} из {nativeQr.length}
                        </figcaption>
                      )}
                    </figure>
                  ))}
                </div>
              )}
              <ErrorBox error={error} />
            </>
          )}
        </>
      )}
    </Modal>
  );
}
