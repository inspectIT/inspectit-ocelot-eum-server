package rocks.inspectit.ocelot.eum.server.metrics;

import io.opentelemetry.api.baggage.Baggage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.AggregationType;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class InstrumentManagerTest {

    @InjectMocks
    InstrumentManager manager;

    @Mock
    InstrumentFactory factory;

    @Mock
    AttributesRegistry registry;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    EumServerConfiguration configuration;

    final String metricName = "my-metric";

    @Test
    void shouldCreateInstrumentAndProcessAttributes() {
        MetricDefinitionSettings metric = new MetricDefinitionSettings();
        ViewDefinitionSettings view = new ViewDefinitionSettings();
        metric.setViews(Map.of("my-view", view));

        manager.createInstrument(metricName, metric);

        verify(factory).createInstrument(anyString(), any(MetricDefinitionSettings.class));
        verify(registry).processAttributeKeysForView(any(ViewDefinitionSettings.class));
    }

    @Test
    void shouldCreateInstrumentAndNotProcessAttributesWhenNoViews() {
        MetricDefinitionSettings metric = new MetricDefinitionSettings();

        manager.createInstrument(metricName, metric);

        verify(factory).createInstrument(anyString(), any(MetricDefinitionSettings.class));
        verifyNoInteractions(registry);
    }

    @Test
    void shouldNotCreateInstrumentAndProcessAttributesWhenTimeWindowView() {
        MetricDefinitionSettings metric = new MetricDefinitionSettings();
        ViewDefinitionSettings view = new ViewDefinitionSettings();
        view.setAggregation(AggregationType.QUANTILES);
        metric.setViews(Map.of("my-view", view));

        manager.createInstrument(metricName, metric);

        verifyNoInteractions(factory);
        verify(registry).processAttributeKeysForView(any(ViewDefinitionSettings.class));
    }

    @Test
    void shouldReturnBaggageWithCustomAndGlobalAttributes() {
        Map<String, String> customAttributes = Map.of("key1", "value1", "key2", "value2");
        when(configuration.getAttributes().getExtra()).thenReturn(Map.of("key3", "value3"));
        when(registry.getRegisteredGlobalAttributes()).thenReturn(Set.of("key3"));

        Baggage baggage = manager.getBaggage(customAttributes);

        assertThat(baggage.asMap().get("key1").getValue()).isEqualTo("value1");
        assertThat(baggage.asMap().get("key2").getValue()).isEqualTo("value2");
        assertThat(baggage.asMap().get("key3").getValue()).isEqualTo("value3");
    }

    @Test
    void shouldReturnBaggageWithOnlyCustomAttributes() {
        Map<String, String> customAttributes = Map.of("key1", "value1", "key2", "value2");
        when(registry.getRegisteredGlobalAttributes()).thenReturn(Set.of("key3"));

        Baggage baggage = manager.getBaggage(customAttributes);

        assertThat(baggage.asMap().get("key1").getValue()).isEqualTo("value1");
        assertThat(baggage.asMap().get("key2").getValue()).isEqualTo("value2");
    }
}
