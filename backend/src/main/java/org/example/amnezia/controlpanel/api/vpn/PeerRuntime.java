package org.example.amnezia.controlpanel.api.vpn;

import java.time.Duration;
import java.time.Instant;

public record PeerRuntime(Instant lastHandshake, long rxBytes, long txBytes) {
  public boolean online(Instant now, Duration threshold) {
    return lastHandshake != null
        && !lastHandshake.isAfter(now)
        && Duration.between(lastHandshake, now).compareTo(threshold) < 0;
  }
}
