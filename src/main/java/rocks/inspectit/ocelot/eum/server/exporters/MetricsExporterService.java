package rocks.inspectit.ocelot.eum.server.exporters;

import io.opentelemetry.sdk.metrics.export.MetricReader;

public interface MetricsExporterService {

    /**
     * @return A new {@link MetricReader}
     */
    MetricReader getNewMetricReader();
}
