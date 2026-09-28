package com.example.repro;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same config-repo/demo-service.yml, uploaded to the root of an S3 bucket and served by the
 * stock {@link AwsS3EnvironmentRepository}. Fails: the client ends up without the properties.
 */
@Testcontainers
@SpringBootTest(classes = {ConfigServerApplication.class, AwsS3BackendTest.S3Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.awss3.bucket=" + AwsS3BackendTest.BUCKET)
class AwsS3BackendTest {

    static final String BUCKET = "config-repo";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4")).withServices("s3");

    @LocalServerPort
    int port;

    @BeforeAll
    static void uploadConfigRepo() {
        try (S3Client s3 = s3Client()) {
            s3.createBucket(b -> b.bucket(BUCKET));
            s3.putObject(b -> b.bucket(BUCKET).key("demo-service.yml"),
                    Path.of("src/test/resources/config-repo/demo-service.yml"));
        }
    }

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

    static S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(LOCALSTACK.getEndpoint().toString()))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                // LocalStack needs path-style addressing.
                .forcePathStyle(true)
                .build();
    }

    /**
     * The stock repository, only built on a path-style client for LocalStack (the built-in
     * {@code awss3} profile factory cannot set path-style access). Nothing about how the
     * repository reads or names property sources is changed.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class S3Config {

        @Bean
        AwsS3EnvironmentRepository awsS3EnvironmentRepository(AwsS3EnvironmentProperties properties,
                                                              ConfigServerProperties serverProperties) {
            return new AwsS3EnvironmentRepository(s3Client(), properties, serverProperties);
        }
    }
}
