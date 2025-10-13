package rocks.inspectit.ocelot.eum.server.beacon.processor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@ExtendWith(MockitoExtension.class)
class CsvKeyValueExpanderBeaconProcessorTest {

    @InjectMocks
    CsvKeyValueExpanderBeaconProcessor processor;

    @Nested
    class Process {

        @Test
        void noTOtherAttribute() {
            Beacon beacon = Beacon.of(Collections.singletonMap("key", "value"));

            Beacon result = processor.process(beacon);

            assertThat(result.toMap()).containsOnly(entry("key", "value"));
        }

        @Test
        void tOtherWithoutPatternAttribute() {
            Beacon beacon = Beacon.of(Collections.singletonMap("t_other", "value"));

            Beacon result = processor.process(beacon);

            assertThat(result.toMap()).containsOnly(entry("t_other", "value"));
        }

        @Test
        void tOtherWithPatternAttribute() {
            Beacon beacon = Beacon.of(Collections.singletonMap("t_other", "value|123"));

            Beacon result = processor.process(beacon);

            assertThat(result.toMap()).containsOnly(
                    entry("t_other", "value|123"),
                    entry("t_other.value", "123"));
        }

        @Test
        void tOtherWithMultiplePatternAttributes() {
            Beacon beacon = Beacon.of(Collections.singletonMap("t_other", "value|123,another_value|321"));

            Beacon result = processor.process(beacon);

            assertThat(result.toMap()).containsOnly(
                    entry("t_other", "value|123,another_value|321"),
                    entry("t_other.value", "123"),
                    entry("t_other.another_value", "321"));
        }
    }
}
