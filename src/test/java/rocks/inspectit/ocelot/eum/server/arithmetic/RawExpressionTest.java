package rocks.inspectit.ocelot.eum.server.arithmetic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RawExpressionTest {

    @Nested
    class Constructor {

        @Test
        void withoutFields() {
            RawExpression expression = new RawExpression("2");

            assertThat(expression.getFields()).isEmpty();
            assertThat(expression.isSelectionExpression()).isFalse();
        }

        @Test
        void withReference() {
            RawExpression expression = new RawExpression("{field}");

            assertThat(expression.getFields()).containsExactly("field");
            assertThat(expression.isSelectionExpression()).isTrue();
        }

        @Test
        void withReferences() {
            RawExpression expression = new RawExpression("{field} - {field.second}");

            assertThat(expression.getFields()).containsExactly("field", "field.second");
            assertThat(expression.isSelectionExpression()).isFalse();
        }

        @Test
        void withSameReferences() {
            RawExpression expression = new RawExpression("{field} - {field}");

            assertThat(expression.getFields()).containsExactly("field");
            assertThat(expression.isSelectionExpression()).isFalse();
        }
    }

    @Nested
    class IsSolvable {

        @Test
        void notSolvable() {
            Beacon beacon = Beacon.of(Collections.singletonMap("field", "5"));
            RawExpression expression = new RawExpression("{field} - {field.second}");

            boolean result = expression.isSolvable(beacon);

            assertThat(result).isFalse();
        }

        @Test
        void isSolvable() {
            Map<String, String> map = new HashMap<>();
            map.put("field", "5");
            map.put("field.second", "10");
            Beacon beacon = Beacon.of(map);
            RawExpression expression = new RawExpression("{field} - {field.second}");

            boolean result = expression.isSolvable(beacon);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class Solve {

        @Test
        void calculation() {
            Map<String, String> map = new HashMap<>();
            map.put("field", "5");
            map.put("field.second", "10");
            Beacon beacon = Beacon.of(map);
            RawExpression expression = new RawExpression("({field.second} - {field}) * 2");

            Number result = expression.solve(beacon);

            assertThat(result).isEqualTo(10D);
        }

        @Test
        void directReference() {
            Map<String, String> map = new HashMap<>();
            map.put("field", "5");
            map.put("field.second", "10");
            Beacon beacon = Beacon.of(map);
            RawExpression expression = new RawExpression("{field.second}");

            Number result = expression.solve(beacon);

            assertThat(result).isEqualTo(10D);
        }

        @Test
        void missingField() {
            Map<String, String> map = new HashMap<>();
            map.put("field", "5");
            Beacon beacon = Beacon.of(map);
            RawExpression expression = new RawExpression("{field.second} - {field}");

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> expression.solve(beacon))
                    .withMessage("The given beacon does not contain the required field 'field.second'.");
        }

        @Test
        void invalidExpression() {
            Map<String, String> map = new HashMap<>();
            map.put("field", "5");
            Beacon beacon = Beacon.of(map);
            RawExpression expression = new RawExpression("5 -* {field}");

            Number result = expression.solve(beacon);

            assertThat(result).isNull();
        }
    }
}
