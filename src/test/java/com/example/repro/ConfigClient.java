package com.example.repro;

import java.util.List;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** A plain Spring Cloud Config client application, and raw HTTP access to the server under test. */
final class ConfigClient {

    private ConfigClient() {
    }

    static ConfigurableApplicationContext start(int serverPort, String profile) {
        return new SpringApplicationBuilder(ClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        // Nothing local: everything the client gets comes from the config server.
                        "spring.config.location=optional:classpath:/no-local-config/",
                        "spring.application.name=demo-service",
                        "spring.profiles.active=" + profile,
                        "spring.config.import=configserver:http://localhost:" + serverPort,
                        // The server jar on the test classpath disables the client by default.
                        "spring.cloud.config.enabled=true",
                        "spring.cloud.config.fail-fast=true",
                        "spring.main.banner-mode=off")
                .run();
    }

    /** GET /demo-service/{profile} from the server; the raw JSON is printed so it shows in the test output. */
    static Environment fetch(int serverPort, String profile) {
        RestClient http = RestClient.create("http://localhost:" + serverPort);
        System.out.println("GET /demo-service/" + profile + " -> "
                + http.get().uri("/demo-service/{profile}", profile).retrieve().body(String.class));
        return http.get().uri("/demo-service/{profile}", profile).retrieve().body(Environment.class);
    }

    static List<String> sourceNames(Environment environment) {
        return environment.getPropertySources().stream().map(PropertySource::getName).toList();
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientApp {
    }
}
