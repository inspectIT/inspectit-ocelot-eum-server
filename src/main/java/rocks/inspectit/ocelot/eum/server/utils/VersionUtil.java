package rocks.inspectit.ocelot.eum.server.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class VersionUtil {

    /**
     * Version string of unknown versions
     */
    private static final String UNKNOWN = "UNKNOWN";

    /**
     * The file used to load the server's version
     */
    private static final String SERVER_VERSION_INFORMATION_FILE = "/eum-version.info";

    /**
     * The server's version
     */
    private static String serverVersion;

    /**
     * The date the server was built
     */
    private static String serverBuildDate;

    /**
     * The Boomerang.js version shipped with the server
     */
    private static String bommerangjsVersion;

    /**
     * The OpenTelemetry API version
     */
    private static String openTelemetryVersion;

    public static String getServerVersion() {
        if(serverVersion == null) readVersionInformation();
        return serverVersion;
    }

    public static String getServerBuildDate() {
        if(serverBuildDate == null) readVersionInformation();
        return serverBuildDate;
    }

    public static String getBoomerangVersion() {
        if(bommerangjsVersion == null) readVersionInformation();
        return bommerangjsVersion;
    }

    public static String getOpenTelemetryVersion() {
        if(openTelemetryVersion == null) readVersionInformation();
        return openTelemetryVersion;
    }

    /**
     * Loads the agent's version information from the {@link #SERVER_VERSION_INFORMATION_FILE} file.
     */
    private static void readVersionInformation() {
        try (InputStream inputStream = VersionUtil.class.getResourceAsStream(SERVER_VERSION_INFORMATION_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            serverVersion = reader.readLine();
            serverBuildDate = reader.readLine();
            bommerangjsVersion = reader.readLine();
            openTelemetryVersion = reader.readLine();
        } catch (Exception e) {
            log.warn("Could not read server version information file");
            serverVersion = UNKNOWN;
            serverBuildDate = UNKNOWN;
            bommerangjsVersion = UNKNOWN;
            openTelemetryVersion = UNKNOWN;
        }
    }
}
