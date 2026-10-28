import { useState } from "react";
import { Link } from "react-router-dom";
import { ExternalLink } from "lucide-react";

export function Guide() {
  const [client, setClient] = useState<"vpn" | "wg">("vpn");
  return (
    <div className="guide-page">
      <div className="page-heading">
        <div>
          <h1>Как подключить VPN</h1>
          <p>Выберите приложение, которое указали при создании устройства.</p>
        </div>
      </div>
      <nav className="guide-tabs" aria-label="Приложение">
        <button
          aria-pressed={client === "vpn"}
          onClick={() => setClient("vpn")}
        >
          Amnezia VPN
        </button>
        <button aria-pressed={client === "wg"} onClick={() => setClient("wg")}>
          AmneziaWG
        </button>
      </nav>
      <section className="panel guide-install">
        <h2>1. Установите {client === "vpn" ? "Amnezia VPN" : "AmneziaWG"}</h2>
        <p>
          Используйте актуальную версию приложения с поддержкой AmneziaWG 3.1.
        </p>
        <div className="guide-downloads">
          <a
            className="button primary"
            href={
              client === "vpn"
                ? "https://play.google.com/store/apps/details?id=org.amnezia.vpn"
                : "https://amnezia.org/ru/downloads"
            }
            target="_blank"
            rel="noopener noreferrer"
          >
            {client === "vpn" ? "Скачать в Google Play" : "Скачать AmneziaWG"}{" "}
            <ExternalLink size={14} />
          </a>
          {client === "vpn" && (
            <a
              className="button"
              href="https://amnezia.org/ru/downloads"
              target="_blank"
              rel="noopener noreferrer"
            >
              Другие устройства <ExternalLink size={14} />
            </a>
          )}
        </div>
      </section>
      <section className="panel guide-connect">
        <h2>2. Создайте конфигурацию</h2>
        <p>
          Откройте <Link to="/my-devices">«Мои устройства»</Link>, выберите
          сервер, нажмите «Добавить устройство» и выберите{" "}
          {client === "vpn" ? "Amnezia VPN" : "AmneziaWG"}. Укажите название и
          создайте устройство.
        </p>
        <div className={client === "vpn" ? "guide-grid" : "guide-steps"}>
          <div className="guide-steps">
            <h3>3. Импортируйте файл {client === "vpn" ? ".vpn" : ".conf"}</h3>
            <ol>
              <li>
                Нажмите «Подключить» у созданного устройства и скачайте файл.
              </li>
              <li>
                {client === "vpn"
                  ? "В Amnezia VPN нажмите + → «Файл с настройками подключения» и выберите .vpn файл."
                  : "В AmneziaWG нажмите + → импорт из файла и выберите .conf файл."}
              </li>
              <li>Подтвердите добавление и включите подключение.</li>
            </ol>
            {client === "wg" && (
              <>
                <h3>Или подключитесь через QR-код</h3>
                <p>
                  Откройте устройство в панели на другом экране и нажмите
                  «Сканировать QR-код». В AmneziaWG на телефоне выберите + →
                  сканирование QR-кода и наведите камеру.
                </p>
              </>
            )}
          </div>
          {client === "vpn" && (
            <figure className="guide-shot">
              <a
                href="/guide/file.png"
                target="_blank"
                rel="noopener noreferrer"
              >
                <img
                  src="/guide/file.png"
                  alt="Amnezia VPN: файл с настройками подключения"
                  width={1177}
                  height={2560}
                  loading="lazy"
                />
              </a>
              <figcaption>
                Пример меню на iOS. На других устройствах названия кнопок могут
                отличаться.
              </figcaption>
            </figure>
          )}
        </div>
      </section>
    </div>
  );
}
