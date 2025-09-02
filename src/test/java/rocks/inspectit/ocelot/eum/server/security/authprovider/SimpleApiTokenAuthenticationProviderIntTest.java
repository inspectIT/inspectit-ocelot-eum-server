package rocks.inspectit.ocelot.eum.server.security.authprovider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = SimpleApiTokenAuthenticationProviderIntTest.Initializer.class)
@DirtiesContext
public class SimpleApiTokenAuthenticationProviderIntTest {

    @Autowired
    private TestRestTemplate rest;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String tokenDir = getClass().getClassLoader().getResource("security/simple-auth-provider").getFile();
            TestPropertyValues.of("inspectit-eum-server.security.enabled=true", "inspectit-eum-server.security.auth-provider.simple.enabled=true", "inspectit-eum-server.security.auth-provider.simple.token-directory=" + tokenDir, "inspectit-eum-server.security.auth-provider.simple.default-file-name=")
                    .applyTo(applicationContext);
        }
    }

    @Nested
    public class Authorized {

        @Test
        void authorizedRequest() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "dummy1");

            HttpEntity<String> request = new HttpEntity<>("Let me in", headers);
            ResponseEntity<Void> result = rest.postForEntity("/", request, Void.class);

            // Simply check if Request was successful
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    public class Forbidden {

        @Test
        void forbiddenRequest() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "invalid-token");

            HttpEntity<String> request = new HttpEntity<>("Let me in", headers);
            ResponseEntity<Void> result = rest.postForEntity("/", request, Void.class);

            // Simply check if Request was successful
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

}

