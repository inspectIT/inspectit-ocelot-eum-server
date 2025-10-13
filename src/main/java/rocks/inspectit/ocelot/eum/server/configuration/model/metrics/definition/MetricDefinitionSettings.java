package rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition;

import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.InstrumentValueType;
import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.AggregationType;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines an OpenTelemetry metric in combination with one or multiple views
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MetricDefinitionSettings {

    /**
     * Defines if this metric is enabled.
     * If this metric is disabled:
     * - no views for it are created
     */
    @Builder.Default
    private boolean enabled = true;

    @NotBlank
    private String unit;

    @NotNull
    @Builder.Default
    private InstrumentType instrumentType = InstrumentType.GAUGE;

    @NotNull
    @Builder.Default
    private InstrumentValueType valueType = InstrumentValueType.LONG;

    /**
     * The description of the metric.
     * If this is null, the description is simply the name of the metric.
     */
    private String description;

    /**
     * Maps view names to their definitions for the metric defined by this {@link MetricDefinitionSettings}.
     */
    @Singular
    private Map<@NotBlank String, @Valid @NotNull ViewDefinitionSettings> views;

    /**
     * Copies the settings of this object but applies the defaults, like creating a default view if no views were defined.
     *
     * @param metricName        the name of the metric
     *
     * @return a copy of this view definition with the default populated
     */
    public MetricDefinitionSettings getCopyWithDefaultsPopulated(String metricName) {
        val resultDescription = description == null ? metricName : description;
        val result = toBuilder().description(resultDescription).clearViews();

        if(!CollectionUtils.isEmpty(views)) {
            views.forEach((name, def) -> result.view(name, def.getCopyWithDefaultsPopulated(resultDescription, unit)));
        }
        else {
            /*
                If there are no views specified, we will create a default view which only sets the default
                OpenTelemetry aggregation for the particular instrument type.
                Otherwise, we would not know about this view and could not set a proper AttributesProcessor
                in the ViewManager to regulate, which attributes are allowed for the metric
             */
            Map<String, ViewDefinitionSettings> defaultView = new HashMap<>();
            val builder =  ViewDefinitionSettings.builder();
            switch (instrumentType) {
                case GAUGE:
                    ViewDefinitionSettings gaugeView = builder.aggregation(AggregationType.LAST_VALUE).build();
                    defaultView.put(metricName, gaugeView);
                    break;
                case COUNTER:
                case UP_DOWN_COUNTER:
                    ViewDefinitionSettings counterView = builder.aggregation(AggregationType.SUM).build();
                    defaultView.put(metricName, counterView);
                    break;
                case HISTOGRAM:
                    ViewDefinitionSettings histogramView = builder.aggregation(AggregationType.HISTOGRAM).build();
                    defaultView.put(metricName, histogramView);
                    break;
            }
            result.views(defaultView);
        }

        return result.build();
    }
}
