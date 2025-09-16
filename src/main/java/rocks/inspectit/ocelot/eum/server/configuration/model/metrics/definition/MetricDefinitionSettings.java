package rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition;

import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.InstrumentValueType;
import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.time.Duration;
import java.util.Map;

/**
 * Defines an OpenTelemetry instrument in combination with one or multiple views
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
    private InstrumentValueType valueType = InstrumentValueType.DOUBLE;

    /**
     * The description of the metric.
     * If this is null, the description is simply the name of the metric.
     */
    private String description;

    /**
     * Maps view names to their definitions for the metric defined by this {@link MetricDefinitionSettings}.
     * If this map is null, OpenTelemetry will create a default view automatically.
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

        return result.build();
    }
}
