package rocks.inspectit.ocelot.eum.server.exporters.beacon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BeaconHttpExporterAnnotationsTest {

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.beacons.http.enabled=ENABLED"})
    @Nested
    class Enabled {

        @Autowired
        BeaconHttpExporter exporter;

        @Autowired
        ExportWorkerFactory factory;

        @Test
        void testBeanWasCreated() {
            assertThat(exporter).isNotNull();
            assertThat(factory).isNotNull();
        }
    }

    @TestPropertySource(properties = {"inspectit-eum-server.exporters.beacons.http.enabled=DISABLED"})
    @Nested
    class Disabled {

        @Autowired(required = false)
        BeaconHttpExporter exporter;

        @Autowired(required = false)
        ExportWorkerFactory factory;

        @Test
        void testBeanWasNotCreated() {
            assertThat(exporter).isNull();
            assertThat(factory).isNull();
        }
    }
}
