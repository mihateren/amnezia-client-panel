import { createContext, useContext, useEffect, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { AlertTriangle, WifiOff } from "lucide-react";
import type { Schema } from "./api";
import { ErrorBox, useResource } from "./ui";

type Migration = Schema<"VpnMigrationStatus">;
const Context = createContext<{
  data?: Migration;
  reload: () => Promise<void>;
}>({
  reload: async () => {},
});
export const useVpnMigration = () => useContext(Context);

export function VpnMigrationProvider({ children }: { children: ReactNode }) {
  const r = useResource<Migration>("/vpn/migration", 10000);
  const location = useLocation();
  useEffect(() => {
    void r.reload();
  }, [location.pathname, r.reload]);
  useEffect(() => {
    const refresh = () => void r.reload();
    window.addEventListener("focus", refresh);
    return () => window.removeEventListener("focus", refresh);
  }, [r.reload]);
  return (
    <Context.Provider value={r}>
      <ErrorBox error={r.error} />
      {r.data && (r.data.maintenance || r.data.outdatedDevices > 0) && (
        <section
          className="vpn-migration-banner"
          role="status"
          aria-label="Переезд VPN"
        >
          <span className="migration-icon">
            <AlertTriangle size={24} aria-hidden="true" />
          </span>
          <div>
            <h2>
              {r.data.maintenance
                ? "Проводится перенос VPN"
                : "VPN переехал на новый сервер"}
            </h2>
            <p>
              {r.data.maintenance
                ? "Создание, удаление и скачивание конфигураций временно недоступно. Дождитесь завершения работ."
                : "Создайте новые конфигурации и удалите все старые конфигурации из панели. После проверки подключения удалите старые и неиспользуемые профили из AmneziaVPN."}
            </p>
            {r.data.outdatedDevices > 0 && (
              <strong>
                Старых конфигураций осталось: {r.data.outdatedDevices}
              </strong>
            )}
          </div>
        </section>
      )}
      {r.data?.vpnUnavailable && !r.data.maintenance && (
        <section
          className="vpn-migration-banner vpn-problem-banner"
          role="status"
          aria-label="Проблемы с VPN"
        >
          <span className="migration-icon">
            <WifiOff size={24} aria-hidden="true" />
          </span>
          <div>
            <h2>Проблемы с VPN</h2>
            <p>
              Сейчас VPN может работать нестабильно или не подключаться.
              Пожалуйста, напишите администратору.
            </p>
          </div>
        </section>
      )}
      {children}
    </Context.Provider>
  );
}
