package rocks.inspectit.oce.eum.server.exporters.configuration;

import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This Test class tests whether the annotations on {@link TraceExportersConfiguration#otlpSpanExporter()} are working as expected,
 * i.e. whether the Bean only gets created when 'otlp.enabled' is not set to DISABLED and 'otlp.endpoint' is not empty.
 */
@SpringBootTest
public class TraceExportersConfigurationOtlpTest {

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=", "inspectit-eum-server.exporters.tracing.otlp.enabled=ENABLED"})
    @Nested
    public class OtlpEndpointTest {

        @Autowired(required = false)
        OtlpGrpcSpanExporter exporter;

        @Test
        public void testBeanWasNotCreated() {
            assertThat(exporter).isNull();
        }
    }

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=localhost:1234", "inspectit-eum-server.exporters.tracing.otlp.enabled=DISABLED", "inspectit-eum-server.exporters.tracing.otlp.protocol=grpc"})
    @Nested
    public class DisabledTest {

        @Autowired(required = false)
        OtlpGrpcSpanExporter exporter;

        @Test
        public void testBeanWasNotCreated() {
            assertThat(exporter).isNull();
        }
    }

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=localhost:1234", "inspectit-eum-server.exporters.tracing.otlp.enabled=ENABLED", "inspectit-eum-server.exporters.tracing.otlp.protocol=grpc"})
    @Nested
    public class BothAvailableTest {

        @Autowired
        OtlpGrpcSpanExporter exporter;

        @Test
        public void testBeanWasCreated() {
            assertThat(exporter).isNotNull();
        }
    }

}
