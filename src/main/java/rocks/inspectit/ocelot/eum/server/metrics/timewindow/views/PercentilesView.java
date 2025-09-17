package rocks.inspectit.ocelot.eum.server.metrics.timewindow.views;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.common.Attributes;
import lombok.Getter;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * For the data within this window, percentiles and min / max values can be computed.
 */
public class PercentilesView extends TimeWindowView {

    /**
     * The tag to use for the percentile or "min","max" respectively.
     */
    private static final String PERCENTILE_TAG_KEY = "percentile";

    /**
     * The tag value to use for {@link #PERCENTILE_TAG_KEY} for the "minimum" series.
     */
    private static final String MIN_METRIC_SUFFIX = "_min";

    /**
     * The tag value to use for {@link #PERCENTILE_TAG_KEY} for the "maximum" series.
     */
    private static final String MAX_METRIC_SUFFIX = "_max";

    /**
     * The formatter used to print percentiles to tags.
     */
    private static final DecimalFormat PERCENTILE_TAG_FORMATTER = new DecimalFormat("#.#####", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    /**
     * The descriptor of the metric for this view, if percentile.
     */
    private MetricInfo percentileMetricInfo;

    /**
     * If not null, the minimum value will be exposed as this gauge.
     */
    private MetricInfo minMetricInfo;

    /**
     * If not null, the maximum value will be exposed as this gauge.
     */
    private MetricInfo maxMetricInfo;

    /**
     * The percentiles to compute in the range (0,1)
     */
    @Getter
    private final Set<Double> percentiles;

    /**
     * @param viewName    the prefix to use for the names of all exposed metrics
     * @param description the description of this view
     * @param unit        the unit of the measure
     * @param attributes  the attribute keys to use for this view
     * @param timeWindow  the time range to use for computing minimum / maximum and percentile values
     * @param bufferLimit the maximum number of measurements to be buffered by this view
     * @param percentiles the set of percentiles in the range (0,1) which shall be provided as metrics
     * @param includeMax  true, if the maximum value should be exposed as metric
     * @param includeMin  true, if the minimum value should be exposed as metric
     */
    public PercentilesView(String viewName, String description, String unit, Set<String> attributes, Duration timeWindow, int bufferLimit,
                           Set<Double> percentiles, boolean includeMax, boolean includeMin) {
        super(viewName, description, unit, attributes, timeWindow, bufferLimit);
        validateConfiguration(includeMin, includeMax, percentiles);

        this.percentiles = new HashSet<>(percentiles);

        if (!percentiles.isEmpty()) {
            this.percentileMetricInfo = new MetricInfo(viewName, description, unit);
        }
        if (includeMin) {
            this.minMetricInfo = new MetricInfo(viewName + MIN_METRIC_SUFFIX, description, unit);
        }
        if (includeMax) {
            this.maxMetricInfo = new MetricInfo(viewName + MAX_METRIC_SUFFIX, description, unit);
        }
    }

    private void validateConfiguration(boolean includeMin, boolean includeMax, Set<Double> percentiles) {
        percentiles.stream().filter(p -> p <= 0.0 || p >= 1.0).forEach(p -> {
            throw new IllegalArgumentException("Percentiles must be in range (0,1)");
        });
        if (percentiles.isEmpty() && !includeMin && !includeMax) {
            throw new IllegalArgumentException("You must specify at least one percentile or enable minimum or maximum computation!");
        }
    }

    boolean isMinEnabled() {
        return minMetricInfo != null;
    }

    boolean isMaxEnabled() {
        return maxMetricInfo != null;
    }

    @VisibleForTesting
    static String getPercentileTag(double percentile) {
        return PERCENTILE_TAG_FORMATTER.format(percentile);
    }

    @Override
    protected List<MetricInfo> getMetrics() {
        List<MetricInfo> metrics = new ArrayList<>();
        if (isMinEnabled()) {
            metrics.add(minMetricInfo);
        }
        if (isMaxEnabled()) {
            metrics.add(maxMetricInfo);
        }
        if (!percentiles.isEmpty()) {
            metrics.add(percentileMetricInfo);
        }
        return metrics;
    }

    @Override
    protected void computeSeries(ResultSeriesCollector resultSeries, Instant time, Attributes attributes, double[] data) {
        if (isMinEnabled() || isMaxEnabled()) {
            double minValue = Double.MAX_VALUE;
            double maxValue = -Double.MAX_VALUE;
            for (double value : data) {
                minValue = Math.min(minValue, value);
                maxValue = Math.max(maxValue, value);
            }
            if (isMinEnabled()) {
                resultSeries.add(minMetricInfo, time, attributes, minValue);
            }
            if (isMaxEnabled()) {
                resultSeries.add(maxMetricInfo, time, attributes, maxValue);
            }
        }
        if (!percentiles.isEmpty()) {
            Percentile percentileComputer = new Percentile();
            percentileComputer.setData(data);
            for (double percentile : percentiles) {
                double percentileValue = percentileComputer.evaluate(percentile * 100);
                Attributes attributesWithPercentile = attributes.toBuilder()
                        .put(PERCENTILE_TAG_KEY, getPercentileTag(percentile))
                        .build();
                resultSeries.add(percentileMetricInfo, time, attributesWithPercentile, percentileValue);
            }
        }
    }
}
