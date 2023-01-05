package rocks.inspectit.oce.eum.server.exporters.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.oce.eum.server.exporters.ExporterIntTestBaseWithOtelCollector;

@DirtiesContext
@ContextConfiguration(initializers = JaegerHttpExporterIntTest.EnvInitializer.class)
class JaegerHttpExporterIntTest extends ExporterIntTestBaseWithOtelCollector {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.jaeger.enabled=" + ExporterEnabledState.ENABLED, String.format("inspectit-eum-server.exporters.tracing.jaeger.endpoint=%s/api/traces", getEndpoint(COLLECTOR_JAEGER_THRIFT_HTTP_PORT)), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.jaeger.protocol=" + TransportProtocol.HTTP_THRIFT.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void verifyTraceSentHttp() throws Exception {
        String grpcTraceId = "497d4e959f574a77d0d3abf05523ec5d";
        postSpan(grpcTraceId);
        awaitSpansExported(grpcTraceId);
    }
}