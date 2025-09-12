package rocks.inspectit.ocelot.eum.server.opentelemetry.metrics;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.metrics.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view.AggregationType;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.TimeWindowViewManager;

import java.util.*;

/**
 * Stores all user-specified metric views.
 */
@Component
public class ViewManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private TimeWindowViewManager viewManager;

    /**
     * Registers all configured views in the {@link SdkMeterProviderBuilder}.
     *
     * @param builder the sdk builder to register views with
     *
     * @return the extended builder
     */
    public SdkMeterProviderBuilder registerViews(SdkMeterProviderBuilder builder) {
        Map<String, Map<String, ViewDefinitionSettings>> metricViews = getAllViewsForMetrics();

        for (Map.Entry<String, Map<String, ViewDefinitionSettings>> metricEntry : metricViews.entrySet()) {
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
        return builder;
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
     * @return the map of all metric names and their particular view settings
     */
    private Map<String, Map<String, ViewDefinitionSettings>> getAllViewsForMetrics() {
        Map<String, Map<String, ViewDefinitionSettings>> metricViews = new HashMap<>();
        configuration.getDefinitions()
                .forEach((metricName, metricDefinition) -> metricViews.put(metricName, metricDefinition.getViews()));
        configuration.getSelfMonitoring().getMetrics()
                .forEach((metricName, metricDefinition) -> metricViews.put(metricName, metricDefinition.getViews()));

        return metricViews;
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

        if(!CollectionUtils.isEmpty(settings.getAttributes())) {
           builder.setAttributeFilter(attribute -> settings.getAttributes().getOrDefault(attribute, false));
        }

        return builder.build();
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
