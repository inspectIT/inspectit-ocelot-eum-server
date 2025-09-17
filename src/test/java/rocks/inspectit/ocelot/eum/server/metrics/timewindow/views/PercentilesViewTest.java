package rocks.inspectit.ocelot.eum.server.metrics.timewindow.views;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static rocks.inspectit.ocelot.eum.server.metrics.timewindow.utils.TimeWindowTestUtils.assertContainsData;

class PercentilesViewTest {

    final String name = "name";

    final String desc = "description";

    final String unit = "unit";

    @Nested
    class Constructor {

        @Test
        void noPercentilesAndMinMaxSpecified() {
            assertThatThrownBy(() -> new PercentilesView(name, desc, unit, Collections.emptySet(),
                    Duration.ofSeconds(1), 1000, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void invalidPercentile() {
            assertThatThrownBy(() -> new PercentilesView(name, desc, unit, Collections.emptySet(),
                    Duration.ofSeconds(1), 1000, Set.of(1.0), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankName() {
            assertThatThrownBy(() -> new PercentilesView(" ", desc, unit, Collections.emptySet(),
                    Duration.ofSeconds(1), 1000, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankDescription() {
            assertThatThrownBy(() -> new PercentilesView(name, " ", unit, Collections.emptySet(),
                    Duration.ofSeconds(1), 1000, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankUnit() {
            assertThatThrownBy(() -> new PercentilesView(name, desc, " ", Collections.emptySet(),
                    Duration.ofSeconds(1), 1000, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void invalidTimeWindow() {
            assertThatThrownBy(() -> new PercentilesView(name, desc, unit, Collections.emptySet(),
                    Duration.ZERO, 1000, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void invalidBufferSize() {
            assertThatThrownBy(() -> new PercentilesView(name, desc, " ", Collections.emptySet(),
                    Duration.ofSeconds(1), 0, Collections.emptySet(), false, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

    }

    @Nested
    class GetPercentileTag {

        @Test
        void unnecessaryZeroesOmitted() {
            String tag = PercentilesView.getPercentileTag(0.50);
            assertThat(tag).isEqualTo("0.5");
        }

        @Test
        void tooLongValueRoundedDown() {
            String tag = PercentilesView.getPercentileTag(1.0 / 3);
            assertThat(tag).isEqualTo("0.33333");
        }

        @Test
        void tooLongValueRoundedUp() {
            String tag = PercentilesView.getPercentileTag(1.0 / 3 * 2);
            assertThat(tag).isEqualTo("0.66667");
        }
    }

    @Nested
    class ComputeMetrics {

        @Test
        void checkPercentileMetricData() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 1, Set.of(0.5), false, false);

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertThat(result).hasSize(1);
            Optional<MetricData> maybeMetric = result.stream().findFirst();

            assertThat(maybeMetric).isNotEmpty();
            MetricData metric = maybeMetric.get();

            assertThat(metric.getName()).isEqualTo(name);
            assertThat(metric.getDescription()).isEqualTo(desc);
            assertThat(metric.getUnit()).isEqualTo(unit);
            assertThat(metric.getType()).isEqualTo(MetricDataType.DOUBLE_GAUGE);
        }

        @Test
        void checkMinMetricData() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 1, Collections.emptySet(), false, true);

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertThat(result).hasSize(1);
            Optional<MetricData> maybeMetric = result.stream().findFirst();

            assertThat(maybeMetric).isNotEmpty();
            MetricData metric = maybeMetric.get();

            assertThat(metric.getName()).isEqualTo(name + "_min");
            assertThat(metric.getDescription()).isEqualTo(desc);
            assertThat(metric.getUnit()).isEqualTo(unit);
            assertThat(metric.getType()).isEqualTo(MetricDataType.DOUBLE_GAUGE);
        }

        @Test
        void checkMaxMetricData() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 1, Collections.emptySet(), true, false);

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertThat(result).hasSize(1);
            Optional<MetricData> maybeMetric = result.stream().findFirst();

            assertThat(maybeMetric).isNotEmpty();
            MetricData metric = maybeMetric.get();

            assertThat(metric.getName()).isEqualTo(name + "_max");
            assertThat(metric.getDescription()).isEqualTo(desc);
            assertThat(metric.getUnit()).isEqualTo(unit);
            assertThat(metric.getType()).isEqualTo(MetricDataType.DOUBLE_GAUGE);
        }

        @Test
        void checkMinimumMetric() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 4, Collections.emptySet(), false, true);

            insertValues(view);

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertContainsData(result, name + "_min", 42, Map.of("my-tag", "foo"));
            assertContainsData(result, name + "_min", 100, Map.of("my-tag", "bar"));
        }

        @Test
        void checkMaximumMetric() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 4, Collections.emptySet(), true, false);

            insertValues(view);

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertContainsData(result, name + "_max", 99, Map.of("my-tag", "foo"));
            assertContainsData(result, name + "_max", 101, Map.of("my-tag", "bar"));
        }

        @Test
        void checkPercentileMetrics() {
            TimeWindowView view = new PercentilesView(name, desc, unit, Set.of("my-tag"),
                    Duration.ofMillis(10), 18, Set.of(0.5, 0.9), false, false);

            Baggage baggage1 = Baggage.builder().put("my-tag", "foo").build();
            Baggage baggage2 = Baggage.builder().put("my-tag", "bar").build();

            for (int i = 1; i < 10; i++) {
                Instant timestamp = Instant.now();
                view.insertValue(10 + i, timestamp, baggage1);
                view.insertValue(100 + i, timestamp.plus(2, ChronoUnit.MILLIS), baggage2);
            }

            Collection<MetricData> result = view.computeMetrics(Instant.now(), Resource.empty());

            assertContainsData(result, name, 19, Map.of("my-tag", "foo", "percentile", "0.9"));
            assertContainsData(result, name, 109, Map.of("my-tag", "bar", "percentile", "0.9"));
            assertContainsData(result, name, 15, Map.of("my-tag", "foo", "percentile", "0.5"));
            assertContainsData(result, name, 105, Map.of("my-tag", "bar", "percentile", "0.5"));
        }
    }

    /**
     * Helper method to insert values into the view
     */
    static void insertValues(TimeWindowView view) {
        Baggage baggage1 = Baggage.builder().put("my-tag", "foo").build();
        Baggage baggage2 = Baggage.builder().put("my-tag", "bar").build();
        Instant timestamp = Instant.now();

        view.insertValue(42, timestamp, baggage1);
        view.insertValue(99, timestamp.plus(1, ChronoUnit.MILLIS), baggage1);
        view.insertValue(101, timestamp.plus(2, ChronoUnit.MILLIS), baggage2);
        view.insertValue(100, timestamp.plus(3, ChronoUnit.MILLIS), baggage2);
    }
}
