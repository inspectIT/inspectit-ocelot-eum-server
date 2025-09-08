package rocks.inspectit.ocelot.eum.server.exporters.metrics;

import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;

import jakarta.annotation.PreDestroy;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState;

/**
 * Is enabled, if exporters.metrics.prometheus.enabled is set to {@link ExporterEnabledState#ENABLED ENBLED}
 * or {@link ExporterEnabledState IF_CONFIGURED}.
 */
@Component
@Slf4j
public class PrometheusExporterService {

    private PrometheusHttpServer httpServer;

    @Autowired
    private EumServerConfiguration configuration;

    // TODO I'm not happy with this style
    @Bean
    @ConditionalOnProperty({"inspectit-eum-server.exporters.metrics.prometheus.enabled", "inspectit-eum-server.exporters.metrics.prometheus.endpoint"})
    @ConditionalOnExpression("(NOT new String('${inspectit-eum-server.exporters.metrics.prometheus.enabled}').toUpperCase().equals(T(rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState).DISABLED.toString()))")
    MetricReader prometheusService() {
        val config = configuration.getExporters().getMetrics().getPrometheus();
        try {
            String host = config.getHost();
            int port = config.getPort();
            log.info("Starting Prometheus Exporter on {}:{}", host, port);
            httpServer = PrometheusHttpServer.builder()
                    .setHost(host)
                    .setPort(port)
                    .build();

        } catch (Exception e) {
            log.error("Error Starting Prometheus HTTP Endpoint!", e);
        }
        return httpServer;
    }

    @PreDestroy
    protected boolean doDisable() {
        if (httpServer != null) {
            log.info("Stopping Prometheus Exporter");
            httpServer.close();
        }
        return true;
    }
}
