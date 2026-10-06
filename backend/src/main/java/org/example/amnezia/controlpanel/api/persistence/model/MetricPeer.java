package org.example.amnezia.controlpanel.api.persistence.model;

import java.util.UUID;

public record MetricPeer(UUID id, String name, String userName, String publicKey) {}
