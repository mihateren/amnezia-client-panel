package org.example.amnezia.controlpanel.api.ssh;

/** Script and its output may contain secrets: implementations must never log either. */
public interface SshExecutor {
  String execute(String script);
}
