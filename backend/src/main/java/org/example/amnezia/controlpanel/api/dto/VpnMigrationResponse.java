package org.example.amnezia.controlpanel.api.dto;

public record VpnMigrationResponse(long generation, boolean maintenance, long outdatedDevices, boolean vpnUnavailable) {}
