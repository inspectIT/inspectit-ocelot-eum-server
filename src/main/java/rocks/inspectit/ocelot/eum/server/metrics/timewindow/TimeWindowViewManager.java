package rocks.inspectit.ocelot.eum.server.metrics.timewindow;

import io.opentelemetry.api.baggage.Baggage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.metrics.AttributesRegistry;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.PercentilesView;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.SmoothedAverageView;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.TimeWindowView;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.worker.TimeWindowRecorder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Allows the creation of time-window views on metrics.
 * Note that these views DO NOT coexist with OpenTelemetry {@link io.opentelemetry.sdk.metrics.View}s.
 * For this reason observations must be reported via {@link TimeWindowRecorder#recordMetric(String, double, Baggage)}
 * instead of using OpenTelemetry instruments.<br>
 * Note: The EUM-server cannot update metric definitions during runtime.
 */
@Slf4j
@Component
public class TimeWindowViewManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private AttributesRegistry attributesRegistry;

    /**
     * Maps the name of measures to registered percentile views.
     */
    private final Map<String, CopyOnWriteArrayList<TimeWindowView>> measuresToViewsMap = new ConcurrentHashMap<>();

    /**
     * @return the collection of registered time-window views for all metrics
     */
    public Collection<TimeWindowView> getAllViews() {
        return measuresToViewsMap.values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    /**
     * @param metricName the metric name
     *
     * @return the collection of registered time-window vies for the provided metric
     */
    public Collection<TimeWindowView> getViews(String metricName) {
        return measuresToViewsMap.get(metricName);
    }

    /**
     * @param metricName the name of the metric
     *
     * @return true, if any time-window view exists for the metric
     */
    public boolean areAnyViewsRegistered(String metricName) {
        return measuresToViewsMap.containsKey(metricName);
    }

    /**
     * Register the new custom time-window view in {@link #measuresToViewsMap} for the provided metric.
     *
     * @param metricName the metric name
     * @param viewName the view name
     * @param unit the metric unit
     * @param settings the (already validated) view settings
     */
    public synchronized void registerView(String metricName, String viewName, String unit, ViewDefinitionSettings settings) {
        log.debug("Registering time-window metric: {}", metricName);

        List<TimeWindowView> views = measuresToViewsMap.computeIfAbsent(metricName, (name) -> new CopyOnWriteArrayList<>());

        TimeWindowView view = switch (settings.getAggregation()) {
            case SMOOTHED_AVERAGE -> registerSmoothedAverageView(viewName, unit, settings);
            case PERCENTILES -> registerPercentilesView(viewName, unit, settings);
            default -> throw new IllegalArgumentException("Unknow time-window aggregation:" + settings.getAggregation());
        };

        views.add(view);
    }

    private TimeWindowView registerSmoothedAverageView(String viewName, String unit, ViewDefinitionSettings settings) {
        String description = settings.getDescription();
        Set<String> attributes = getAttributeKeysForView(settings);
        Duration timeWindow = settings.getTimeWindow();
        int bufferLimit = settings.getMaxBufferedPoints();
        double dropUpper = settings.getDropUpper();
        double dropLower = settings.getDropLower();

        return new SmoothedAverageView(viewName, description, unit, attributes, timeWindow, bufferLimit, dropUpper, dropLower);
    }

    private TimeWindowView registerPercentilesView(String viewName, String unit, ViewDefinitionSettings settings) {
        String description = settings.getDescription();
        Set<String> attributes = getAttributeKeysForView(settings);
        Duration timeWindow = settings.getTimeWindow();
        int bufferLimit = settings.getMaxBufferedPoints();
        Set<Double> percentiles = settings.getPercentiles();
        boolean includeMin = percentiles.contains(0.0);
        boolean includeMax = percentiles.contains(1.0);
        Set<Double> percentilesFiltered = percentiles.stream()
                .filter(p -> p > 0 && p < 1)
                .collect(Collectors.toSet());

        return new PercentilesView(viewName, description, unit, attributes, timeWindow, bufferLimit, percentilesFiltered, includeMin, includeMax);
    }

    /**
     * @return the attributes which are exposed for the given view
     */
    private Set<String> getAttributeKeysForView(ViewDefinitionSettings settings) {
        return attributesRegistry.getAttributeKeysForView(settings);
    }
}
