package rocks.inspectit.ocelot.eum.server.configuration.model.attributes;

import lombok.Data;
import lombok.NoArgsConstructor;
import rocks.inspectit.ocelot.eum.server.utils.IPUtils;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.*;

/**
 * Holds an additional map of attributes, which will be resolved based on the EUM beacon.
 */
@Data
@NoArgsConstructor
public class AttributeSettings {

    /**
     * Map of arbitrary user defined attributes
     */
    private Map<String, String> extra = new HashMap<>();

    private static final String IP_PATTERN = "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])($|(\\/[1-9]$|\\/[1-2][0-9]$|\\/3[0-2]$))";

    /**
     * List of attributes, which are defined as global
     */
    private final Set<String> defineAsGlobal = new HashSet<>();

    /**
     * Custom IP mapping for COUNTRY_CODE
     */
    private final Map<String, List<@Pattern(regexp = IP_PATTERN) String>> customIPMapping = new HashMap<>();

    /**
     * Attributes which are derived using regex-replace operations.
     * The keys are the names of the beacon fields under which the results of the given replacement operation will be stored.
     * Attributes via regexes can depend on each other, as long as no cyclic dependency is involved.
     */
    private Map<String, BeaconAttributeSettings> beacon = new HashMap<>();

    /**
     * IPUtils
     */
    private IPUtils ipUtils = new IPUtils();

    @AssertFalse(message = "All defined global attributes should exist either in extra tags or beacon tags")
    public boolean isGlobalAttributeMissing() {
        return defineAsGlobal.stream()
                .anyMatch(globalAttribute ->
                        !(getExtra().containsKey(globalAttribute)
                                || getBeacon().containsKey(globalAttribute)
                        )
                );
    }

    @AssertTrue(message = "Each attribute should only be defined once")
    public boolean isCheckUniquenessOfAttributes() {
        return getExtra().keySet().stream().allMatch(extraAttribute -> !beacon.containsKey(extraAttribute));
    }

    @AssertTrue(message = "The ip definitions between the different categories must not overlap")
    public boolean isCheckIpRangesDoNotOverlap() {
        return customIPMapping.values().stream()
                .allMatch(ipList -> ipList.stream()
                        .allMatch(address -> customIPMapping.values().stream()
                                .allMatch(listToCompare -> listToCompare == ipList ||
                                        listToCompare.stream()
                                                .noneMatch(addressToCompare -> areOverlapping(address, addressToCompare))
                                )
                        )
                );
    }

    /**
     * Helper method, which compares two address entries.
     */
    private boolean areOverlapping(String address1, String address2) {
        if (address1.contains("/") && address2.contains("/")) {
            return ipUtils.overlap(address1, address2);
        } else if (address1.contains("/") && !address2.contains("/")) {
            return ipUtils.containsIp(address1, address2);
        } else if (!address1.contains("/") && address2.contains("/")) {
            return ipUtils.containsIp(address2, address1);
        } else {
            return address1.equals(address2);
        }
    }
}
