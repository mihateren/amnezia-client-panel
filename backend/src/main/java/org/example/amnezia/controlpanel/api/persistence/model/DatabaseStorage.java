package org.example.amnezia.controlpanel.api.persistence.model;

import java.util.List;

public record DatabaseStorage(long allocatedBytes, List<TableStorage> tables) {
  public DatabaseStorage {
    tables = List.copyOf(tables);
  }
}
