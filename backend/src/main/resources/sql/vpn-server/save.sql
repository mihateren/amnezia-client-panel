INSERT INTO vpn_server_settings(id, ip, ssh_port, username, private_key_enc, host_key, vpn_port)
VALUES (1, :ip, :sshPort, :username, :privateKeyEncrypted, :hostKey, :vpnPort)
ON CONFLICT(id) DO UPDATE SET ip=:ip, ssh_port=:sshPort, username=:username,
 private_key_enc=:privateKeyEncrypted, host_key=:hostKey, vpn_port=:vpnPort
