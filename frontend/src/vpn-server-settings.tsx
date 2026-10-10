import { useRef, useState, type FormEvent } from "react";
import { request, type Schema } from "./api";
import { ErrorBox, ResourceState, useResource } from "./ui";

export function VpnServerSettings({
  maintenance,
  onBusy,
}: {
  maintenance: boolean;
  onBusy: (busy: boolean) => void;
}) {
  const r =
    useResource<Schema<"VpnServerSettingsResponse">>("/admin/vpn/server");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<unknown>();
  const key = useRef<HTMLTextAreaElement>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    onBusy(true);
    setError(undefined);
    setSaved(false);
    try {
      await request("/admin/vpn/server", {
        method: "PUT",
        body: JSON.stringify({
          ip: String(form.get("ip")).trim(),
          username: String(form.get("username")).trim(),
          sshPort: Number(form.get("sshPort")),
          vpnPort: Number(form.get("vpnPort")),
          hostKey: String(form.get("hostKey")).trim(),
          privateKey: String(form.get("privateKey")),
        }),
      });
      if (key.current) key.current.value = "";
      setSaved(true);
      await r.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
      onBusy(false);
    }
  }
  return (
    <section className="server-settings-dialog">
      <p>
        Сначала начните работы. Затем сохраните адрес и SSH-доступ к уже
        настроенному серверу AmneziaWG. Перезапуск панели не требуется.
      </p>
      <ResourceState {...r} />
      {r.data && (
        <form
          onSubmit={submit}
          key={JSON.stringify(r.data)}
          onChange={() => setSaved(false)}
        >
          <fieldset
            disabled={!maintenance || busy}
            className="server-settings-fields"
          >
            <label>
              IP сервера
              <input
                name="ip"
                defaultValue={r.data.ip}
                required
                maxLength={45}
                autoComplete="off"
              />
            </label>
            <label>
              Пользователь SSH
              <input
                name="username"
                defaultValue={r.data.username}
                required
                maxLength={64}
                autoComplete="off"
              />
            </label>
            <label>
              Порт SSH
              <input
                name="sshPort"
                type="number"
                min={1}
                max={65535}
                defaultValue={r.data.sshPort}
                required
              />
            </label>
            <label>
              Порт VPN (UDP)
              <input
                name="vpnPort"
                type="number"
                min={1}
                max={65535}
                defaultValue={r.data.vpnPort}
                required
              />
            </label>
            <label>
              Приватный SSH-ключ
              <textarea
                ref={key}
                name="privateKey"
                rows={5}
                maxLength={32768}
                autoComplete="off"
                spellCheck={false}
                placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
              />
            </label>
            <p>
              Для нового IP вставьте ключ. Для прежнего IP пустое поле сохраняет
              текущий ключ. Сохранённый приватный ключ не отображается.
            </p>
            <label>
              Публичный ключ SSH-сервера
              <textarea
                name="hostKey"
                rows={3}
                maxLength={8192}
                defaultValue={r.data.hostKey || ""}
                spellCheck={false}
                placeholder="ssh-ed25519 AAAA…"
              />
            </label>
            <p>
              Публичный ключ сервера нужен для проверки его подлинности.
              Возьмите его через доверенную консоль провайдера (например, из
              /etc/ssh/ssh_host_ed25519_key.pub); вставьте тип и ключ без
              комментария.
            </p>
            <p>
              Контейнер, интерфейс, пути файлов и подсеть AmneziaWG используются
              из текущих настроек панели. На новом сервере они должны
              соответствовать этим настройкам.
            </p>
            <button className="primary" disabled={busy}>
              {busy ? "Сохраняем…" : "Сохранить сервер"}
            </button>
          </fieldset>
          <ErrorBox error={error} />
          {saved && (
            <p role="status">
              Настройки сохранены. Нажмите «Закончить работы» для проверки
              сервера.
            </p>
          )}
        </form>
      )}
    </section>
  );
}
