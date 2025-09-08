package rocks.inspectit.ocelot.eum.server.beacon.processor;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.AttributeSettings;
import rocks.inspectit.ocelot.eum.server.utils.GeolocationResolver;
import rocks.inspectit.ocelot.eum.server.utils.IPUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test of {@link CountryCodeBeaconProcessorTest}
 */
@ExtendWith(MockitoExtension.class)
class CountryCodeBeaconProcessorTest {

    @InjectMocks
    CountryCodeBeaconProcessor preProcessor;

    @Mock
    private GeolocationResolver geolocationResolver;

    @Spy
    private IPUtils ipUtils = new IPUtils();

    @Mock
    private EumServerConfiguration configuration;

    public static final String DEFAULT_IP_ADDRESS = "10.10.10.10";

    private AttributeSettings attributeSettings = new AttributeSettings();

    private Beacon beacon;

    private MockHttpServletRequest request;

    @BeforeEach
    private void initializeBeacon() {
        beacon = Beacon.of(ImmutableMap.of("dummyMetric", "dummyValue"));
        request = new MockHttpServletRequest();
        request.setRemoteAddr(DEFAULT_IP_ADDRESS);
        ServletRequestAttributes requestAttributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(requestAttributes);
    }

    @Nested
    public class Process {

        @Test
        public void addEmptyCountryCode() {
            when(geolocationResolver.getCountryCode(DEFAULT_IP_ADDRESS)).thenReturn("");
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("");
        }

        @Test
        public void addGeoIPDBCountryCode() {
            when(geolocationResolver.getCountryCode(DEFAULT_IP_ADDRESS)).thenReturn("DE");
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.contains(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isTrue();
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("DE");
        }

        @Test
        public void addCustomLabelIPMatches() {
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_1", Arrays.asList(new String[]{"10.10.10.10"}));
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("CUSTOM_TAG_1");
        }

        @Test
        public void addGeoIPDBCountryCodeSecondPrio1() {
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_1", Arrays.asList(new String[]{"10.10.10.11"}));

            when(geolocationResolver.getCountryCode(DEFAULT_IP_ADDRESS)).thenReturn("DE");
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("DE");
        }

        @Test
        public void addGeoIPDBCountryCodeSecondPrio2() {
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_1", Arrays.asList(new String[]{"10.11.0.0/16"}));

            when(geolocationResolver.getCountryCode(DEFAULT_IP_ADDRESS)).thenReturn("DE");
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("DE");
        }

        @Test
        public void addCustomLabelCIDRMatches1() {
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_1", Arrays.asList(new String[]{"10.10.0.0/16"}));
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("CUSTOM_TAG_1");
        }

        @Test
        public void addCustomLabelCIDRMatches2() {
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_1", Arrays.asList(new String[]{"10.11.0.0/16"}));
            attributeSettings.getCustomIPMapping().put("CUSTOM_TAG_2", Arrays.asList(new String[]{"10.10.0.0/16"}));
            when(configuration.getTags()).thenReturn(attributeSettings);

            Beacon b = preProcessor.process(beacon);
            assertThat(b.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("CUSTOM_TAG_2");
        }

        @Test
        public void respectForwardedHeader() {
            when(geolocationResolver.getCountryCode("10.0.0.0")).thenReturn("DE");
            when(configuration.getTags()).thenReturn(attributeSettings);
            request.addHeader("X-Forwarded-For", "10.0.0.0");

            Beacon result = preProcessor.process(beacon);

            assertThat(result.contains(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isTrue();
            assertThat(result.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("DE");
        }

        @Test
        public void respectForwardedHeader_multiValue() {
            when(geolocationResolver.getCountryCode("10.0.0.0")).thenReturn("DE");
            when(configuration.getTags()).thenReturn(attributeSettings);
            request.addHeader("X-Forwarded-For", new String[]{"10.0.0.0", "10.0.0.1"});

            Beacon result = preProcessor.process(beacon);

            assertThat(result.contains(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isTrue();
            assertThat(result.get(CountryCodeBeaconProcessor.TAG_COUNTRY_CODE)).isEqualTo("DE");
        }
    }
}
