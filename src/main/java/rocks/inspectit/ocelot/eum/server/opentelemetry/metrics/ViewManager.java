package rocks.inspectit.ocelot.eum.server.opentelemetry.metrics;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.metrics.*;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.AggregationType;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.metrics.InstrumentManager;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.TimeWindowViewManager;

import java.util.*;

/**
 * Stores all active OpenTelemetry metric views.
 * <br>
 * Note that if we do not register any view for an instrument (see {@link InstrumentManager}),
 * OpenTelemetry would use a default view automatically to record data.
 * However, this default view would not filter any attributes and thus would use every attribute,
 * which was passed during recording. To prevent this, we should register at least one view for every metric by ourselves.
 */
@Component
public class ViewManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private TimeWindowViewManager viewManager;

    /**
     * Registers all configured views in the provided {@link SdkMeterProviderBuilder}.
     *
     * @param builder the sdk builder to register views with
     */
    public void registerViews(SdkMeterProviderBuilder builder) {
        val metricViews = getAllActiveViewsForMetrics();

        for (val metricEntry : metricViews.entrySet()) {
            String metricName = metricEntry.getKey();
            Map<String, ViewDefinitionSettings> viewDefinitionSettings = metricEntry.getValue();

            for (Map.Entry<String, ViewDefinitionSettings> viewEntry: viewDefinitionSettings.entrySet()) {
                String viewName = viewEntry.getKey();
                ViewDefinitionSettings settings = viewEntry.getValue();

                if (settings.getAggregation().isTimeWindowAggregation()) {
                    registerTimeWindowView(metricName, viewName, settings);
                } else {
                    View view = createView(viewName, settings);
                    InstrumentSelector selector = createSelector(metricName);

                    builder.registerView(selector, view);
                }
            }
        }
    }

    /**
     * Registers the view as custom time-window view, which is handled by {@link TimeWindowViewManager} instead of
     * OpenTelemetry {@link MeterProvider}.
     *
     * @param metricName the metric name
     * @param viewName the view name
     * @param settings the view settings
     */
    private void registerTimeWindowView(String metricName, String viewName, ViewDefinitionSettings settings) {
        String unit = getUnit(metricName);

        viewManager.registerView(metricName, viewName, unit, settings);
    }

    /**
     * @return the map of all metric names and their particular active views
     */
    private Map<String, Map<String, ViewDefinitionSettings>> getAllActiveViewsForMetrics() {
        Map<String, Map<String, ViewDefinitionSettings>> metricViews = new HashMap<>();

        // check self-monitoring definitions
        configuration.getSelfMonitoring().getMetrics()
                .entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .forEach((e) -> {
                    String metricName = e.getKey();
                    MetricDefinitionSettings metricDefinition = e.getValue().getCopyWithDefaultsPopulated(metricName);

                    Map<String, ViewDefinitionSettings> activeViews = getActiveViews(metricDefinition);
                    metricViews.put(metricName, activeViews);
                });

        // check beacon metric definitions
        configuration.getDefinitions().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .forEach(e -> {
                    String metricName = e.getKey();
                    MetricDefinitionSettings metricDefinition = e.getValue().getCopyWithDefaultsPopulated(metricName);

                    Map<String, ViewDefinitionSettings> activeViews = getActiveViews(metricDefinition);
                    metricViews.put(metricName, activeViews);
                });

        return metricViews;
    }

    /**
     * @return the map of all active views for the provided metric definition
     */
    private Map<String, ViewDefinitionSettings> getActiveViews(MetricDefinitionSettings metricDefinition) {
        Map<String, ViewDefinitionSettings> activeViews = new HashMap<>();
        metricDefinition.getViews().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .forEach(e -> activeViews.put(e.getKey(), e.getValue()));
        return activeViews;
    }

    /**
     * Creates a new view from the provided settings.
     *
     * @param viewName the view name
     * @param settings the view settings
     *
     * @return the created view
     */
    private View createView(String viewName, ViewDefinitionSettings settings) {
        Aggregation aggregation = convertAggregation(settings);
        ViewBuilder builder =  View.builder()
                .setName(viewName)
                .setDescription(settings.getDescription())
                .setAggregation(aggregation)
                .setCardinalityLimit(settings.getCardinalityLimit());
        boolean withCommonAttributes = settings.isWithCommonAttributes();

        if (!CollectionUtils.isEmpty(settings.getAttributes())) {
            builder.setAttributeFilter((attribute) -> filterAttribute(settings, attribute));
        }
        else if (withCommonAttributes){
            builder.setAttributeFilter(this::isDefinedAsGlobal);
        }
        else {
            builder.setAttributeFilter((attr) -> false); // reject all attributes
        }

        return builder.build();
    }

    /**
     * Checks, if the view includes the provided attribute key or if the key is defined globally
     *
     * @param settings the view settings
     * @param attribute the current attribute key
     *
     * @return true, if this attribute key should be used for the provided view
     */
    private boolean filterAttribute(ViewDefinitionSettings settings, String attribute) {
        return settings.getAttributes().getOrDefault(attribute, false) ||
                isDefinedAsGlobal(attribute);
    }

    /**
     * @param attribute the current attribute key
     *
     * @return true, if the attribute key is defined globally
     */
    private boolean isDefinedAsGlobal(String attribute) {
        return configuration.getAttributes().getDefineAsGlobal().contains(attribute);
    }

    /**
     * Creates a selector so the view can be applied to their particular metric.
     * At them moment, we select the metric solely by their name.
     *
     * @param metricName the metric name
     *
     * @return the instrument selector
     */
    private InstrumentSelector createSelector(String metricName) {
        return InstrumentSelector.builder()
                .setName(metricName)
                .build();
    }

    /**
     * Converts the {@link AggregationType} to a proper OpenTelemetry {@link Aggregation}.
     *
     * @param settings the view settings
     *
     * @return the converted {@link Aggregation}
     */
    private Aggregation convertAggregation(ViewDefinitionSettings settings) {
        AggregationType type = settings.getAggregation();
        return switch (type) {
            case SUM -> Aggregation.sum();
            case LAST_VALUE -> Aggregation.lastValue();
            case HISTOGRAM -> Aggregation.explicitBucketHistogram(settings.getBucketBoundaries());
            case EXPONENTIAL_HISTOGRAM -> Aggregation.base2ExponentialBucketHistogram(settings.getMaxBuckets(), settings.getMaxScale());
            default -> throw new IllegalArgumentException("Unexpected OpenTelemetry aggregation:" + type);
        };
    }

    private String getUnit(String metricName) {
        return configuration.getDefinitions().get(metricName).getUnit();
    }
}
