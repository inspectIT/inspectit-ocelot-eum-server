package rocks.inspectit.ocelot.eum.server.security.authprovider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.security.ApiTokenAuthentication;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleApiTokenAuthenticationProviderTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    EumServerConfiguration configuration;

    @Mock
    ScheduledExecutorService executorService;

    @InjectMocks
    SimpleApiTokenAuthenticationProvider authenticationProvider;

    private static HashMap<String, Object> defaultTokens() {
        HashMap<String, Object> tokens = new HashMap<>();
        tokens.put("dummy1", new SimpleApiTokenAuthenticationProvider.TokenPrincipal("dummy1 app", "dummy1"));
        tokens.put("dummy2", new SimpleApiTokenAuthenticationProvider.TokenPrincipal("dummy2 app", "dummy2"));
        return tokens;
    }

    private void configurationReturnsDefaultTokensDirectory() {
        File file = new File(getClass().getClassLoader().getResource("security/simple-auth-provider").getFile());

        when(configuration.getSecurity()
                .getAuthProvider()
                .getSimple()
                .getTokenDirectory()).thenReturn(file.getAbsolutePath());
    }

    @Nested
    class Authentication {

        @Test
        void authenticationSuccess() {
            configurationReturnsDefaultTokensDirectory();
            authenticationProvider.init();

            ApiTokenAuthentication unauthenticated = new ApiTokenAuthentication("dummy1");

            org.springframework.security.core.Authentication authenticated = authenticationProvider.authenticate(unauthenticated);

            assertThat(authenticated).isNotEqualTo(unauthenticated);
            assertThat(authenticated.isAuthenticated()).isTrue();
        }

        @Test
        void authenticationFailure() {
            configurationReturnsDefaultTokensDirectory();
            authenticationProvider.init();

            ApiTokenAuthentication unauthenticated = new ApiTokenAuthentication("unkowntoken");

            org.springframework.security.core.Authentication authenticated = authenticationProvider.authenticate(unauthenticated);

            assertThat(authenticated).isEqualTo(unauthenticated);
            assertThat(authenticated.isAuthenticated()).isFalse();
        }
    }

    @Nested
    class LoadTokens {

        @Test
        void loadExistingTokens() {
            configurationReturnsDefaultTokensDirectory();

            authenticationProvider.init();
            Object o = ReflectionTestUtils.getField(authenticationProvider, "knownTokens");
            assertThat(o).isEqualTo(defaultTokens());

        }

        @Test
        void createTokenDirectoryAndCreateInitialToken(@TempDir File tempTokenDir) {
            String tokenDir = tempTokenDir.getAbsolutePath() + File.separator + "tokens";
            when(configuration.getSecurity().getAuthProvider().getSimple().getTokenDirectory()).thenReturn(tokenDir);

            when(configuration.getSecurity()
                    .getAuthProvider()
                    .getSimple()
                    .getDefaultFileName()).thenReturn("test-tokens.yaml");

            authenticationProvider.init();

            String expectedFile = tokenDir + File.separator + "test-tokens.yaml";

            assertThat(new File(expectedFile).exists()).isEqualTo(true);

        }
    }
}
