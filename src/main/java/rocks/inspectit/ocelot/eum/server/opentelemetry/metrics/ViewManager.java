package rocks.inspectit.ocelot.eum.server.opentelemetry.metrics;

import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view.ViewDefinitionSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stores all user-specified views.
 */
@Component
public class ViewManager {

    @Autowired
    private EumServerConfiguration configuration;

    private Set<View> registeredViews;

    /**
     * Registers all configured views in the {@link SdkMeterProviderBuilder} and caches them in {@link #registeredViews}.
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

                View view = createView(viewName, settings);
                InstrumentSelector selector = createSelector(metricName);

                builder.registerView(selector, view);
                registeredViews.add(view);
            }
        }
        return builder;
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
        return View.builder()
                .setName(viewName)
                .setDescription(settings.getDescription())
                .setAttributeFilter(attribute -> settings.getAttributes().get(attribute))
                .setAggregation(settings.getAggregation().convert())
                .setCardinalityLimit(settings.getCardinalityLimit())
                .build();
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
     * Checks, if the view is already registered.
     *
     * @param viewName the name to check
     *
     * @return true, if the view is already registered
     */
    public boolean isRegistered(String viewName) {
        return registeredViews.stream()
                .anyMatch(view -> Objects.equals(view.getName(), viewName));
    }
}
