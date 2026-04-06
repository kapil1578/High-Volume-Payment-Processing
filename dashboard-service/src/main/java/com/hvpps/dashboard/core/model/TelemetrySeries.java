package com.hvpps.dashboard.core.model;

import java.util.List;

public record TelemetrySeries(
        String range,
        List<TelemetryPoint> points
) {
}
