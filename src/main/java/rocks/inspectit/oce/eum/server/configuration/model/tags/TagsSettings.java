package rocks.inspectit.oce.eum.server.configuration.model.tags;

import lombok.Data;
import lombok.NoArgsConstructor;
import rocks.inspectit.oce.eum.server.configuration.model.tags.providers.TagsProvidersSettings;
import rocks.inspectit.oce.eum.server.utils.IPUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.*;

/**
 * Holds an additional map of tags, which will be resolved based on the EUM beacon.
 */
@Data
@NoArgsConstructor
public class TagsSettings {

    /**
     * Settings for available tags providers.
     */
    @Valid
    private TagsProvidersSettings providers;

    /**
     * Map of arbitrary user defined tags.
     */
    private Map<String, String> extra = new HashMap<>();

    private static final String IP_PATTERN = "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])($|(\\/[1-9]$|\\/[1-2][0-9]$|\\/3[0-2]$))";

    /**
     * List of tags, which are defined as global
     */
    private final Set<String> defineAsGlobal = new HashSet<>();

    /**
     * Custom IP mapping for COUNTRY_CODE
     */
    private final Map<String, List<@Pattern(regexp = IP_PATTERN) String>> customIPMapping = new HashMap<>();

    /**
     * Tags which are derived using regex-replace operations.
     * The keys are the names of the beacon fields under which the results of the given replacement operation will be stored.
     * Tags via regexes can depend on each other, as long as no cyclic dependency is involved.
     */
    private Map<String, BeaconTagSettings> beacon = new HashMap<>();

    /**
     * IPUtils
     */
    private IPUtils ipUtils = new IPUtils();

    @AssertFalse(message = "All defined global tags should exist either in extra tags or beacon tags")
    public boolean isGlobalTagMissing() {
        return defineAsGlobal.stream()
                .anyMatch(globalTag ->
                        !(getExtra().containsKey(globalTag)
                                || getBeacon().containsKey(globalTag)
                        )
                );
    }

    @AssertTrue(message = "Each tag should only be defined once")
    public boolean isCheckUniquenessOfTags() {
        return getExtra().keySet().stream().allMatch(extraTag -> !beacon.containsKey(extraTag));
    }

    @AssertTrue(message = "The ip definitions between the different categories must not overlap")
    public boolean isCheckIpRangesDoNotOverlap() {
        return customIPMapping.values().stream()
                .allMatch(ipList -> ipList.stream()
                        .allMatch(adresse -> customIPMapping.values().stream()
                                .allMatch(listToCompare -> listToCompare == ipList || listToCompare.stream().noneMatch(adresseToCompare -> areOverlapping(adresse, adresseToCompare)))));
    }

    /**
     * Helper method, which compares two address entries.
     *
     * @param address1
     * @param address2
     * @return
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
