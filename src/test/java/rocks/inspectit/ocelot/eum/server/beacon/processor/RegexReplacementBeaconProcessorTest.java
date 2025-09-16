package rocks.inspectit.ocelot.eum.server.beacon.processor;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.BeaconAttributeSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.AttributeSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.PatternAndReplacement;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RegexReplacementBeaconProcessorTest {

    @Nested
    class Process {

        @Test
        void errorOnCyclicDependency() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder().input("second").build();
            BeaconAttributeSettings second = BeaconAttributeSettings.builder().input("third").build();
            BeaconAttributeSettings third = BeaconAttributeSettings.builder().input("first").build();

            Map<String, BeaconAttributeSettings> regexSettings = new LinkedHashMap<>();
            regexSettings.put("first", first);
            regexSettings.put("second", second);
            regexSettings.put("third", third);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(regexSettings);

            assertThatThrownBy(() -> new RegexReplacementBeaconProcessor(conf)).hasMessageStartingWith("Cyclic");
        }

        @Test
        void dependenciesRespected() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("in")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("Bye World")
                            .build()))
                    .build();
            BeaconAttributeSettings second = BeaconAttributeSettings.builder()
                    .input("first")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Bye World")
                            .replacement("Bye Earth")
                            .build()))
                    .build();

            Map<String, BeaconAttributeSettings> invalidOrder = new LinkedHashMap<>();
            invalidOrder.put("second", second);
            invalidOrder.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(invalidOrder);

            RegexReplacementBeaconProcessor proc = new RegexReplacementBeaconProcessor(conf);

            Beacon result = proc.process(Beacon.of(ImmutableMap.of("in", "Hello World")));

            assertThat(result.get("in")).isEqualTo("Hello World");
            assertThat(result.get("first")).isEqualTo("Bye World");
            assertThat(result.get("second")).isEqualTo("Bye Earth");
        }

        @Test
        void copyWithoutRegexSupported() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder().input("in").build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor proc = new RegexReplacementBeaconProcessor(conf);

            Beacon result = proc.process(Beacon.of(ImmutableMap.of("in", "Hello World")));

            assertThat(result.get("first")).isEqualTo("Hello World");
        }

        @Test
        void inplaceChangeSupported() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("Goodbye")
                            .build()))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("value", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor proc = new RegexReplacementBeaconProcessor(conf);

            Beacon result = proc.process(Beacon.of(ImmutableMap.of("value", "Hello World")));

            assertThat(result.get("value")).isEqualTo("Goodbye");
        }

        @Test
        void handleBrokenReplacementStrings() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("one")
                            .build()))
                    .build();
            BeaconAttributeSettings second = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("$5")
                            .keepNoMatch(false)
                            .build()))
                    .build();
            BeaconAttributeSettings third = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("three")
                            .build()))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);
            beaconTags.put("second", second);
            beaconTags.put("third", third);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("value", "Hello World")));

            assertThat(result.toMap()).containsOnly(
                    entry("value", "Hello World"),
                    entry("first", "one"),
                    entry("third", "three")
            );
        }

        @Test
        void nullAttribute() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("not-existing")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("Hello World")
                            .replacement("Bye World")
                            .build()))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("value", "Hello World")));

            assertThat(result.toMap()).containsOnly(
                    entry("value", "Hello World")
            );
        }

        @Test
        void nullAttributeAsEmpty() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("not-existing")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                            .pattern("^$")
                            .replacement("one")
                            .build()))
                    .nullAsEmpty(true)
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("value", "Hello World")));

            assertThat(result.toMap()).containsOnly(
                    entry("value", "Hello World"),
                    entry("first", "one")
            );
        }

        @Test
        void multipleReplacementsWithDeprecation() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder().input("source")
                    .regex("Hello")
                    .replacement("Bye")
                    .replacements(Arrays.asList(
                            PatternAndReplacement.builder().pattern("Bye").replacement("test").build(),
                            PatternAndReplacement.builder().pattern("World").replacement("universe").build()
                    ))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("source", "Hello Hello World")));

            assertThat(result.toMap()).containsOnly(
                    entry("source", "Hello Hello World"),
                    entry("first", "test test universe")
            );
        }

        @Test
        void brokenAdditionalReplacements() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder().input("source")
                    .regex("Hello")
                    .replacement("Bye")
                    .replacements(Arrays.asList(
                            PatternAndReplacement.builder().pattern("Bye").replacement("test").build(),
                            PatternAndReplacement.builder().pattern("World").replacement("universe").build(),
                            PatternAndReplacement.builder().pattern(".*").replacement("$5").build()
                    ))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("first", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("source", "Hello Hello World")));

            assertThat(result.toMap()).containsOnly(
                    entry("source", "Hello Hello World"),
                    entry("first", "test test universe")
            );
        }

        @Test
        void keepNoMatchByDefault() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                                    .pattern("Hello World")
                                    .replacement("something")
                                    .build(),
                            PatternAndReplacement.builder()
                                    .pattern("Hello")
                                    .replacement("Bye")
                                    .build()))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("output", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("value", "Hello Earth")));

            assertThat(result.toMap()).containsOnly(
                    entry("value", "Hello Earth"),
                    entry("output", "Bye Earth")
            );
        }

        @Test
        void discardNoMatch() {
            BeaconAttributeSettings first = BeaconAttributeSettings.builder()
                    .input("value")
                    .replacements(Arrays.asList(PatternAndReplacement.builder()
                                    .pattern("Hello World")
                                    .replacement("something")
                                    .keepNoMatch(false)
                                    .build(),
                            PatternAndReplacement.builder()
                                    .pattern("Hello")
                                    .replacement("Bye")
                                    .build()))
                    .build();

            Map<String, BeaconAttributeSettings> beaconTags = new LinkedHashMap<>();
            beaconTags.put("output", first);

            EumServerConfiguration conf = new EumServerConfiguration();
            conf.setAttributes(new AttributeSettings());
            conf.getAttributes().setBeacon(beaconTags);

            RegexReplacementBeaconProcessor processor = new RegexReplacementBeaconProcessor(conf);

            Beacon result = processor.process(Beacon.of(ImmutableMap.of("value", "Hello Earth")));

            assertThat(result.toMap()).containsOnly(
                    entry("value", "Hello Earth")
            );
        }
    }
}
