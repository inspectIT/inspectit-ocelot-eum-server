package rocks.inspectit.ocelot.eum.server.beacon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class BeaconTest {

    @Nested
    class Contains {

        Beacon beacon;

        @BeforeEach
        void before() {
            HashMap<String, String> map = new HashMap<>();
            map.put("first", "1");
            map.put("second", "2");
            beacon = Beacon.of(map);
        }

        @Test
        void containsField() {
            boolean result = beacon.contains("first");

            assertThat(result).isTrue();
        }

        @Test
        void merge() {
            HashMap<String, String> map = new HashMap<>();
            map.put("first", "3");
            map.put("third", "4");
            Beacon beacon1 = Beacon.of(map);
            beacon = Beacon.merge(beacon, beacon1);
            assertThat(beacon.contains(Arrays.asList("first", "second", "third"))).isTrue();
            assertThat(beacon.get("first")).isEqualTo("3");
        }

        @Test
        void containsFields() {
            boolean result = beacon.contains("first", "second");

            assertThat(result).isTrue();
        }

        @Test
        void doesNotContainField() {
            boolean result = beacon.contains("third");

            assertThat(result).isFalse();
        }

        @Test
        void doesNotContainFields() {
            boolean result = beacon.contains("first", "third");

            assertThat(result).isFalse();
        }

        @Test
        void containsFieldsAsList() {
            boolean result = beacon.contains(Arrays.asList("first", "second"));

            assertThat(result).isTrue();
        }

        @Test
        void doesNotContainFieldsAsList() {
            boolean result = beacon.contains(Arrays.asList("first", "third"));

            assertThat(result).isFalse();
        }
    }
}
