package org.example.amnezia.controlpanel.api.dto;

import java.time.Instant;

/** Error representation required by the existing OpenAPI contract. */
public record ErrorResponse(String code, String message, Instant timestamp, String correlationId) {}
