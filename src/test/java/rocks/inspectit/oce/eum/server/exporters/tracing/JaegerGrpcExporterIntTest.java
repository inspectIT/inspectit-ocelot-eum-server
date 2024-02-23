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
@ContextConfiguration(initializers = JaegerGrpcExporterIntTest.EnvInitializer.class)
public class JaegerGrpcExporterIntTest extends ExporterIntTestBaseWithOtelCollector {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.jaeger.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.jaeger.endpoint=" + getEndpoint(COLLECTOR_JAEGER_GRPC_PORT), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.jaeger.protocol=" + TransportProtocol.GRPC.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void verifyTraceSentGrpcWithJaeger() throws Exception {
        String grpcTraceId = "497d4e959f574a77d0d3abf05523ec5a";
        postSpan(grpcTraceId);
        awaitSpansExported(grpcTraceId);
    }

    @Test
    void verifyTraceWithArrayValueSentGrpcWithJaeger() throws Exception {
        postResourceSpans();
        awaitSpansExported(RESOURCE_TRACE_ID);
    }
}
