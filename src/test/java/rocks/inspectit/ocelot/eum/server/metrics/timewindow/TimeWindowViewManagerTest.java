package rocks.inspectit.ocelot.eum.server.metrics.timewindow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.AttributeSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.AggregationType;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.metrics.AttributesRegistry;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.TimeWindowView;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeWindowViewManagerTest {

    @InjectMocks
    TimeWindowViewManager viewManager;

    @Mock
    EumServerConfiguration configuration;

    @Mock
    AttributesRegistry attributesRegistry;

    @BeforeEach
    void beforeEach() {
        lenient().when(configuration.getAttributes()).thenReturn(new AttributeSettings());
    }

    @Test
    void shouldRegisterViewWhenQuantilesAggregation() {
        String metricName = "my/metric";
        String viewName = "my/view";
        String unit = "ms";
        String desc = "description";
        Duration timeWindow = Duration.ofSeconds(1);
        int bufferLimit = 1000;
        ViewDefinitionSettings settings = new ViewDefinitionSettings();
        settings.setDescription(desc);
        settings.setTimeWindow(timeWindow);
        settings.setMaxBufferedPoints(bufferLimit);
        settings.setAggregation(AggregationType.QUANTILES);
        settings.setAttributes(Collections.emptyMap());

        viewManager.registerView(metricName, viewName, unit, settings);
        Collection<TimeWindowView> views = viewManager.getViews(metricName);

        assertThat(views).allMatch(view -> view.getViewName().equals(viewName));
        assertThat(views).allMatch(view -> view.getUnit().equals(unit));
        assertThat(views).allMatch(view -> view.getDescription().equals(desc));
        assertThat(views).allMatch(view -> view.getTimeWindow().equals(timeWindow));
        assertThat(views).allMatch(view -> view.getBufferLimit() == bufferLimit);
    }

    @Test
    void shouldRegisterViewWhenSmoothedAverageAggregation() {
        String metricName = "my/metric";
        String viewName = "my/view";
        String unit = "ms";
        String desc = "description";
        Duration timeWindow = Duration.ofSeconds(1);
        int bufferLimit = 1000;
        ViewDefinitionSettings settings = new ViewDefinitionSettings();
        settings.setDescription(desc);
        settings.setTimeWindow(timeWindow);
        settings.setMaxBufferedPoints(bufferLimit);
        settings.setAggregation(AggregationType.SMOOTHED_AVERAGE);
        settings.setAttributes(Collections.emptyMap());

        viewManager.registerView(metricName, viewName, unit, settings);
        Collection<TimeWindowView> views = viewManager.getViews(metricName);

        assertThat(views).allMatch(view -> view.getViewName().equals(viewName));
        assertThat(views).allMatch(view -> view.getUnit().equals(unit));
        assertThat(views).allMatch(view -> view.getDescription().equals(desc));
        assertThat(views).allMatch(view -> view.getTimeWindow().equals(timeWindow));
        assertThat(views).allMatch(view -> view.getBufferLimit() == bufferLimit);
    }

    @Test
    void shouldNotRegisterViewWhenOpenTelemetryAggregation() {
        String metricName = "my/metric";
        String viewName = "my/view";
        String unit = "ms";
        ViewDefinitionSettings settings = new ViewDefinitionSettings();
        settings.setAggregation(AggregationType.HISTOGRAM);

        assertThrows(IllegalArgumentException.class, () -> viewManager.registerView(metricName, viewName, unit, settings));
    }
}
