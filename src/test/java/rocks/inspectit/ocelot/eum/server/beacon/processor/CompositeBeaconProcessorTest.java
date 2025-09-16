package rocks.inspectit.ocelot.eum.server.beacon.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.metrics.SelfMonitoringMetricManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeBeaconProcessorTest {

    @InjectMocks
    CompositeBeaconProcessor processor;

    @Mock
    SelfMonitoringMetricManager selfMonitoring;

    @Spy
    List<BeaconProcessor> processorList = ImmutableList.of(beacon -> beacon.merge(ImmutableMap.of("key2", "value2")), beacon -> beacon
            .merge(ImmutableMap.of("key1", "value2")));

    @Test
    void test() {
        Beacon processedBeacon = processor.process(Beacon.of(ImmutableMap.of("key1", "value1")));

        // Ensure value got properly overwritten
        assertThat(processedBeacon.get("key1")).isEqualTo("value2");
        // Ensure new key was properly added
        assertThat(processedBeacon.get("key2")).isEqualTo("value2");

        ArgumentCaptor<Map<String, String>> tagCaptor = ArgumentCaptor.forClass(Map.class);
        verify(selfMonitoring, times(processorList.size())).record(eq("beacons_processor"), any(), tagCaptor.capture());
        verifyNoMoreInteractions(selfMonitoring);

        Map.Entry<String, String>[] processorTagEntries = (Map.Entry<String, String>[]) processorList.stream()
                .map(processor -> entry("beacon_processor", processor.getClass().getSimpleName()))
                .toArray(Map.Entry[]::new);
        assertThat(tagCaptor.getAllValues()).flatExtracting(Map::entrySet).contains(processorTagEntries);
    }
}
