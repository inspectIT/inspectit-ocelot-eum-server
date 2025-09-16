package rocks.inspectit.ocelot.eum.server.rest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.beacon.processor.CompositeBeaconProcessor;
import rocks.inspectit.ocelot.eum.server.exporters.beacon.BeaconHttpExporter;
import rocks.inspectit.ocelot.eum.server.metrics.BeaconMetricManager;
import rocks.inspectit.ocelot.eum.server.metrics.SelfMonitoringMetricManager;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeaconControllerTest {

    @InjectMocks
    BeaconController controller;

    @Mock
    BeaconMetricManager beaconMetricManager;

    @Mock
    CompositeBeaconProcessor beaconProcessor;

    @Mock
    SelfMonitoringMetricManager selfMonitoringService;

    @Mock
    BeaconHttpExporter beaconHttpExporter;

    @Nested
    class BeaconPost {

        @Test
        void successful() {
            when(beaconProcessor.process(any())).then(i -> i.getArguments()[0]);
            when(beaconMetricManager.processBeacon(any())).thenReturn(true);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("key", "value");

            ResponseEntity result = controller.beaconPost(map);

            ArgumentCaptor<Beacon> beaconCaptor = ArgumentCaptor.forClass(Beacon.class);
            verify(beaconMetricManager).processBeacon(beaconCaptor.capture());
            verify(beaconProcessor).process(any());
            verify(selfMonitoringService).record("beacons_received", 1, Collections.singletonMap("is_error", "false"));
            verifyNoMoreInteractions(beaconMetricManager, beaconProcessor, selfMonitoringService);

            assertThat(result).extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
            assertThat(beaconCaptor.getValue().toMap()).hasSize(1).containsEntry("key", "value");
        }

        @Test
        void notSuccessful() {
            when(beaconProcessor.process(any())).then(i -> i.getArguments()[0]);
            when(beaconMetricManager.processBeacon(any())).thenReturn(false);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

            ResponseEntity result = controller.beaconPost(map);

            verify(beaconMetricManager).processBeacon(any());
            verify(beaconProcessor).process(any());
            verify(selfMonitoringService).record("beacons_received", 1, Collections.singletonMap("is_error", "true"));
            verifyNoMoreInteractions(beaconMetricManager, beaconProcessor, selfMonitoringService);

            assertThat(result).extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
        }

    }

    @Nested
    class BeaconGet {

        @Test
        void successful() {
            when(beaconProcessor.process(any())).then(i -> i.getArguments()[0]);
            when(beaconMetricManager.processBeacon(any())).thenReturn(true);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("key", "value");

            ResponseEntity result = controller.beaconGet(map);

            ArgumentCaptor<Beacon> beaconCaptor = ArgumentCaptor.forClass(Beacon.class);
            verify(beaconMetricManager).processBeacon(beaconCaptor.capture());
            verify(beaconProcessor).process(any());
            verify(selfMonitoringService).record("beacons_received", 1, Collections.singletonMap("is_error", "false"));
            verifyNoMoreInteractions(beaconMetricManager, beaconProcessor, selfMonitoringService);

            assertThat(result).extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
            assertThat(beaconCaptor.getValue().toMap()).hasSize(1).containsEntry("key", "value");
        }

        @Test
        void notSuccessful() {
            when(beaconProcessor.process(any())).then(i -> i.getArguments()[0]);
            when(beaconMetricManager.processBeacon(any())).thenReturn(false);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

            ResponseEntity result = controller.beaconGet(map);

            verify(beaconMetricManager).processBeacon(any());
            verify(beaconProcessor).process(any());
            verify(selfMonitoringService).record("beacons_received", 1, Collections.singletonMap("is_error", "true"));
            verifyNoMoreInteractions(beaconMetricManager, beaconProcessor, selfMonitoringService);

            assertThat(result).extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
        }
    }
}
