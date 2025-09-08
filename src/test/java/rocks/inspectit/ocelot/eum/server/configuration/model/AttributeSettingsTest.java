package rocks.inspectit.ocelot.eum.server.configuration.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.BeaconAttributeSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.AttributeSettings;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AttributeSettingsTest {

    @Nested
    public class IsGlobalAttributeMissing {

        @Test
        public void emptySettings() {
            AttributeSettings settings = new AttributeSettings();

            boolean result = settings.isGlobalAttributeMissing();

            assertThat(result).isFalse();
        }

        @Test
        public void tagMissing() {
            AttributeSettings settings = new AttributeSettings();
            settings.getDefineAsGlobal().add("missing-tag");

            boolean result = settings.isGlobalAttributeMissing();

            assertThat(result).isTrue();
        }

        @Test
        public void hasBeaconTag() {
            AttributeSettings settings = new AttributeSettings();
            settings.getDefineAsGlobal().add("beacon-tag");
            settings.getBeacon().put("beacon-tag", BeaconAttributeSettings.builder().input("beacon-field").build());

            boolean result = settings.isGlobalAttributeMissing();

            assertThat(result).isFalse();
        }

        @Test
        public void hasExtraTag() {
            AttributeSettings settings = new AttributeSettings();
            settings.getDefineAsGlobal().add("extra-attribute");
            settings.getExtra().put("extra-attribute", "constant-value");

            boolean result = settings.isGlobalAttributeMissing();

            assertThat(result).isFalse();
        }
    }

    @Nested
    public class IsCheckUniquenessOfAttributes {

        @Test
        public void emptySettings() {
            AttributeSettings settings = new AttributeSettings();

            boolean result = settings.isCheckUniquenessOfAttributes();

            assertThat(result).isTrue();
        }

        @Test
        public void noDuplicateTags() {
            AttributeSettings settings = new AttributeSettings();
            settings.getExtra().put("tag_a", "");
            settings.getBeacon().put("tag_b", BeaconAttributeSettings.builder().build());

            boolean result = settings.isCheckUniquenessOfAttributes();

            assertThat(result).isTrue();
        }

        @Test
        public void hasDuplicateTags() {
            AttributeSettings settings = new AttributeSettings();
            settings.getExtra().put("tag_a", "");
            settings.getBeacon().put("tag_a", BeaconAttributeSettings.builder().build());

            boolean result = settings.isCheckUniquenessOfAttributes();

            assertThat(result).isFalse();
        }
    }

    @Nested
    public class IsCheckIPsRangesDoNotOverlap {

        @Test
        public void emptyCustomMapping() {
            AttributeSettings settings = new AttributeSettings();

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void ipsAreEqual() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"127.127.127.127"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"127.127.127.127"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void ipsAreNotEqual() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"127.127.127.127"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"127.127.127.128"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrsAreEqual() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.0/16"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrsOverlap() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.0/17"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrsDoNotOverlap() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.1.0.0/16"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrContainsIp() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrDoesNotContainIp() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrsOfSameLabelAreOverlapping() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16", "10.0.0.0/17"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrAndIpOfSameLabelAreOverlapping() {
            AttributeSettings settings = new AttributeSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16", "10.0.0.1"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }
    }
}
