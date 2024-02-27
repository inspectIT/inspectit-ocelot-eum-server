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

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@ContextConfiguration(initializers = OtlpHttpTraceExporterIntTest.EnvInitializer.class)
public class OtlpHttpTraceExporterIntTest extends ExporterIntTestBaseWithOtelCollector {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.enabled=" + ExporterEnabledState.ENABLED, String.format("inspectit-eum-server.exporters.tracing.otlp.endpoint=%s/v1/traces", getEndpoint(COLLECTOR_OTLP_HTTP_PORT)), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.otlp.protocol=" + TransportProtocol.HTTP_PROTOBUF.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void verifyTraceSentHttpWithOtlp() throws Exception {
        String httpTraceId = "497d4e959f574a77d0d3abf05523ec5b";
        postSpan(httpTraceId);
        awaitSpansExported(httpTraceId);
    }

    @Test
    void verifyTraceWithArrayValueSentHttpWithOtlp() throws Exception {
        postResourceSpans();
        awaitSpansExported(RESOURCE_TRACE_ID);
    }
}
