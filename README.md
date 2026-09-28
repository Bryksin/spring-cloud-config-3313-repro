# spring-cloud-config#3313 reproducer

Reproducer for [spring-cloud-config#3313](https://github.com/spring-cloud/spring-cloud-config/issues/3313):
`AwsS3EnvironmentRepository` serves a multi-document YAML with an `on-profile` **expression**
differently from the native/Git backend, and a config client ends up without the properties.

Versions: Spring Boot 4.1.1, Spring Cloud 2025.1.3 (spring-cloud-config 5.0.5), AWS SDK 2.55.6.

## Run

Needs JDK 17+ and Docker (LocalStack runs through Testcontainers).

```bash
./gradlew test
```

## What it does

One config file, [`src/test/resources/config-repo/demo-service.yml`](src/test/resources/config-repo/demo-service.yml):

```yaml
spring:
  config:
    activate:
      on-profile: env1-docker | env2-docker
demo:
  datasource:
    driver-class-name: org.postgresql.Driver
    host: db.example.com
```

It is served by two config servers, and each one runs the same two tests:

| Test class | Backend |
|---|---|
| `NativeBackendTest` | `native`, `search-locations=classpath:/config-repo/` |
| `AwsS3BackendTest` | stock `AwsS3EnvironmentRepository`. The same file is uploaded to the root of a LocalStack bucket (flat layout, no label) |

- `clientGetsPropertiesFromOnProfileExpressionDocument` starts a real config client
  (`spring.application.name=demo-service`, `spring.profiles.active=env2-docker`,
  `spring.config.import=configserver:...`) and reads `demo.datasource.driver-class-name`.
- `serverReturnsUniqueNonEmptyPropertySources` calls `GET /demo-service/env2-docker` and
  `GET /demo-service/default` (the client makes both calls) and checks that source names are unique and that no source is empty.

The only S3-specific setup is that the stock repository is built on a path-style `S3Client`, which
LocalStack needs and the `awss3` profile factory cannot configure. Nothing about how the
repository reads or names property sources is changed.

## Result

```
AwsS3BackendTest > clientGetsPropertiesFromOnProfileExpressionDocument() FAILED
    expected: "org.postgresql.Driver"
     but was: null

AwsS3BackendTest > serverReturnsUniqueNonEmptyPropertySources() FAILED
    [source names for env2-docker]
    Found duplicate(s):
      ["s3:demo-service"]

NativeBackendTest > clientGetsPropertiesFromOnProfileExpressionDocument() PASSED
NativeBackendTest > serverReturnsUniqueNonEmptyPropertySources() PASSED
```

Server responses (these are also printed to each test's stdout):

**native** `GET /demo-service/env2-docker`
```json
{"name":"demo-service","profiles":["env2-docker"],"label":null,"version":null,"state":null,
 "propertySources":[{"name":"classpath:/config-repo/demo-service.yml","source":{
   "spring.config.activate.on-profile":"env1-docker | env2-docker",
   "demo.datasource.driver-class-name":"org.postgresql.Driver","demo.datasource.host":"db.example.com"}}]}
```

**S3** `GET /demo-service/env2-docker`
```json
{"name":"demo-service","profiles":["env2-docker"],"label":null,"version":null,"state":null,
 "propertySources":[{"name":"s3:demo-service","source":{}},
                    {"name":"s3:demo-service","source":{
   "demo.datasource.driver-class-name":"org.postgresql.Driver","demo.datasource.host":"db.example.com",
   "spring.config.activate.on-profile":"env1-docker | env2-docker"}}]}
```

The S3 response has two sources with the same name, and the first one is empty. `MutablePropertySources` keeps only one
source per name, so the client keeps the empty one and `driver-class-name` is `null`.

**Expected:** the S3 backend gives the same client result as native/Git for the same file.

## Docker-outside-of-Docker note

If Docker runs on the host of a devcontainer (the socket is mounted in), point Testcontainers at the
host: `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ./gradlew test`.
