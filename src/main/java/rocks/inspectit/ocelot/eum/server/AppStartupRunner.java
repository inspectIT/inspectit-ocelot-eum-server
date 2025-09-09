package rocks.inspectit.ocelot.eum.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.utils.VersionUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
@Getter
@Component
public class AppStartupRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("> Version Information");
        log.info("\tVersion:             {}", VersionUtil.getServerVersion());
        log.info("\tBuild Date:          {}", VersionUtil.getServerBuildDate());
        log.info("\tBoomerangjs Version: {}", VersionUtil.getBoomerangVersion());
        log.info("\tOpenTelemetry Version: {}", VersionUtil.getOpenTelemetryVersion());
    }
}
