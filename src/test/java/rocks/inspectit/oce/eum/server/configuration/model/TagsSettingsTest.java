package rocks.inspectit.oce.eum.server.configuration.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.oce.eum.server.configuration.model.tags.BeaconTagSettings;
import rocks.inspectit.oce.eum.server.configuration.model.tags.TagsSettings;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TagsSettingsTest {

    @Nested
    public class IsGlobalTagMissing {

        @Test
        public void emptySettings() {
            TagsSettings settings = new TagsSettings();

            boolean result = settings.isGlobalTagMissing();

            assertThat(result).isFalse();
        }

        @Test
        public void tagMissing() {
            TagsSettings settings = new TagsSettings();
            settings.getDefineAsGlobal().add("missing-tag");

            boolean result = settings.isGlobalTagMissing();

            assertThat(result).isTrue();
        }

        @Test
        public void hasBeaconTag() {
            TagsSettings settings = new TagsSettings();
            settings.getDefineAsGlobal().add("beacon-tag");
            settings.getBeacon().put("beacon-tag", BeaconTagSettings.builder().input("beacon-field").build());

            boolean result = settings.isGlobalTagMissing();

            assertThat(result).isFalse();
        }

        @Test
        public void hasExtraTag() {
            TagsSettings settings = new TagsSettings();
            settings.getDefineAsGlobal().add("extra-tag");
            settings.getExtra().put("extra-tag", "constant-value");

            boolean result = settings.isGlobalTagMissing();

            assertThat(result).isFalse();
        }
    }

    @Nested
    public class IsCheckUniquenessOfTags {

        @Test
        public void emptySettings() {
            TagsSettings settings = new TagsSettings();

            boolean result = settings.isCheckUniquenessOfTags();

            assertThat(result).isTrue();
        }

        @Test
        public void noDuplicateTags() {
            TagsSettings settings = new TagsSettings();
            settings.getExtra().put("tag_a", "");
            settings.getBeacon().put("tag_b", BeaconTagSettings.builder().build());

            boolean result = settings.isCheckUniquenessOfTags();

            assertThat(result).isTrue();
        }

        @Test
        public void hasDuplicateTags() {
            TagsSettings settings = new TagsSettings();
            settings.getExtra().put("tag_a", "");
            settings.getBeacon().put("tag_a", BeaconTagSettings.builder().build());

            boolean result = settings.isCheckUniquenessOfTags();

            assertThat(result).isFalse();
        }
    }

    @Nested
    public class IsCheckIPsRangesDoNotOverlap {

        @Test
        public void emptyCustomMapping() {
            TagsSettings settings = new TagsSettings();

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void ipsAreEqual() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"127.127.127.127"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"127.127.127.127"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void ipsAreNotEqual() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"127.127.127.127"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"127.127.127.128"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrsAreEqual() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.0/16"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrsOverlap() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.0/17"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrsDoNotOverlap() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.1.0.0/16"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrContainsIp() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"10.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isFalse();
        }

        @Test
        public void cidrDoesNotContainIp() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrsOfSameLabelAreOverlapping() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16", "10.0.0.0/17"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }

        @Test
        public void cidrAndIpOfSameLabelAreOverlapping() {
            TagsSettings settings = new TagsSettings();
            settings.getCustomIPMapping().put("GER", Arrays.asList(new String[]{"10.0.0.0/16", "10.0.0.1"}));
            settings.getCustomIPMapping().put("FR", Arrays.asList(new String[]{"11.0.0.1"}));

            boolean result = settings.isCheckIpRangesDoNotOverlap();

            assertThat(result).isTrue();
        }
    }
}
