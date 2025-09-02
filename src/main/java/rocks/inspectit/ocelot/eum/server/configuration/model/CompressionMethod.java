package rocks.inspectit.ocelot.eum.server.configuration.model;

/**
 * The compression method used in OTLP exporters.
 */
public enum CompressionMethod {
    GZIP, NONE;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

