package com.example.repro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Baseline: the native backend serves config-repo/demo-service.yml. Passes. */
@SpringBootTest(classes = ConfigServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.search-locations=classpath:/config-repo/"
        })
class NativeBackendTest {

    @LocalServerPort
    int port;

    @Test
    void clientGetsPropertiesFromOnProfileExpressionDocument() {
        try (ConfigurableApplicationContext client = ConfigClient.start(port, "env2-docker")) {
            assertThat(client.getEnvironment().getProperty("demo.datasource.driver-class-name"))
                    .isEqualTo("org.postgresql.Driver");
        }
    }

    @Test
    void serverReturnsUniqueNonEmptyPropertySources() {
        for (String profile : new String[] {"env2-docker", "default"}) {
            Environment environment = ConfigClient.fetch(port, profile);
            assertThat(ConfigClient.sourceNames(environment)).as("source names for " + profile).doesNotHaveDuplicates();
            assertThat(environment.getPropertySources()).as("sources for " + profile)
                    .allSatisfy(source -> assertThat(source.getSource()).isNotEmpty());
        }
    }
}
