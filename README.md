# inspectIT Ocelot EUM-server

[![EUM Server Tests](https://github.com/inspectIT/inspectit-ocelot-eum-server/actions/workflows/eumserver_test.yml/badge.svg?branch=main)](https://github.com/inspectIT/inspectit-ocelot-eum-server/actions/workflows/eumserver_test.yml)

This server provides real user monitoring data by using [OpenTelemetry](https://opentelemetry.io/docs/languages/java/).

For an extensive documentation, please check out https://inspectit.github.io/inspectit-ocelot/docs/enduser-monitoring/enduser-monitoring-server.

## Metrics

The inspectIT Ocelot EUM-server offers a backend for JavaScript monitoring with [Boomerang](https://akamai.github.io/boomerang/akamai/).
Boomerang is a JavaScript metrics agent, which is able to capture arbitrary customizable metrics. 
By injecting the following snipped in your webpage, all measured metrics are sent to the EUM-server:
```javascript
<script src="boomerang-1.0.0.min.js"></script>
<script src="plugins/rt.js"></script>
<!-- any other Boomerang plugins you want to include -->

<script>
  BOOMR.init({
    beacon_url: "http://[inspectit-eum-server-url]:8080/beacon/"
  });
</script>
```
Boomerang recommends to use an advanced injection, where the boomerang agent is loaded in an **asynchronous** way. 
For further information, please visit the [Boomerang documentation](https://akamai.github.io/boomerang/akamai/#toc8__anchor).

If enabled, the server exposes the metrics via [metrics exporter](#metrics-exporters).

## Server Setup

You can download the [latest release](https://github.com/inspectIT/inspectit-ocelot-eum-server/releases) in this repository.
Or build the server by cloning the repository and executing the following command:

```bash
$ ./gradlew assemble
```

Start the server with the following command:
```bash
$ java -jar inspectit-ocelot-eum-{version}.jar
```

By default, the server will start with the port `8080`. 
You can simply configure the port by using the Java property `-Dserver.port=[port]`:
```bash
$ java -Dserver.port=[port] -jar inspectit-ocelot-eum-0.0.1-SNAPSHOT.jar
```

Our server is delivered with a default configuration supporting the metrics 
`t_page`, `t_done`, `rt.tstart`, `rt.nstart`, `rt.end` and `restiming` of the Boomerang [RT](https://akamai.github.io/boomerang/akamai/BOOMR.plugins.RT.html) plugin.
In order to provide a custom configuration, please set the Java property `-Dspring.config.location=file:[path-to-config]`:

```bash
$ java -Dserver.port=[port] -Dspring.config.location=file:[path-to-config] -jar inspectit-ocelot-eum-0.0.1-SNAPSHOT.jar
```

## Configuration

The configuration file defines the mapping between the concrete Boomerang metric and a OpenTelemetry metric, 
as the following sample configuration file shows:

```yaml
inspectit-eum-server:
  definitions:
    
    resource_time:
      enabled: true
      description: Response end time of the resource loading
      instrument-type: HISTOGRAM
      value-type: DOUBLE
      value-expression: "{restiming}"
      unit: ms
      views:
        resource_time:
          aggregation: HISTOGRAM
          attributes:
            initiatorType: true
            cached: true
            crossOrigin: true

    page_ready_time:
      instrument-type: HISTOGRAM
      value-type: LONG
      value-expression: "{t_page}"
      unit: ms
      views:
        page_ready_time: { aggregation: HISTOGRAM }

    load_time:
      instrument-type: HISTOGRAM
      value-type: LONG
      value-expression: "{t_done}"
      beacon-requirements:
        - field: rt.quit
          requirement: NOT_EXISTS
      unit: ms
      views:
        load_time: { aggregation: HISTOGRAM }

    calc_load_time:
      instrument-type: HISTOGRAM
      value-type: LONG
      value-expression: "{rt.end} - {rt.tstart}"
      beacon-requirements:
        - field: rt.quit
          requirement: NOT_EXISTS
      unit: ms
      views:
        calc_load_time: { aggregation: HISTOGRAM }

    start_timestamp:
      instrument-type: GAUGE
      value-type: LONG
      value-expression: "{rt.tstart}"
      unit: ms
      views:
        start_timestamp:
          aggregation: LAST_VALUE
          attributes: { APPLICATION: true }

    navigation_start_timestamp:
      instrument-type: GAUGE
      value-type: LONG
      value-expression: "{rt.nstart}"
      unit: ms
      views:
        navigation_start_timestamp:
          aggregation: LAST_VALUE
          attributes: { APPLICATION: true }

    end_timestamp:
      instrument-type: GAUGE
      value-type: LONG
      value-expression: "{rt.end}"
      unit: ms
      views:
        end_timestamp:
          aggregation: LAST_VALUE
          attributes: { APPLICATION: true }
          
  attributes:
    extra:
      APPLICATION: my-application
    beacon:
      URL: 
        input: u
        null-as-empty: true
      OS: 
        input: ua.plt
        null-as-empty: true
    global:
      - URL
      - OS
      - COUNTRY_CODE
  
  exporters:
    metrics:
      otlp:
        enabled: ENABLED
        protocol: grpc
        endpoint: localhost:4317
    tracing:
      otlp:
        enabled: ENABLED
        protocol: grpc
        endpoint: localhost:4317
```

#### Metrics Definition

A metric is defined through the following properties:

* `name`: Defines the name of the metric.
* `instrument-type`: The OpenTelemetry instrument to be used. Can be either `COUNTER`, `UP_DOWN_COUNTER`, `GAUGE` or `HISTOGRAM`.
* `value-type`: Can be either `LONG` or `DOUBLE`.
* `beacon-field`: The beacon key name, which is used as source of metric.
* `unit`: The unit of the metric.
* `description`: Defines an additional description of the exposed metric (optional).
* `views`: A list of the views, which should be exposed. If no view are specified, OpenTelemetry will create default views automatically.

#### View Definition

A view is defined through the following properties:

* `name`: Defines the name of the view.
* `aggregation`: There OTel aggregations (`SUM`, `LAST_VALUE`, `HISTORGRAM`; `EXPONENTIAL_HISTOGRAM`) as well as custom aggregations (`QUANTILES`, `SMOOTHED_AVERAGE`).
* `attributes`: Defines a list of attribute keys, which are exposed with the current metric.
* `bucket-boundaries`: Used for the `HISTOGRAM` aggregation, defines the bucket boundaries as list of doubles.
* `cardinality-limit`: Defines the maximum amount of unique combinations of attributes for this view.
* `max-buckets`: Used for `EXPONENTIAL_HISTOGRAM`, defines the max number of positive buckets and negative buckets
* `max-scale`: Used for `EXPONENTIAL_HISTOGRAM`, defines the maximum and initial scale.
* `percentiles`: Used for `QUANTILES`, defines which percentiles shall be captured as list of doubles.
* `drop-upper`: Used for `SMOOTHED_AVERAGE`, how many metrics in the upper range shall be dropped (0-1)
* `drop-lower`: Used for `SMOOTHED_AVERAGE`, how many metrics in the lower range shall be dropped (0-1)
* `time-window`: Used for `QUANTILES` & `SMOOTHED_AVERAGE` to group by metrics.
* `max-buffered-points`: Used for `QUANTILES` & `SMOOTHED_AVERAGE`, to set a maximum amount of metrics per time-window.
* `description`: Defines an additional description of the exposed view (optional).

#### Attributes Definition

We distinguish between to different types of tags:
* `extra`- tags: Extra tags define tags, which are manually set in the configuration. The field `extra` holds a list of key-value mappings.
* `beacon`- tags: Beacon tags define tags, whose tag value is resolved by a beacon entry. The defined value of the `beacon` map will be resolved by using the provided beacon.
In order to provide selected tags to each measurement by default, tags can be defined as global. `global` holds a list of already defined tags, which will be then exposed for each measurement.

#### Automated Geolocation Detection

By using the tag `COUNTRY_CODE`, the geolocation of the requester is resolved by using the requester IP 
and the [GeoLite2 database](https://www.maxmind.com). If the IP cannot be resolved, the tag value will be empty.

### Metrics Exporters

The inspectIT Ocelot EUM Server currently supports the following metrics exporters:

| Exporter                                                                                                                         | Push / Pull | Enabled by default |
|----------------------------------------------------------------------------------------------------------------------------------|-------------|--------------------|
| [Prometheus Exporter](#prometheus-exporter)                                                                                      | Pull        | No                 |
| [OTLP Exporter (Metrics)](#otlp-exporter-metrics) [[Homepage](https://opentelemetry.io/docs/languages/java/sdk/#otlp-exporters)] | Push        | No                 |

##### Prometheus Exporter

If `enabled` is set to `ENABLED`, the exporter exposes the metrics under 
```bash
http://[host]:[port]/metrics
```

Prometheus exporter exposes the metrics in Prometheus format.
When enabled, the EUM-server starts a Prometheus HTTP server in parallel with your application.
The server is by default started on the port `8888` and metrics can then be accessed by visiting http://localhost:8888/metrics.

The following properties are nested properties below the `inspectit-eum-server.exporters.metrics.prometheus` property:

| Property   | Default    | Description                                                                                                                   |
|------------|------------|-------------------------------------------------------------------------------------------------------------------------------|
| `.enabled` | `DISABLED` | If `ENABLED` or `IF_CONFIGURED`, the EUM-server will try to start the Prometheus metrics exporter and Prometheus HTTP server. |
| `.host`    | `0.0.0.0`  | The hostname or network address to which the Prometheus HTTP server should bind.                                              |
| `.port`    | `8888`     | The port the Prometheus HTTP server should use.                                                                               |

##### OTLP Exporter (Metrics)

The OpenTelemetry Protocol (OTLP) exporters export the metrics to the desired endpoint at a specified interval.
To enable the OTLP exporters, it is only required to specify the `endpoint`.

The following properties are nested properties below the `inspectit-eum-server.exporters.metrics.otlp` property:

| Property                 | Default         | Description                                                                                                                                                                                                        |
|--------------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.enabled`               | `IF_CONFIGURED` | If `ENABLED` or `IF_CONFIGURED`, the EUM-server will try to start the OTLP metrics exporter.                                                                                                                       |
| `.endpoint`              | `null`          | Target to which the exporter is going to send metrics, e.g. `http://localhost:4317`                                                                                                                                |
| `.protocol`              | `null`          | The transport protocol, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported protocols are `grpc` and `http/protobuf`.                                    |
| `.preferred-temporality` | `CUMULATIVE`    | The preferred output aggregation temporality, see [OTel documentation](https://opentelemetry.io/docs/languages/java/configuration/). Supported values are `CUMULATIVE` and `DELTA`.                                | 
| `.headers`               | `null`          | Key-value pairs to be used as headers associated with gRPC or HTTP requests, see [OTel documentation](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md). |
| `.compression`           | `NONE`          | The compression method, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported compression methods are `gzip` and `none`.                                   |
| `.timeout`               | `10s`           | Maximum time the OTLP exporter will wait for each batch export, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/).                                                |

##### InfluxDB Exporter (removed)

Since version 3.0.0 the InspectIT Ocelot EUM-Server does no longer support the InfluxDB exporter.
Please use the OTLP Exporter instead.

## Traces

The inspectIT Ocelot EUM-server also offers a backend for JavaScript monitoring with the [OpenTelemetry-Boomerang-Plugin](https://github.com/inspectIT/boomerang-opentelemetry-plugin). 
The self-made plugin allows to record traces via Boomerang and send them to the EUM-server. 
You can include the plugin as any other boomerang plugin.

If enabled, the server exposes the traces via [trace exporters](#trace-exporters).

### Trace Exporters

Trace exporters are responsible for passing the recorded tracing data to a corresponding storage.
The inspectIT Ocelot EUM Server currently supports the following trace exporters:

* [OTLP (Traces)](#otlp-exporter-traces) [[Homepage](https://opentelemetry.io/docs/languages/java/sdk/#otlp-exporters)]

Furthermore, you can configure all exporters to mask IP addresses within attributes by setting
`inspectit-eum-server.exporters.tracing.mask-span-ip-addresses` to `true`.

##### OTLP Exporter (Traces)

The OpenTelemetry Protocol (OTLP) exporters export the Traces in OTLP to the desired endpoint at a specified interval.
By default, the OTLP exporters are enabled but the URL `endpoint` needed for the exporter to actually start is set to `null`.

The following properties are nested properties below the `inspectit-eum-server.exporters.tracing.otlp` property:

| Property       | Default         | Description                                                                                                                                                                                                        |
|----------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.enabled`     | `IF_CONFIGURED` | If `ENABLED` or `IF_CONFIGURED`, the EUM-server will try to start the OTLP gRPC trace exporter.                                                                                                                    |
| `.endpoint`    | `null`          | Target to which the exporter is going to send traces, e.g. `http://localhost:4317`                                                                                                                                 |
| `.protocol`    | `grpc`          | The transport protocol, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported protocols are `grpc` and `http/protobuf`.                                    |
| `.headers`     | `null`          | Key-value pairs to be used as headers associated with gRPC or HTTP requests, see [OTel documentation](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md). |
| `.compression` | `NONE`          | The compression method, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/). Supported compression methods are `gzip` and `none`.                                   |
| `.timeout`     | `10s`           | Maximum time the OTLP exporter will wait for each batch export, see [OTel documentation](https://opentelemetry.io/docs/reference/specification/protocol/exporter/).                                                |


##### Jaeger Exporter (removed)

Since version 2.6.2 the InspectIT EUM-Server does no longer support the Jaeger exporter.
Please use the OTLP Exporter instead.

### Security
Currently, the EUM Server only supports a simple API token security concept. 
In the future, additional authentication providers will be supported.
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

##### Simple Token Provider
The simple token provider can be enabled from config file:

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

### Build Docker Image Locally.

In order to build a docker image locally, the eum-server should be built locally and the resulting jar should be renamed
to ```inspectit-ocelot-eum-server.jar``` and copied to the ./docker directory

###
How to Release
To create a new release, you have to create a new git tag and push it on to GitHub. 
This Tag is the new version number of the release. Afterward the release build will be automatically triggered.

Important tasks to check first are `dependencyUpdates` and `dependencyUpdates[Major|Minor]` for newer (patch, minor, major)
versions and `dependencyCheckAnalyze` for security issues in the used dependencies. 
