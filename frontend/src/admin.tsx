import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  Users as UsersIcon,
  Monitor,
  ArrowUpRight,
  Plus,
  Search,
  Server,
  RefreshCw,
} from "lucide-react";
import { invitationUrl, post, query, request, type Schema } from "./api";
import {
  Badge,
  Confirm,
  Copy,
  date,
  ErrorBox,
  Modal,
  Pagination,
  ResourceState,
  DashboardSkeleton,
  EventsSkeleton,
  VpnSkeleton,
  useResource,
} from "./ui";
import { DeviceDetails } from "./devices";
import { VpnServerSettings } from "./vpn-server-settings";
import { useVpnMigration } from "./vpn-migration";
import { UserPicker } from "./user-picker";
export const events: Record<string, string> = {
  VPN_SERVER_UPDATED: "Настройки VPN-сервера изменены",
  VPN_MIGRATION_STARTED: "Переезд VPN начат",
  VPN_MIGRATION_COMPLETED: "Переезд VPN завершён",
  USER_LOGIN: "Вход пользователя",
  ADMIN_LOGIN: "Вход администратора",
  USER_LOGOUT: "Выход из системы",
  USER_NICKNAME_CHANGED: "Никнейм изменён",
  USER_PASSWORD_CHANGED: "Пароль изменён",
  INVITATION_CREATED: "Приглашение создано",
  INVITATION_USED: "Приглашение принято",
  DEVICE_CREATED: "Устройство добавлено",
  DEVICE_CONFIG_VIEWED: "Просмотр конфигурации",
  DEVICE_CONFIG_DOWNLOADED: "Скачивание конфигурации",
  DEVICE_DELETED: "Устройство удалено",
  DEVICE_UPDATED: "Устройство изменено",
  USER_CREATED: "Пользователь создан",
  USER_DISABLED: "Пользователь отключён",
  USER_DELETED: "Пользователь удалён",
};
export function Dashboard() {
  const r = useResource<Schema<"DashboardResponse">>("/admin/dashboard", 30000);
  const summary = useResource<Schema<"DashboardSummary">>(
    "/admin/dashboard/summary",
    30000,
  );
  const recent = useResource<Schema<"AuditPage">>("/admin/audit?size=5", 30000);
  const [invite, setInvite] = useState(false);
  return (
    <>
      <header className="page-heading">
        <div>
          <h1>Обзор сети</h1>
        </div>
        <button className="primary" onClick={() => setInvite(true)}>
          <Plus size={18} />
          Пригласить пользователя
        </button>
      </header>
      <ResourceState {...summary} loading={false} />
      {!summary.data && summary.loading && <DashboardSkeleton />}
      {summary.data && (
        <section className="stats">
          {[
            [
              UsersIcon,
              "Пользователи",
              summary.data.usersTotal,
              "Всего в панели",
            ],
            [
              Monitor,
              "Устройства",
              summary.data.devicesTotal,
              "Управляются панелью",
            ],
          ].map(([Icon, label, value, caption]) => {
            const I = Icon as typeof UsersIcon;
            return (
              <article className="stat" key={String(label)}>
                <div>
                  <span>{String(label)}</span>
                  <I size={20} />
                </div>
                <strong>{String(value)}</strong>
                <small>{String(caption)}</small>
              </article>
            );
          })}
        </section>
      )}
      <section className="overview-grid">
        {!r.data && (
          <div className="panel skeleton-server">
            <ResourceState {...r} loading={false} />
            {r.loading && (
              <div role="status" aria-label="Загрузка состояния VPN">
                <div className="skeleton skeleton-label" />
                <div className="skeleton skeleton-number" />
                <div className="skeleton skeleton-row" />
              </div>
            )}
          </div>
        )}
        {r.data && (
          <article className="server-card">
            <ResourceState {...r} loading={false} />
            <div className="card-top">
              <Badge value={r.data.vpn.serverUp ? "UP" : "DOWN"} />
            </div>
            <div className="server-art">
              <Server size={58} />
              <span className={r.data.vpn.serverUp ? "pulse" : ""} />
            </div>
            <h2>AmneziaWG</h2>

            <div className="server-numbers">
              <div>
                <strong>{r.data.vpn.peersTotal}</strong>
                <small>Пиров на сервере</small>
              </div>
              <div>
                <strong>{r.data.vpn.peersActive}</strong>
                <small>Активных пиров</small>
              </div>
            </div>
            <footer>Проверено: {date(r.data.vpn.checkedAt)}</footer>
          </article>
        )}
        <article className="panel">
          <div className="section-heading">
            <h2>Инфраструктура</h2>
            <span className="subtle">Обновление каждые 30 сек.</span>
          </div>
          {Object.entries({
            application: "Приложение",
            database: "База данных",
            ssh: "SSH-подключение",
            vpn: "VPN-сервис",
          }).map(([key, label]) => (
            <div className="infra-row" key={key}>
              <span className="infra-icon">
                <Server size={18} />
              </span>
              <div>
                <strong>{label}</strong>
                <small>
                  {
                    (
                      {
                        application: "API панели управления",
                        database: "Хранилище пользователей и устройств",
                        ssh: "Соединение с VPN-сервером",
                        vpn: "Состояние AmneziaWG",
                      } as Record<string, string>
                    )[key]
                  }
                </small>
              </div>
              {(
                key === "application" || key === "database"
                  ? !summary.data && summary.loading
                  : !r.data && r.loading
              ) ? (
                <span
                  className="skeleton skeleton-infra-status"
                  aria-label={"Загрузка: " + label}
                />
              ) : (
                <Badge
                  value={
                    key === "application" || key === "database"
                      ? summary.data
                        ? "UP"
                        : "UNKNOWN"
                      : r.data?.infrastructure?.[
                          key as keyof Schema<"InfrastructureStatus">
                        ] || "UNKNOWN"
                  }
                />
              )}
            </div>
          ))}
          <div className="panel-note">
            Сервер может содержать устройства, созданные вне панели. Они
            учитываются в числе пиров.
          </div>
        </article>
      </section>
      <section className="panel">
        <div className="section-heading">
          <h2>Последние события</h2>
          <Link to="/audit">
            Весь журнал <ArrowUpRight size={15} />
          </Link>
        </div>
        {!recent.data && recent.loading && <EventsSkeleton />}
        <ResourceState
          {...recent}
          loading={false}
          empty={
            !recent.loading && !recent.error && !recent.data?.content.length
          }
        />
        {!!recent.data?.content.length && (
          <AuditTable rows={recent.data.content} />
        )}
      </section>
      {invite && <Invitation onClose={() => setInvite(false)} />}
    </>
  );
}
function Invitation({ onClose }: { onClose: () => void }) {
  const [link, setLink] = useState("");
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const name = String(new FormData(e.currentTarget).get("name")).trim();
    if (!name) return;
    setBusy(true);
    try {
      const data = await post<Schema<"InvitationResponse">>(
        "/admin/invitations",
        { internalName: name },
      );
      setLink(invitationUrl(data.invitationUrl));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Пригласить пользователя"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      {link ? (
        <>
          <p>
            Передайте эту ссылку пользователю. После регистрации она перестанет
            работать.
          </p>
          <Copy value={link} />
        </>
      ) : (
        <form onSubmit={submit}>
          <label>
            Имя для администратора
            <input
              name="name"
              maxLength={100}
              required
              placeholder="Например, Александр"
              autoFocus
            />
          </label>

          <ErrorBox error={error} />
          <button className="primary wide" disabled={busy}>
            {busy ? "Создаём…" : "Создать приглашение"}
          </button>
        </form>
      )}
    </Modal>
  );
}
export function Users() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [invite, setInvite] = useState(false);
  const [selected, setSelected] = useState<string>();
  const r = useResource<Schema<"UserPage">>(
    `/admin/users?${query({ page, size: 20, search, status })}`,
  );
  return (
    <>
      <header className="page-heading">
        <div>
          <h1>Пользователи</h1>
        </div>
        <button className="primary" onClick={() => setInvite(true)}>
          <Plus size={18} />
          Пригласить пользователя
        </button>
      </header>
      <section className="panel">
        <div className="filters">
          <label className="search">
            <Search size={18} />
            <input
              aria-label="Поиск пользователей"
              placeholder="Поиск по имени или никнейму"
              maxLength={100}
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <select
            aria-label="Статус пользователя"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Все статусы</option>
            <option value="ACTIVE">Активные</option>
            <option value="DISABLED">Отключённые</option>
          </select>
        </div>
        <ResourceState
          {...r}
          empty={!r.data?.content.length}
          emptyTitle="Ничего не найдено"
          emptyDescription="Попробуйте изменить условия поиска."
        />
        {!!r.data?.content.length && (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Пользователь</th>
                    <th>Роль</th>
                    <th>Статус</th>
                    <th>Устройства</th>
                    <th>Дата регистрации</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {r.data?.content.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <button
                          className="user-link"
                          onClick={() => setSelected(u.id)}
                        >
                          <span>
                            <strong>{u.nickname}</strong>
                            <small>{u.internalName}</small>
                          </span>
                        </button>
                      </td>
                      <td>
                        {u.role === "ADMIN" ? "Администратор" : "Пользователь"}
                      </td>
                      <td>
                        <Badge value={u.status} />
                      </td>
                      <td>{u.deviceCount}</td>
                      <td>{date(u.createdAt)}</td>
                      <td>
                        <button
                          aria-label={`Открыть ${u.nickname}`}
                          onClick={() => setSelected(u.id)}
                        >
                          <ArrowUpRight size={17} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {r.data && <Pagination {...r.data} onChange={setPage} />}
          </>
        )}
      </section>
      {invite && <Invitation onClose={() => setInvite(false)} />}
      {selected && (
        <UserDetails
          id={selected}
          onClose={() => setSelected(undefined)}
          onDone={r.reload}
        />
      )}
    </>
  );
}
function UserDetails({
  id,
  onClose,
  onDone,
}: {
  id: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const r = useResource<Schema<"AdminUserDetails">>(`/admin/users/${id}`);
  const [action, setAction] = useState<"delete" | "status">();
  const [device, setDevice] = useState<string>();
  return (
    <Modal title={r.data?.nickname || "Пользователь"} onClose={onClose}>
      <ResourceState {...r} />
      {r.data && (
        <>
          <Badge value={r.data.status} />
          <dl>
            <dt>Внутреннее имя</dt>
            <dd>{r.data.internalName}</dd>
            <dt>ID</dt>
            <dd className="break mono">{id}</dd>
            <dt>Регистрация</dt>
            <dd>{date(r.data.createdAt)}</dd>
            <dt>Последняя активность</dt>
            <dd>{date(r.data.lastActivityAt)}</dd>
          </dl>
          <h3>Устройства ({r.data.deviceCount})</h3>
          {r.data.devices?.map((d) => (
            <button
              className="detail-device"
              key={d.id}
              onClick={() => setDevice(d.id)}
            >
              {d.name}
            </button>
          ))}
          {!r.data.devices?.length && <p>Нет устройств.</p>}
          {r.data.role !== "ADMIN" && (
            <div className="actions">
              <button onClick={() => setAction("status")}>
                {r.data.status === "ACTIVE"
                  ? "Отключить доступ"
                  : "Включить доступ"}
              </button>
              <button className="danger" onClick={() => setAction("delete")}>
                Удалить пользователя
              </button>
            </div>
          )}
          {action && (
            <Confirm
              title={
                action === "delete"
                  ? "Удалить пользователя?"
                  : r.data.status === "ACTIVE"
                    ? "Отключить доступ?"
                    : "Включить доступ?"
              }
              description={
                action === "delete"
                  ? "Аккаунт и все его устройства будут удалены."
                  : r.data.status === "ACTIVE"
                    ? "Все устройства пользователя будут удалены, VPN-доступ и сессии отозваны."
                    : "Пользователь сможет войти снова. Удалённые устройства не восстановятся."
              }
              action={() =>
                request(`/admin/users/${id}`, {
                  method: action === "delete" ? "DELETE" : "PATCH",
                  ...(action === "status"
                    ? {
                        body: JSON.stringify({
                          status:
                            r.data!.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
                        }),
                      }
                    : {}),
                })
              }
              onClose={() => setAction(undefined)}
              onDone={() => {
                onDone();
                if (action === "delete") onClose();
                else void r.reload();
              }}
            />
          )}
          {device && (
            <DeviceDetails
              id={device}
              admin
              onClose={() => setDevice(undefined)}
            />
          )}
        </>
      )}
    </Modal>
  );
}
function AuditTable({ rows }: { rows: Schema<"AuditEvent">[] }) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Событие</th>
            <th>Пользователь</th>
            <th>Результат</th>
            <th>Время</th>
            <th>Объект / код обращения</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((e) => (
            <tr key={e.id}>
              <td>{events[e.eventType] || e.eventType}</td>
              <td>{e.actor?.nickname || e.actor?.userId || "Система"}</td>
              <td>
                <Badge value={e.result} />
              </td>
              <td>{date(e.timestamp)}</td>
              <td className="mono audit-id">
                <span>
                  {e.object?.type} {e.object?.id}
                </span>
                <small>{e.correlationId}</small>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function Audit() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const r = useResource<Schema<"AuditPage">>(
    `/admin/audit?${query({ page, size: 20, ...filters })}`,
  );
  function filter(key: string, value: string) {
    setFilters((v) => ({ ...v, [key]: value }));
    setPage(0);
  }
  return (
    <>
      <header className="page-heading">
        <div>
          <h1>Журнал событий</h1>
        </div>
        <button onClick={r.reload} disabled={r.loading}>
          <RefreshCw size={17} />
          Обновить
        </button>
      </header>
      <section className="panel">
        <div className="filters audit-filters">
          <label>
            Событие
            <select
              value={filters.eventType || ""}
              onChange={(e) => filter("eventType", e.target.value)}
            >
              <option value="">Все события</option>
              {Object.entries(events).map(([k, v]) => (
                <option value={k} key={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label>
            Результат
            <select
              value={filters.result || ""}
              onChange={(e) => filter("result", e.target.value)}
            >
              <option value="">Все результаты</option>
              <option value="SUCCESS">Успешно</option>
              <option value="FAILURE">Ошибка</option>
            </select>
          </label>
          <UserPicker
            value={filters.userId || ""}
            onChange={(id) => filter("userId", id)}
          />
          {["from", "to"].map((k) => (
            <label key={k}>
              {k === "from" ? "С даты" : "По дату"}
              <input
                type="datetime-local"
                onChange={(e) =>
                  filter(
                    k,
                    e.target.value
                      ? new Date(e.target.value).toISOString()
                      : "",
                  )
                }
              />
            </label>
          ))}
        </div>
        <ResourceState
          {...r}
          empty={!r.data?.content.length}
          emptyTitle="Ничего не найдено"
          emptyDescription="Попробуйте изменить условия поиска."
        />
        {!!r.data?.content.length && (
          <>
            <AuditTable rows={r.data.content} />
            <Pagination {...r.data} onChange={setPage} />
          </>
        )}
      </section>
    </>
  );
}
export function Vpn() {
  const migration = useVpnMigration();
  const [savingServer, setSavingServer] = useState(false);
  const [serverForm, setServerForm] = useState(false);
  const [transition, setTransition] = useState<"start" | "complete">();
  const r = useResource<Schema<"VpnState">>("/admin/vpn", 30000);
  return (
    <>
      <header className="page-heading">
        <div>
          <h1>VPN-сервер</h1>
        </div>
        <div className="device-toolbar">
          <button
            className="primary"
            disabled={!migration.data || savingServer}
            onClick={() =>
              setTransition(migration.data?.maintenance ? "complete" : "start")
            }
          >
            {migration.data?.maintenance ? "Закончить работы" : "Начать работы"}
          </button>
          <button
            onClick={() => setServerForm(true)}
            disabled={!migration.data}
          >
            <Plus size={18} />
            Добавить новый сервер
          </button>
          <button
            onClick={r.reload}
            disabled={r.loading}
            aria-label="Обновить состояние VPN"
          >
            <RefreshCw size={17} />
          </button>
        </div>
      </header>
      {serverForm && (
        <Modal
          title="Добавить новый сервер"
          onClose={() => {
            if (!savingServer) setServerForm(false);
          }}
        >
          <VpnServerSettings
            maintenance={!!migration.data?.maintenance}
            onBusy={setSavingServer}
          />
        </Modal>
      )}
      {transition && migration.data && (
        <Confirm
          title={
            transition === "start"
              ? "Начать работы VPN?"
              : "Закончить работы VPN?"
          }
          description={
            transition === "start"
              ? "Создание, удаление и выдача конфигураций будут приостановлены до завершения работ. Затем нажмите «Добавить новый сервер» и сохраните IP и SSH-доступ к настроенному AmneziaWG. После этого закончите работы. Старые конфигурации устареют только при смене IP. Режим обслуживания ограничивает операции в панели; старый VPN-сервер нужно остановить самостоятельно."
              : "Панель проверит сохранённое подключение к AmneziaWG. Если IP изменился с начала работ, старые конфигурации станут устаревшими. Если IP прежний, конфигурации сохранятся."
          }
          action={() =>
            post("/admin/vpn/migration/" + transition, {
              generation: migration.data!.generation,
            })
          }
          onClose={() => setTransition(undefined)}
          onDone={() => {
            void migration.reload();
            void r.reload();
          }}
        />
      )}
      <ResourceState {...r} loading={false} />
      {!r.data && r.loading && <VpnSkeleton />}
      {r.data && (
        <section className="panel vpn-detail">
          <Server size={48} />
          <h2>AmneziaWG</h2>
          <Badge value={r.data.serverUp ? "UP" : "DOWN"} />
          <dl>
            <dt>Всего пиров</dt>
            <dd>{r.data.peersTotal}</dd>
            <dt>Активные пиры</dt>
            <dd>{r.data.peersActive}</dd>
            <dt>Последняя проверка</dt>
            <dd>{date(r.data.checkedAt)}</dd>
          </dl>
        </section>
      )}
    </>
  );
}
