# inspectit-ocelot-eum-server

[![EUM Server Tests](https://github.com/inspectIT/inspectit-ocelot-eum-server/actions/workflows/eumserver_test.yml/badge.svg?branch=main)](https://github.com/inspectIT/inspectit-ocelot-eum-server/actions/workflows/eumserver_test.yml)

This server provides Enduser Monitoring data by using the [OpenCensus](https://github.com/census-instrumentation/opencensus-java) toolkit.

## Metrics
The inspectit-ocelot server offers a backend for Javascript monitoring with [Boomerang](https://developer.akamai.com/tools/boomerang/docs/index.html).
Boomerang is a Javascript metrics agent, which is able to capture arbitrary customizable metrics. 
By injecting the following snipped in your webpage, all measured metrics are sent to the inspectit-ocelot-eum-server:
```javascript
<script src="boomerang-1.0.0.min.js"></script>
 <script src="plugins/rt.js"></script>
 <!-- any other plugins you want to include -->
 <script>
   BOOMR.init({
     beacon_url: "http://[inspectit-eum-server-url]:8080/beacon/"
   });
 </script>
```
Boomerang recommends to use an advanced injection, where the boomerang agent is loaded in an asynchronous way. 
For further information, please visit the [Boomerang documentation](https://developer.akamai.com/tools/boomerang/docs/index.html).

If enabled, the server exposes the metrics by using the [Prometheus exporter](https://github.com/census-instrumentation/opencensus-java/tree/master/exporters/stats/prometheus).
A tutorial on how to install Prometheus can be found [here](https://opencensus.io/codelabs/prometheus/#0).

## Server Setup
Before starting the server, please build the server by cloning the repository and executing the following command or download the [latest release](https://github.com/inspectIT/inspectit-ocelot/releases).
```bash
$ ./gradlew build
```
Start the server with the following command:
```bash
$ java -jar inspectit-ocelot-eum-{version}.jar
```
By default, the server is starting with the port `8080`. 
You can simply configure the port by using the Java property `-Dserver.port=[port]`:
```bash
$ java -Dserver.port=[port] -jar inspectit-ocelot-eum-0.0.1-SNAPSHOT.jar
```
Our server is delivered with a default configuration 
supporting the metrics `t_page`, `t_done`, `rt.tstart`, `rt.nstart` and `rt.end` of the Boomerang plugin [RT](https://developer.akamai.com/tools/boomerang/docs/BOOMR.plugins.RT.html).
In order to provide a custom configuration, please set the Java property `-Dspring.config.location=file:[path-to-config]`:

```bash
$ java -Dserver.port=[port] -Dspring.config.location=file:[path-to-config] -jar inspectit-ocelot-eum-0.0.1-SNAPSHOT.jar
```

## Configuration
The configuration file defines the mapping between the concrete Boomerang metric and a OpenCensus metric, as the following sample configuration file shows:
```yaml
inspectit-eum-server:
  definitions:
    page_ready_time:
      measure-type: LONG
      beacon-field: t_page
      unit: ms
      views:
        '[page_ready_time/SUM]': {aggregation: SUM}
        '[page_ready_time/COUNT]': {aggregation: COUNT}
    load_time:
      measure-type: LONG
      beacon-field: t_done
      unit: ms
      views:
        '[load_time/SUM]': {aggregation: SUM}
        '[load_time/COUNT]': {aggregation: COUNT}

    start_timestamp:
      measure-type: LONG
      beacon-field: rt.tstart
      unit: ms

    navigation_start_timestamp:
      measure-type: LONG
      beacon-field: rt.nstart
      unit: ms

    end_timestamp:
      measure-type: LONG
      beacon-field: rt.end
      unit: ms
      views:
        end_timestamp:
          aggregation: LAST_VALUE
          tags: {APPLICATION : true}
  tags:
    extra:
      APPLICATION: my-application
    beacon:
      URL: u
      OS: ua.plt
    global:
      - URL
      - OS
      - COUNTRY_CODE
  exporters:
    metrics:
      prometheus:
        enabled: ENABLED
        host: localhost
        port: 8888
    tracing:
      otlp:
        enabled: ENABLED
        protocol: grpc
        endpoint: localhost:4317

  security:
    enabled: false
    authorization-header: Authorization
    permitted-urls:
      - "/actuator/health"
      - "/boomerang/**"
    auth-provider:
      simple:
        enabled: false
        watch: true
        frequency: 60s
        token-directory: "" # Empty by default to force users to provide one
        default-file-name: "default-token-file.yaml"
```
##### Metrics Definition
A metric is defined through the following attributes:
* `name`: Defines the name of the metric. The name of the exposed view will have the used aggregation as suffix.
* `measure-type`: Can be either `LONG` or `DOUBLE`.
* `beacon-field`: The beacon key name, which is used as source of metric.
* `description`: Optional. Defines an additional description of the exposed metric.
* `unit`: The unit of the metric.
* `tag-keys`: Optional. Defines a list of tag keys, which are exposed with the current metric.
* `views`: A list of the views, which should be exposed. The aggregation can be either `SUM`, `COUNT`, `LAST_VALUE` or `HISTORGRAM`. For using `HISTOGRAM`, the field `bucket-boundaries` is mandatory.
* `bucket-boundaries`: Used for the `HISTOGRAM` aggregation, defines the bucket boundaries as list of Doubles.

##### Tags Definition
We distinguish between to different types of tags:
* `extra`- tags: Extra tags define tags, which are manually set in the configuration. The field `extra` holds a list of key-value mappings.
* `beacon`- tags: Beacon tags define tags, whose tag value is resolved by a beacon entry. The defined value of the `beacon` map will be resolved by using the provided beacon.
In order to provide selected tags to each measurement by default, tags can be defined as global. `global` holds a list of already defined tags, which will be then exposed for each measurement.

##### Automated Geolocation Detection
By using the tag `COUNTRY_CODE`, the geolocation of the requester is resolved by using the requester IP and the [GeoLite2 database](https://www.maxmind.com). If the IP cannot be resolved, the tag value will be empty.

##### Metrics Exporters

The inspectIT Ocelot EUM Server currently supports the following metrics exporters:

|Exporter |Supports run-time updates| Push / Pull |Enabled by default|
|---|---|---|---|
|[Prometheus Exporter](#prometheus-exporter)|Yes|Pull|No|
|[InfluxDB Exporter](#influxdb-exporter)|Yes|Push|No|
|[OTLP Exporter (Metrics)](#otlp-exporter-metrics) [[Homepage](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp/metrics)]|Yes|Push|No|

###### Prometheus Exporter
If `enabled` is set to `ENABLED`, the exporter exposes the metrics under 
```bash
http://[host]:[port]/metrics
```

Prometheus exporter exposes the metrics in Prometheus format.
When enabled, the EUM  starts a Prometheus HTTP server in parallel with your application.
The server is by default started on the port `8888` and metrics can then be accessed by visiting http://localhost:8888/metrics.

The following properties are nested properties below the `inspectit-eum-server.exporters.metrics.prometheus` property:

|Property | Default    | Description
|---|------------|---|
|`.enabled`| `DISABLED` |If `ENABLED` or `IF_CONFIGURED`, the inspectIT Ocelot agent will try to start the Prometheus metrics exporter and Prometheus HTTP server.
|`.host`| `0.0.0.0`  |The hostname or network address to which the Prometheus HTTP server should bind.
|`.port`| `8888`     |The port the Prometheus HTTP server should use.


###### InfluxDB Exporter
If enabled, metrics are pushed at a specified interval directly to a given InfluxDB v1.x instance.
To enable the InfluxDB Exporters, it is only required to specify the `endpoint`.

The InfluxDB exporter provides a special handling for counter and sum metrics which is enabled by default and can be disabled using the `counters-as-differences` option.
Usually, the absolute value of such counters is irrelevant when querying the data, instead you want to have the increase of it over a certain period of time.
With the `counters-as-differences` option enabled, counters are preprocessed before being exported.

Instead of writing the absolute value of each counter into the InfluxDB, only the increase since the last export will be written.
In addition, no value will be exported, if the counter has not changed since the last export.
This can greatly reduce the amount of data written into the InfluxDB, especially if the metrics are quite constant and won't change much.

The following properties are nested properties below the `inspectit.exporters.metrics.influx` property:


|Property | Default                                 | Description|
|---|-----------------------------------------|---|
|`.enabled`| `IF_CONFIGURED`                         |If `ENABLED` or `IF_CONFIGURED`, the agent will try to start the Influx exporter. If the url is not set, it will log a warning if set to `ENABLED` but fail silently if set to `IF_CONFIGURED`.|
|`.endpoint`| `null`                                  |The HTTP endpoint of the InfluxDB, e.g. `http://localhost:8086`.|
|`.user`| `null`                                  | The user to use for connecting to the InfluxDB, can not be empty.|
|`.password`| `null`                                  |The password to use for connecting to the InfluxDB, can be not be empty.|
|`.database`| `inspectit`                             | The InfluxDB database to which the metrics are pushed.|
|`.retention-policy`| `autogen`                               | The retention policy of the database to use for writing metrics.|
|`.create-database`| `true`                                  | If enabled, the database defined by the `database` property is automatically created on startup with an `autogen` retention policy if it does not exist yet.|
|`.export-interval`| refers to `inspectit.metrics.frequency` |Defines how often metrics are pushed to the InfluxDB.|
|<nobr>`.counters-as-differences`</nobr>| `true`                                  |Defines whether counters are exported using their absolute value or as the increase between exports|
|`buffer-size`| `40`                                    | In case the InfluxDB is not reachable, failed writes will be buffered and written on the next export. This value defines the maximum number of batches to buffer.|

###### OTLP Exporter (Metrics)


The OpenTelemetry Protocol (OTLP) exporters export the metrics to the desired endpoint at a specified interval.
To enable the OTLP exporters, it is only required to specify the `endpoint`.

The following properties are nested properties below the `inspectit-eum-server.exporters.metrics.otlp` property:

| Property                 | Default         | Description                                                                                                                                                                                                                  |
|--------------------------|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.enabled`               | `IF_CONFIGURED` | If `ENABLED` or `IF_CONFIGURED`, the inspectIT Ocelot agent will try to start the OTLP gRPC metrics exporter.                                                                                                                |
| `.endpoint`              | `null`          | Target to which the exporter is going to send metrics, e.g. `http://localhost:4317`                                                                                                                                          |
| `.protocol`              | `null`          | The transport protocol, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported protocols are `grpc` and `http/protobuf`.                                              |
| `.preferred-temporality` | `CUMULATIVE`    | The preferred output aggregation temporality, see [OTEL documentation](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md). Supported values are `CUMULATIVE` and `DELTA`.| 
| `.headers`               | `null`          | Key-value pairs to be used as headers associated with gRPC or HTTP requests, see [OTEL documentation](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md).|
| `.compression`           | `NONE`          | The compression method, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported compression methods are `gzip` and `none`.                                   |
| `.timeout`               | `10s`           | Maximum time the OTLP exporter will wait for each batch export, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/).                           |

##### Trace Exporters

Tracing exporters are responsible for passing the recorded tracing data to a corresponding storage.
The inspectIT Ocelot EUM Server currently supports the following trace exporters:

* [OTLP (Traces)](#otlp-exporter-traces) [[Homepage](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp/trace)]

###### General Trace Exporter Settings

These settings apply to all trace exporters and can set below the `inspectit-eum-server.exporters.tracing` property.

| Property        | Default                     | Description                                                                                                                                                               |
|-----------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.service-name` | `${inspectit.service-name}` | The value of this property will be used to identify the service a trace came from. Please note that changes of this property only take effect after restarting the agent. |

###### OTLP Exporter (Traces)

The OpenTelemetry Protocol (OTLP) exporters export the Traces in OTLP to the desired endpoint at a specified interval.
By default, the OTLP exporters are enabled but the URL `endpoint` needed for the exporter to actually start is set to `null`.

The following properties are nested properties below the `inspectit.exporters.tracing.otlp` property:

| Property       | Default         | Description                                                                                                                                                                                                        |
|----------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.enabled`     | `IF_CONFIGURED` | If `ENABLED` or `IF_CONFIGURED`, the inspectIT Ocelot agent will try to start the OTLP gRPC trace exporter.                                                                                                        |
| `.endpoint`    | `null`          | Target to which the exporter is going to send traces, e.g. `http://localhost:4317`                                                                                                                                 |
| `.protocol`    | `grpc`          | The transport protocol, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported protocols are `grpc` and `http/protobuf`.                                    |
| `.headers`     | `null`          | Key-value pairs to be used as headers associated with gRPC or HTTP requests, see [OTEL documentation](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md). |
| `.compression` | `NONE`          | The compression method, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported compression methods are `gzip` and `none`.                                   |
| `.timeout`     | `10s`           | Maximum time the OTLP exporter will wait for each batch export, see [OTEL documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/).                           |


###### Jaeger Exporter (removed)

Since version 2.6.2 the InspectIT EUM-Server does no longer support the Jaeger exporter.

##### Security
Currently, the EUM Server only supports a simple API token security concept. In future, additional authentication providers
will be supported.
Security can be enabled by changing spring `application.yml`, using system properties or environment variables.

```yaml
inspectit-eum-server:
  ....
  security:
    enabled: true
    authorization-header: Authorization
    permitted-urls:
      - "/actuator/health"
      - "/boomerang/**"
    auth-provider:
      # List of providers
      ....
```

###### Simple Token Provider
The simple token provider can be enabled from config file...

```yaml
inspectit-eum-server:
  ....
  security:
    ....
    auth-provider:
      simple:
        # Enable/Disable Provider
        enabled: true
        # Flag indicates if the directory should be watched for changes and tokens reloaded
        watch: true
        # How often directory should be watched for changes
        frequency: 60s
        # The directory where token files are stored. Empty by default to force users to provide one
        token-directory: ""
        # The name of the initial token file
        default-file-name: "default-token-file.yaml"
```
or via environment variables:
```bash
INSPECTIT_EUM_SERVER_SECURITY_AUTH_PROVIDER_SIMPLE_TOKEN_DIRECTORY=<my-directory>
INSPECTIT_EUM_SERVER_SECURITY_AUTH_PROVIDER_SIMPLE_ENABLED=TRUE;
INSPECTIT_EUM_SERVER_SECURITY_ENABLED=TRUE
```

The format for the token files is as follows:
```yaml
  # Identifies a token. E.g. you can document which application, person, organization, etc. knows about this token. It has no influence on security.
- name: "Name for first token"
  # The value of the token. If an HTTP-request contains this value (without opening and closing double quotes), access is allowed.
  token: "755f3e71-e43f-4715-bd26-b6e112fd30dd"
  # You may specify as many elements as you like
- name: "Name of other token"
  token: "any token value you like"
```


##### Build Docker Image Locally.

In order to build a docker image locally, the eum-server should be build locally and the resulting jar should be renamed
to ```inspectit-ocelot-eum-server.jar``` and copied to the ./docker directory

##### How to Release
To create a new release, you have to create a new git tag and push it on to GitHub. 
This Tag is the new version number of the release. Afterwards the release build will be automatically triggered.

Important tasks to check first are `dependencyUpdates` and `dependencyUpdates[Major|Minor]` for newer (patch, minor, major)
versions and `dependencyCheckAnalyze` for security issues in the used dependencies. 
