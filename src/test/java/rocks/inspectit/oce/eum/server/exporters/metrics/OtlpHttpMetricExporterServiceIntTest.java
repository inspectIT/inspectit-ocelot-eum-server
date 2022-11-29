package rocks.inspectit.oce.eum.server.exporters.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.oce.eum.server.exporters.ExporterIntTestBaseWithOtelCollector;

import java.util.Map;

@DirtiesContext
@ContextConfiguration(initializers = OtlpHttpMetricExporterServiceIntTest.EnvInitializer.class)
public class OtlpHttpMetricExporterServiceIntTest extends ExporterIntTestBaseWithOtelCollector {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.metrics.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.metrics.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_HTTP_PORT, OTLP_HTTP_METRICS_PATH), "inspectit-eum-server.exporters.metrics.otlp.interval=1s", "inspectit-eum-server.exporters.metrics.otlp.protocol=" + TransportProtocol.HTTP_PROTOBUF.getConfigRepresentation(), "inspectit-eum-server.exporters.metrics.service-name=" + SERVICE_NAME)
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.self-monitoring.enabled=" + false).applyTo(applicationContext);
        }
    }

    @Test
    void verifyOtlpHttpMetrics() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        // fake beacon that we don't expect
        beacon.put(FAKE_BEACON_KEY_NAME, "1339");
        // real beacon that we expect
        beacon.put(BEACON_PAGE_READY_TIME_KEY_NAME, "1336");
        sendBeacon(beacon);
        // wait until metrics have been exported
        awaitMetricsExported(METRIC_PAGE_READY_TIME_KEY_NAME, 1336);
        assertMetric(1339, false);
    }
}
