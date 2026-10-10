import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Download,
  FileDown,
  QrCode,
  KeyRound,
  ExternalLink,
} from "lucide-react";

type Method = "file" | "qr" | "key";
type Screenshot = { src: string; alt: string };
type GuideScreenshots = Record<Method, Screenshot>;

const screenshots: GuideScreenshots = {
  file: {
    src: "/guide/file.png",
    alt: "AmneziaVPN на iOS: выбран пункт «Файл с настройками подключения»",
  },
  qr: {
    src: "/guide/qr.png",
    alt: "AmneziaVPN на iOS: выбран пункт «QR-код»",
  },
  key: {
    src: "/guide/key.png",
    alt: "AmneziaVPN на iOS: поле «Вставьте ключ» и кнопка «Вставить»",
  },
};

const methods = {
  file: {
    label: "Файл",
    icon: FileDown,
    title: "Подключение через файл",
    detail: "Удобно, если панель открыта на том устройстве, где нужен VPN.",
    steps: [
      [
        "Скачайте файл",
        "В настройках устройства нажмите «Скачать .conf файл».",
      ],
      [
        "Откройте в AmneziaVPN",
        "Нажмите «+» → «Файл с настройками подключения» и выберите файл.",
      ],
      [
        "Подключитесь",
        "Нажмите «Продолжить» → «Подключиться» и разрешите VPN.",
      ],
    ],
    source:
      "https://docs.amnezia.org/ru/documentation/instructions/connect-via-config/",
  },
  qr: {
    label: "QR-код",
    icon: QrCode,
    title: "Подключение через QR-код",
    detail:
      "Откройте QR на компьютере или другом телефоне, чтобы отсканировать его камерой.",
    steps: [
      [
        "Откройте QR-код",
        "В настройках устройства нажмите «Сканировать QR-код».",
      ],
      [
        "Отсканируйте телефоном",
        "В AmneziaVPN нажмите «+» → «QR-код» и разрешите доступ к камере.",
      ],
      [
        "Подключитесь",
        "Если кодов несколько, сканируйте по порядку. Затем «Продолжить» → «Подключиться».",
      ],
    ],
    source:
      "https://docs.amnezia.org/ru/documentation/instructions/connect-via-qr-code/",
  },
  key: {
    label: "Ключ доступа",
    icon: KeyRound,
    title: "Подключение через ключ",
    detail:
      "Удобно на одном устройстве: достаточно скопировать и вставить ключ.",
    steps: [
      [
        "Скопируйте ключ",
        "В настройках устройства нажмите «Скопировать ключ доступа».",
      ],
      [
        "Вставьте в AmneziaVPN",
        "Нажмите «+» → «Вставить». Ключ должен начинаться с vpn://.",
      ],
      [
        "Подключитесь",
        "Нажмите «Продолжить» → «Подключиться» и разрешите VPN.",
      ],
    ],
    source:
      "https://docs.amnezia.org/ru/documentation/instructions/connect-via-text-key/",
  },
} as const;

export function Guide({ images = screenshots }: { images?: GuideScreenshots }) {
  const [method, setMethod] = useState<Method>("file");
  const current = methods[method];
  return (
    <div className="guide-page">
      <div className="page-heading">
        <div>
          <h1>Как подключить VPN</h1>
          <p>
            Установите AmneziaVPN и выберите один из трёх способов подключения.
          </p>
        </div>
      </div>

      <section
        className="panel guide-install"
        aria-labelledby="guide-install-title"
      >
        <h2 id="guide-install-title">1. Установите AmneziaVPN</h2>
        <p>
          Для Android — Google Play. Для iPhone и компьютера — официальный сайт.
        </p>
        <div className="guide-downloads">
          <a
            className="button primary"
            href="https://play.google.com/store/apps/details?id=org.amnezia.vpn"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Download size={18} /> Скачать в Google Play{" "}
            <ExternalLink size={14} />
          </a>
          <a
            className="button"
            href="https://amnezia.org/ru/downloads"
            target="_blank"
            rel="noopener noreferrer"
          >
            Другие устройства <ExternalLink size={14} />
          </a>
        </div>
      </section>

      <section
        className="panel guide-connect"
        aria-labelledby="guide-connect-title"
      >
        <h2 id="guide-connect-title">2. Добавьте подключение</h2>
        <p>
          Создайте устройство в разделе{" "}
          <Link to="/my-devices">«Мои устройства»</Link> и откройте его
          настройки подключения.
        </p>
        <nav className="guide-tabs" aria-label="Способ подключения">
          {(Object.keys(methods) as Method[]).map((id) => {
            const Icon = methods[id].icon;
            return (
              <button
                key={id}
                type="button"
                className={method === id ? "active" : ""}
                aria-pressed={method === id}
                onClick={() => setMethod(id)}
              >
                <Icon size={18} /> {methods[id].label}
              </button>
            );
          })}
        </nav>
        <div className="guide-grid" key={method}>
          <div className="guide-steps">
            <h3>{current.title}</h3>
            <p>{current.detail}</p>
            <ol className="guide-timeline">
              {current.steps.map(([title, detail], index) => (
                <li key={title}>
                  <span className="timeline-marker" aria-hidden="true">
                    {index + 1}
                  </span>
                  <div>
                    <h4>{title}</h4>
                    <p>{detail}</p>
                  </div>
                </li>
              ))}
            </ol>
            <p className="muted">
              На скриншоте — iOS. На Android кнопки могут отличаться.
            </p>
            <a
              className="guide-source"
              href={current.source}
              target="_blank"
              rel="noopener noreferrer"
            >
              Инструкция Amnezia <ExternalLink size={14} />
            </a>
          </div>
          <figure className="guide-shot">
            <a
              href={images[method].src}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Открыть скриншот в полном размере"
            >
              <img
                src={images[method].src}
                alt={images[method].alt}
                width={1177}
                height={2560}
                loading="lazy"
              />
            </a>
            <figcaption>Нажмите на скриншот, чтобы увеличить.</figcaption>
          </figure>
        </div>
      </section>
      <p className="guide-access-note">
        Файл, QR-код и ключ дают доступ к вашему VPN. Храните их для своего
        устройства.
      </p>
    </div>
  );
}
