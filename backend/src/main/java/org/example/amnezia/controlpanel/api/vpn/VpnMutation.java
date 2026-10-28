package org.example.amnezia.controlpanel.api.vpn;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Caller holds the shared application lock through remote apply AND database commit. */
@Service
public class VpnMutation {
  private final VpnGateway gateway;
  private final TransactionTemplate tx;
  private final MeterRegistry metrics;

  public VpnMutation(
      VpnGateway gateway,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx,
      MeterRegistry metrics) {
    this.gateway = gateway;
    this.tx = tx;
    this.metrics = metrics;
  }

  public void apply(String original, String replacement, Runnable persist) {
    apply(List.of(new Change(gateway, original, replacement)), persist);
  }

  public record Change(VpnGateway gateway, String original, String replacement) {
    @Override
    public String toString() {
      return "Change[redacted]";
    }
  }

  public void apply(List<Change> changes, Runnable persist) {
    var applied = new ArrayList<Change>();
    try {
      for (var change : changes) {
        change.gateway().replace(change.original(), change.replacement());
        applied.add(change);
      }
      try {
        tx.executeWithoutResult(status -> persist.run());
      } catch (RuntimeException failure) {
        metrics.counter("db_errors").increment();
        throw failure;
      }
    } catch (RuntimeException failure) {
      for (var change : applied.reversed()) {
        try {
          change.gateway().replace(change.replacement(), change.original());
        } catch (RuntimeException rollbackFailure) {
          metrics.counter("vpn_rollback_errors").increment();
          LoggerFactory.getLogger(getClass())
              .error("VPN rollback failed; operator reconciliation required");
        }
      }
      throw failure;
    }
  }
}
