import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { ApiError, request } from "./api";
import {
  X,
  AlertCircle,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
export const date = (v?: string | null) =>
  v
    ? new Date(v).toLocaleString("ru-RU", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "Ещё не подключалось";
export function bytes(value?: number) {
  if (value === undefined) return "—";
  if (!value) return "0 Б";
  const i = Math.min(4, Math.floor(Math.log(value) / Math.log(1024)));
  return `${(value / 1024 ** i).toLocaleString("ru-RU", { maximumFractionDigits: 1 })} ${["Б", "КБ", "МБ", "ГБ", "ТБ"][i]}`;
}
const labels: Record<string, string> = {
  ONLINE: "В сети",
  OFFLINE: "Не в сети",
  UNKNOWN: "Неизвестно",
  ACTIVE: "Активен",
  DISABLED: "Отключён",
  UP: "Работает",
  DOWN: "Недоступен",
  SUCCESS: "Успешно",
  FAILURE: "Ошибка",
};
export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`badge ${["ONLINE", "ACTIVE", "UP", "SUCCESS"].includes(value) ? "good" : ["DOWN", "FAILURE", "DISABLED"].includes(value) ? "bad" : "neutral"}`}
    >
      <i />
      {labels[value] || value}
    </span>
  );
}
export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="error" role="alert">
      <AlertCircle size={18} />
      <div>
        {error instanceof Error ? error.message : "Произошла ошибка"}
        {error instanceof ApiError && error.correlationId && (
          <small>Код обращения: {error.correlationId}</small>
        )}
      </div>
    </div>
  );
}
export function useResource<T>(path: string, interval = 0) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const revision = useRef(0);
  const load = useCallback(async () => {
    const id = ++revision.current;
    setLoading(true);
    try {
      const value = await request<T>(path);
      if (id === revision.current) {
        setData(value);
        setError(undefined);
      }
    } catch (e) {
      if (id === revision.current) setError(e);
    } finally {
      if (id === revision.current) setLoading(false);
    }
  }, [path]);
  useEffect(() => {
    setData(undefined);
    void load();
    const timer = interval
      ? setInterval(() => {
          if (!document.hidden) void load();
        }, interval)
      : undefined;
    return () => {
      revision.current++;
      clearInterval(timer);
    };
  }, [load, interval]);
  return { data, error, loading, reload: load };
}
export function ResourceState({
  loading,
  error,
  empty,
  reload,
}: {
  loading: boolean;
  error: unknown;
  empty?: boolean;
  reload: () => void;
}) {
  return (
    <>
      {loading && (
        <div className="loading" role="status">
          Загружаем данные…
        </div>
      )}
      <ErrorBox error={error} />
      {!!error && (
        <button onClick={reload}>
          <RefreshCw size={16} />
          Повторить
        </button>
      )}
      {!loading && !error && empty && (
        <div className="empty">
          <span>Пока здесь пусто</span>
          <p>Новые записи появятся здесь.</p>
        </div>
      )}
    </>
  );
}
export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="pagination">
      <span>Всего: {totalElements}</span>
      <div>
        <button
          aria-label="Предыдущая страница"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          <ChevronLeft size={16} />
        </button>
        <span>
          {page + 1} / {Math.max(1, totalPages)}
        </span>
        <button
          aria-label="Следующая страница"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => previous?.focus();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-head">
        <h2>{title}</h2>
        <button aria-label="Закрыть" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Copy({
  value,
  label = "Ссылка",
}: {
  value: string;
  label?: string;
}) {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<unknown>();
  return (
    <>
      <div className="copy">
        <input aria-label={label} readOnly value={value} />
        <button
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(value);
              setCopied(true);
            } catch (e) {
              setError(e);
            }
          }}
        >
          {copied ? "Скопировано" : "Копировать"}
        </button>
      </div>
      <ErrorBox error={error} />
    </>
  );
}
export function Confirm({
  title,
  description,
  action,
  onClose,
  onDone,
}: {
  title: string;
  description: string;
  action: () => Promise<unknown>;
  onClose: () => void;
  onDone: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  return (
    <Modal
      title={title}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <p>{description}</p>
      <ErrorBox error={error} />
      <div className="actions">
        <button disabled={busy} onClick={onClose}>
          Отмена
        </button>
        <button
          className="danger"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              await action();
              onDone();
              onClose();
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? "Выполняется…" : "Подтвердить"}
        </button>
      </div>
    </Modal>
  );
}
