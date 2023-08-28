package rocks.inspectit.oce.eum.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.security.SecuritySettings;

import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private EumServerConfiguration configuration;
    @Autowired(required = false)
    private List<AuthenticationProvider> apiTokenAuthProviders = Collections.emptyList();

    /**
     * Adds all {@link AuthenticationProvider}s to the {@link AuthenticationManagerBuilder}.
     *
     * @param auth the {@link AuthenticationManagerBuilder} to use
     */
    @Autowired
    protected void configure(AuthenticationManagerBuilder auth) {
        if (configuration.getSecurity().isEnabled()) {
            apiTokenAuthProviders.forEach(auth::authenticationProvider);
        }
    }

    /**
     * Configures {@link HttpSecurity} if security is enabled by
     * <ul>
     *  <li>Permitting all white listed urls in {@link SecuritySettings#getPermittedUrls()}</li>
     *  <li>Ensure all other requests require authentication</li>
     *  <li>add {@link ApiTokenFilter} to filter chain</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} to modify
     * @throws Exception In case of any error
     */
    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable);
        if (configuration.getSecurity().isEnabled()) {
            http.authorizeHttpRequests(
                    authz -> authz
                            .requestMatchers(configuration.getSecurity().getPermittedUrls().toArray(new String[]{}))
                            .permitAll()
                            .anyRequest()
                            .authenticated()
            ).addFilterAt(
                    new ApiTokenFilter(authenticationManager, configuration.getSecurity().getAuthorizationHeader()),
                    BasicAuthenticationFilter.class
            );
        }
        else {
            http.authorizeHttpRequests(
                    authz -> authz.anyRequest().permitAll()
            ).httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }
}
