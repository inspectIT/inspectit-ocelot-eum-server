package rocks.inspectit.oce.eum.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.security.SecuritySettings;

import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired(required = false)
    private List<AuthenticationProvider> apiTokenAuthProviders = Collections.emptyList();

    /**
     * Adds all {@link AuthenticationProvider}s to the {@link AuthenticationManagerBuilder}.
     *
     * @param auth the {@link AuthenticationManagerBuilder} to use
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        if (configuration.getSecurity().isEnabled()) {
            apiTokenAuthProviders.forEach(auth::authenticationProvider);
        }
    }

    /**
     * Configures {@link HttpSecurity} if security is enabeld by
     * <ul>
     *  <li>Permitting all white listed urls in {@link SecuritySettings#getPermittedUrls()}</li>
     *  <li>Ensure all other request require authentication</li>
     *  <li>add {@link ApiTokenFilter} to filter chain</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} to modify
     *
     * @throws Exception In case of any error
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable();
        if (configuration.getSecurity().isEnabled()) {
            http.authorizeRequests()
                    .antMatchers(configuration.getSecurity().getPermittedUrls().toArray(new String[]{}))
                    .permitAll()
                    .anyRequest()
                    .authenticated()
                    .and()
                    .addFilterAt(new ApiTokenFilter(authenticationManagerBean(), configuration.getSecurity()
                            .getAuthorizationHeader()), BasicAuthenticationFilter.class);

        } else {
            http.authorizeRequests().anyRequest().permitAll();
        }
    }

    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

}
