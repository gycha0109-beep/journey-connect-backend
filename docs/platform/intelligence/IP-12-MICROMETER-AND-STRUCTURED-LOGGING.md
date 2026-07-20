# IP-12 Micrometer and Structured Logging

`MicrometerSearchShadowMetricSink` binds the bounded Search metric vocabulary under `journey.search.*`. Counters, timers and gauges catch registry failures. At most four low-cardinality tags are accepted; keys containing query, user, account, session, JWT, post, document, trace or correlation are rejected.

Structured logs include only effective startup state, sampling, allowlist count, bounded activation reason, completion category and drill result. Raw query, identity, JWT, session, account hash, request/response body and candidate payload are never logged.

The backend adds Spring Boot Actuator to provide the Micrometer registry. An external Prometheus/Grafana/APM destination is not added.
