package rocks.inspectit.ocelot.eum.server.configuration.model.exporters.beacon;

import rocks.inspectit.ocelot.eum.server.beacon.Beacon;

/**
 * Types of initiators for beacons.
 * These correspond to values for the http.initiator fields of beacons.
 */
public enum InitiatorType {
    DOCUMENT {
        @Override
        boolean isEqualToBeaconHttpInitiator(String httpInitiatorValue) {
            return httpInitiatorValue == null || "".equals(httpInitiatorValue);
        }
    },
    XHR {
        @Override
        boolean isEqualToBeaconHttpInitiator(String httpInitiatorValue) {
            return "xhr".equalsIgnoreCase(httpInitiatorValue);
        }
    },
    SPA_SOFT {
        @Override
        boolean isEqualToBeaconHttpInitiator(String httpInitiatorValue) {
            return "spa".equalsIgnoreCase(httpInitiatorValue);
        }
    },
    SPA_HARD {
        @Override
        boolean isEqualToBeaconHttpInitiator(String httpInitiatorValue) {
            return "spa_hard".equalsIgnoreCase(httpInitiatorValue);
        }
    };

    abstract boolean isEqualToBeaconHttpInitiator(String httpInitiatorValue);

    /**
     * Checks if the given beacon has an initiator matching this {@link InitiatorType}.
     *
     * @param beacon the beacon to check
     *
     * @return true, if the initiator matches
     */
    public boolean hasInitiator(Beacon beacon) {
        return isEqualToBeaconHttpInitiator(beacon.get("http.initiator"));
    }

}
