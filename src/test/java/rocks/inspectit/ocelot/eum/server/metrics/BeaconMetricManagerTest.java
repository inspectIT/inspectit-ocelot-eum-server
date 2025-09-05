package rocks.inspectit.ocelot.eum.server.metrics;

import com.google.common.collect.ImmutableMap;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.Tags;
import io.opentelemetry.sdk.metrics.InstrumentValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.beacon.recorder.BeaconRecorder;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconMetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.tags.BeaconTagSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.events.RegisteredAttributesEvent;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BeaconMetricManager}
 */
@ExtendWith(MockitoExtension.class)
public class BeaconMetricManagerTest {

    @InjectMocks
    BeaconMetricManager beaconMetricManager;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    EumServerConfiguration configuration;

    @Mock
    InstrumentManager instrumentManager;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    StatsRecorder statsRecorder;

    @Mock
    ViewManager viewManager;

    @Spy
    List<BeaconRecorder> beaconRecorders = new ArrayList<>(Arrays.asList(mock(BeaconRecorder.class)));

    private final Set<String> registeredTags = new HashSet<>(Arrays.asList("first", "second", "third"));

    @Nested
    class ProcessUsedTags {

        @Test
        void processOneUsedTag() {
            Map<String, BeaconTagSettings> beaconSettings = Collections.singletonMap("first", new BeaconTagSettings());
            when(configuration.getTags().getBeacon()).thenReturn(beaconSettings);

            beaconMetricManager.processUsedTags(new RegisteredAttributesEvent(this, registeredTags));

            assertThat(beaconMetricManager.registeredBeaconAttributes).containsExactly("first");
        }

        @Test
        void processMultipleUsedTags() {
            Map<String, BeaconTagSettings> beaconSettings = ImmutableMap.of("first", new BeaconTagSettings(), "third", new BeaconTagSettings());
            when(configuration.getTags().getBeacon()).thenReturn(beaconSettings);

            beaconMetricManager.processUsedTags(new RegisteredAttributesEvent(this, registeredTags));

            assertThat(beaconMetricManager.registeredBeaconAttributes).containsExactlyInAnyOrder("first", "third");
        }

        @Test
        void processNoTags() {
            when(configuration.getTags().getBeacon()).thenReturn(Collections.emptyMap());

            beaconMetricManager.processUsedTags(new RegisteredAttributesEvent(this, registeredTags));

            assertThat(beaconMetricManager.registeredBeaconAttributes).isEmpty();
        }
    }

    @Nested
    class ProcessBeacon {

        private Map<String, BeaconMetricDefinitionSettings> definitionMap;

        @BeforeEach
        void setupConfiguration() {
            ViewDefinitionSettings view = ViewDefinitionSettings.builder()
                    .bucketBoundaries(Arrays.asList(0d, 1d))
                    .aggregation(ViewDefinitionSettings.Aggregation.HISTOGRAM)
                    .tag("TAG_1", true)
                    .tag("TAG_2", true)
                    .build();
            Map<String, ViewDefinitionSettings> views = new HashMap<>();
            views.put("Dummy metric name/HISTOGRAM", view);

            BeaconMetricDefinitionSettings dummyMetricDefinition = BeaconMetricDefinitionSettings.beaconMetricBuilder()
                    .valueExpression("{dummy_beacon_field}")
                    .description("Dummy description")
                    //.instrumentType()
                    .valueType(InstrumentValueType.DOUBLE)
                    .unit("ms")
                    .enabled(true)
                    .views(views)
                    .build();

            definitionMap = new HashMap<>();
            definitionMap.put("Dummy metric name", dummyMetricDefinition);
        }

        @BeforeEach
        public void setupMocks() {
            when(instrumentManager.getTagContext()).thenReturn(Tags.getTagger().emptyBuilder());
        }

        @Test
        void verifyNoViewIsGeneratedWithEmptyBeacon() {
            when(configuration.getDefinitions()).thenReturn(definitionMap);
            HashMap<String, String> beaconMap = new HashMap<>();

            beaconMetricManager.processBeacon(Beacon.of(beaconMap));

            verifyNoMoreInteractions(viewManager, statsRecorder);
        }

        @Test
        void verifyNoViewIsGeneratedWithFullBeacon() {
            when(configuration.getDefinitions()).thenReturn(definitionMap);
            HashMap<String, String> beaconMap = new HashMap<>();
            beaconMap.put("fake_beacon_field", "12d");

            beaconMetricManager.processBeacon(Beacon.of(beaconMap));

            verifyNoMoreInteractions(viewManager, statsRecorder);
        }

        @Test
        void beaconRecordersProcessed() {
            when(configuration.getDefinitions()).thenReturn(definitionMap);
            HashMap<String, String> beaconMap = new HashMap<>();
            beaconMap.put("fake_beacon_field", "12d");
            Beacon beacon = Beacon.of(beaconMap);

            beaconMetricManager.processBeacon(beacon);

            assertThat(beaconRecorders).allSatisfy(beaconRecorder -> {
                verify(beaconRecorder).record(beacon);
                verifyNoMoreInteractions(beaconRecorder);
            });
            verifyNoMoreInteractions(viewManager, statsRecorder);
        }

    }
}
