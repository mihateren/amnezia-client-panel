package org.example.amnezia.controlpanel.api.persistence.model;

public record TableStorage(String tableName, long allocatedBytes, long payloadBytes) {}
