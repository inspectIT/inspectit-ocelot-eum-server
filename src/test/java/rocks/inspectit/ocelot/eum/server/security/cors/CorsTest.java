package rocks.inspectit.ocelot.eum.server.security.cors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = CorsTest.Initializer.class)
@DirtiesContext
public class CorsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String tokenDir = getClass().getClassLoader().getResource("security/simple-auth-provider").getFile();
            TestPropertyValues.of("inspectit-eum-server.security.enabled=true", "inspectit-eum-server.security.auth-provider.simple.enabled=true", "inspectit-eum-server.security.auth-provider.simple.token-directory=" + tokenDir, "inspectit-eum-server.security.auth-provider.simple.default-file-name=")
                    .applyTo(applicationContext);
        }
    }

    @Test
    public void successfulCorsForGetBeacons() {
        String endpoint = "/beacon";

        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://www.example.com");
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                endpoint, HttpMethod.OPTIONS, requestEntity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void successfulCorsForPostBeacons() {
        String endpoint = "/beacon";

        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://www.example.com");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                endpoint, HttpMethod.OPTIONS, requestEntity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void successfulCorsForSpans() {
        String endpoint = "/spans";

        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://www.example.com");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                endpoint, HttpMethod.OPTIONS, requestEntity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
