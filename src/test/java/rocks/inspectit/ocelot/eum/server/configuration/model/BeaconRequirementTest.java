package rocks.inspectit.ocelot.eum.server.configuration.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.BeaconRequirement;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.beacon.InitiatorType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BeaconRequirementTest {

    @Nested
    class StaticValidate {

        Beacon beacon;

        @BeforeEach
        void before() {
            HashMap<String, String> map = new HashMap<>();
            map.put("first", "1");
            map.put("second", "2");
            beacon = Beacon.of(map);
        }

        @Test
        void noFulfillment() {
            BeaconRequirement requirementA = new BeaconRequirement();
            requirementA.setField("third");
            requirementA.setRequirement(BeaconRequirement.RequirementType.NOT_EXISTS);
            BeaconRequirement requirementB = new BeaconRequirement();
            requirementB.setField("second");
            requirementB.setRequirement(BeaconRequirement.RequirementType.NOT_EXISTS);

            boolean result = BeaconRequirement.validate(beacon, List.of(requirementA, requirementB));

            assertThat(result).isFalse();
        }

        @Test
        void fulfillment() {
            BeaconRequirement requirementA = new BeaconRequirement();
            requirementA.setField("third");
            requirementA.setRequirement(BeaconRequirement.RequirementType.NOT_EXISTS);
            BeaconRequirement requirementB = new BeaconRequirement();
            requirementB.setField("second");
            requirementB.setRequirement(BeaconRequirement.RequirementType.EXISTS);

            boolean result = BeaconRequirement.validate(beacon, List.of(requirementA, requirementB));

            assertThat(result).isTrue();
        }

        @Test
        void nullList() {
            boolean result = BeaconRequirement.validate(beacon, null);

            assertThat(result).isTrue();
        }

        @Test
        void emptyList() {
            boolean result = BeaconRequirement.validate(beacon, Collections.emptyList());

            assertThat(result).isTrue();
        }
    }

    @Nested
    class Validate {

        BeaconRequirement requirement = new BeaconRequirement();

        @Test
        void noFulfillmentNotExists() {
            Beacon beacon = Beacon.of(Collections.singletonMap("field", "5"));
            requirement.setField("field");
            requirement.setRequirement(BeaconRequirement.RequirementType.NOT_EXISTS);

            boolean result = requirement.validate(beacon);

            assertThat(result).isFalse();
        }

        @Test
        void fulfillmentNotExists() {
            Beacon beacon = Beacon.of(Collections.singletonMap("field", "5"));
            requirement.setField("another");
            requirement.setRequirement(BeaconRequirement.RequirementType.NOT_EXISTS);

            boolean result = requirement.validate(beacon);

            assertThat(result).isTrue();
        }

        @Test
        void noFulfillmentExists() {
            Beacon beacon = Beacon.of(Collections.singletonMap("field", "5"));
            requirement.setField("another");
            requirement.setRequirement(BeaconRequirement.RequirementType.EXISTS);

            boolean result = requirement.validate(beacon);

            assertThat(result).isFalse();
        }

        @Test
        void fulfillmentExists() {
            Beacon beacon = Beacon.of(Collections.singletonMap("field", "5"));
            requirement.setField("field");
            requirement.setRequirement(BeaconRequirement.RequirementType.EXISTS);

            boolean result = requirement.validate(beacon);

            assertThat(result).isTrue();
        }

        @Test
        void wrongInitiator() {
            Beacon beacon = Beacon.of(Collections.singletonMap("http.initiator", "spa"));
            requirement.setInitiators(List.of(InitiatorType.SPA_HARD));
            requirement.setRequirement(BeaconRequirement.RequirementType.HAS_INITIATOR);

            boolean result = requirement.validate(beacon);

            assertThat(result).isFalse();
        }

        @Test
        void correctInitiator() {
            Beacon beacon = Beacon.of(Collections.singletonMap("http.initiator", "xhr"));
            requirement.setInitiators(List.of(InitiatorType.SPA_HARD, InitiatorType.XHR));
            requirement.setRequirement(BeaconRequirement.RequirementType.HAS_INITIATOR);

            boolean result = requirement.validate(beacon);

            assertThat(result).isTrue();
        }

        @Test
        void nullInitiator() {
            Beacon beacon = Beacon.of(Collections.emptyMap());
            requirement.setInitiators(List.of(InitiatorType.SPA_HARD, InitiatorType.XHR));
            requirement.setRequirement(BeaconRequirement.RequirementType.HAS_INITIATOR);

            boolean result = requirement.validate(beacon);

            assertThat(result).isFalse();
        }

        @Test
        void documentInitiator() {
            Beacon beacon = Beacon.of(Collections.emptyMap());
            requirement.setInitiators(Collections.singletonList(InitiatorType.DOCUMENT));
            requirement.setRequirement(BeaconRequirement.RequirementType.HAS_INITIATOR);

            boolean result = requirement.validate(beacon);

            assertThat(result).isTrue();
        }
    }
}
