package rocks.inspectit.ocelot.eum.server.configuration.model;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.conversion.InspectitConfigConversionService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The bean configuration for the EUM server.
 */
@Configuration
public class BeanConfiguration {

    /**
     * Scheduled Executor service to be used by components for asynchronous tasks.
     */
    @Bean
    public ScheduledExecutorService scheduledExecutor() {
        return Executors.newScheduledThreadPool(4);
    }

    @Bean("conversionService")
    public ConversionService getConversionService() {
        return InspectitConfigConversionService.getInstance();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
