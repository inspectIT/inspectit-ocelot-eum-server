package rocks.inspectit.ocelot.eum.server.exporters.tracing;

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
class TraceExportersConfigurationOtlpTest {

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=", "inspectit-eum-server.exporters.tracing.otlp.enabled=ENABLED"})
    @Nested
    class OtlpEndpointTest {

        @Autowired(required = false)
        DelegatingSpanExporter exporter;

        @Test
        void testBeanWasNotCreated() {
            assertThat(exporter).isNull();
        }
    }

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=localhost:1234", "inspectit-eum-server.exporters.tracing.otlp.enabled=DISABLED", "inspectit-eum-server.exporters.tracing.otlp.protocol=grpc"})
    @Nested
    class DisabledTest {

        @Autowired(required = false)
        DelegatingSpanExporter exporter;

        @Test
        void testBeanWasNotCreated() {
            assertThat(exporter).isNull();
        }
    }

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.tracing.otlp.endpoint=localhost:1234", "inspectit-eum-server.exporters.tracing.otlp.enabled=ENABLED", "inspectit-eum-server.exporters.tracing.otlp.protocol=grpc"})
    @Nested
    class BothAvailableTest {

        @Autowired
        DelegatingSpanExporter exporter;

        @Test
        void testBeanWasCreated() {
            assertThat(exporter).isNotNull();
        }
    }

}
