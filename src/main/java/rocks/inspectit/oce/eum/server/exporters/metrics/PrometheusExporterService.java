package rocks.inspectit.oce.eum.server.exporters.metrics;

import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import rocks.inspectit.oce.eum.server.exporters.MetricsExporterService;
import rocks.inspectit.oce.eum.server.opentelemetry.OpenTelemetryController;

/**
 * Is enabled, if exporters.metrics.prometheus.enabled is set to {@link rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState#ENABLED ENBLED}
 * or {@link rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState IF_CONFIGURED}.
 */
@Component
@Slf4j
public class PrometheusExporterService implements MetricsExporterService {

    private PrometheusHttpServer httpServer;

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private OpenTelemetryController openTelemetryController;

    @PostConstruct
    private void doEnable() {
        val config = configuration.getExporters().getMetrics().getPrometheus();
        if (!config.getEnabled().isDisabled()) {
            try {
                String host = config.getHost();
                int port = config.getPort();
                log.info("Starting Prometheus Exporter on {}:{}", host, port);
                httpServer = PrometheusHttpServer.builder()
                        .setHost(host)
                        .setPort(port)
                        .build();

                openTelemetryController.addMetricsExporterService(this);
            } catch (Exception e) {
                log.error("Error Starting Prometheus HTTP Endpoint!", e);
            }
        }
    }

    @PreDestroy
    protected boolean doDisable() {
        if (httpServer != null) {
            log.info("Stopping Prometheus Exporter");
            httpServer.close();
        }
        return true;
    }

    @Override
    public MetricReader getNewMetricReader() {
        return httpServer;
    }
}
