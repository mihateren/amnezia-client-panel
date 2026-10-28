INSERT INTO vpn_servers(id,name,ip,ssh_port,username,password_enc,host_key,container,interface_name,subnet,vpn_port,enabled,created_at)
VALUES(:id,:name,:ip,:sshPort,:username,:passwordEncrypted,:hostKey,:container,:interfaceName,:subnet,:vpnPort,:enabled,:createdAt)
