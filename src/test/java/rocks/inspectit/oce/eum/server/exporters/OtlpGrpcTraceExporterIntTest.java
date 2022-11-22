package rocks.inspectit.oce.eum.server.exporters;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;

@DirtiesContext
@ContextConfiguration(initializers = OtlpGrpcTraceExporterIntTest.EnvInitializer.class)
public class OtlpGrpcTraceExporterIntTest extends ExporterIntTestBase {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_GRPC_PORT), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.otlp.protocol=" + TransportProtocol.GRPC.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void verifyTraceSentGrpc() {
        String grpcTraceId = "497d4e959f574a77d0d3abf05523ec5a";
        postSpan(grpcTraceId);
        awaitSpansExported(grpcTraceId);
    }

}
