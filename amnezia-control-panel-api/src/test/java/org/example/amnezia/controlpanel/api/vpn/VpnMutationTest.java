package org.example.amnezia.controlpanel.api.vpn;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class VpnMutationTest {
  @Test
  void shouldCompensateVpnWhenDatabaseCommitFails() {
    var gateway = mock(VpnGateway.class);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    doThrow(new IllegalStateException("Commit failed")).when(manager).commit(any());
    var service =
        new VpnMutation(gateway, new TransactionTemplate(manager), new SimpleMeterRegistry());
    assertThatThrownBy(() -> service.apply("old", "new", () -> {})).hasMessage("Commit failed");
    var order = Mockito.inOrder(gateway, manager);
    order.verify(gateway).replace("old", "new");
    order.verify(manager).getTransaction(any());
    order.verify(manager).commit(any());
    order.verify(gateway).replace("new", "old");
  }

  @Test
  void shouldNotStartDbTransactionIfApplyFails() {
    var gateway = mock(VpnGateway.class);
    var manager = mock(PlatformTransactionManager.class);
    doThrow(new IllegalStateException("Apply failed"))
        .when(gateway)
        .replace(anyString(), anyString());
    var service =
        new VpnMutation(gateway, new TransactionTemplate(manager), new SimpleMeterRegistry());
    assertThatThrownBy(() -> service.apply("old", "new", () -> {})).hasMessage("Apply failed");
    verifyNoInteractions(manager);
  }
}
