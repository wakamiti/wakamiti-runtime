/*
 * Copyright (c) 2022-2026 Instituto Tecnológico de Informática (ITI)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.test;


import es.wakamiti.commons.junit.TargetTempDir;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class WakamitiServiceApplicationTest {

    @TargetTempDir
    Path tempDir;

    @DisplayName("Run writes effective configuration without environment overrides")
    @Test
    void testRunWritesEffectiveConfigurationWithoutEnvironmentOverrides() throws Exception {
        Path effectiveProperties = tempDir.resolve("effective.properties");
        Path runtimeProperties = writeProperties(tempDir.resolve("runtime.properties"), Map.of(
                "server.port", "0",
                "single", "ignored",
                "effective.properties", effectiveProperties.toString()
        ));
        AtomicReference<Config> startedConfig = new AtomicReference<>();

        invokeRun(runtimeProperties, Map.of(), startedConfig::set);

        Properties effective = loadProperties(effectiveProperties);
        assertThat(startedConfig.get()).isNotNull();
        assertThat(startedConfig.get().get("server.port").asString().orElseThrow()).isEqualTo("0");
        assertThat(effective.getProperty("server.port")).isEqualTo("0");
        assertThat(effective.getProperty("effective.properties")).isEqualTo(effectiveProperties.toString());
        assertThat(effective).doesNotContainKey("single");
    }

    @DisplayName("Run applies environment overrides before persisting effective configuration")
    @Test
    void testRunAppliesEnvironmentOverridesBeforePersistingEffectiveConfiguration() throws Exception {
        Path effectiveProperties = tempDir.resolve("nested").resolve("effective.properties");
        Path runtimeProperties = writeProperties(tempDir.resolve("runtime.properties"), Map.of(
                "server.port", "9999",
                "single", "ignored",
                "effective.properties", effectiveProperties.toString()
        ));
        AtomicReference<Config> startedConfig = new AtomicReference<>();

        invokeRun(runtimeProperties, Map.of("WAKAMITI_PORT", "0"), startedConfig::set);

        assertThat(startedConfig.get()).isNotNull();
        assertThat(startedConfig.get().get("server.port").asString().orElseThrow()).isEqualTo("0");
        assertThat(Files.exists(effectiveProperties)).isTrue();

        Properties effective = loadProperties(effectiveProperties);
        assertThat(effective.getProperty("server.port")).isEqualTo("0");
        assertThat(effective).doesNotContainKey("single");
    }

    @DisplayName("Run supports relative effective properties path")
    @Test
    void testRunSupportsRelativeEffectivePropertiesPath() throws Exception {
        Path relativeEffectiveProperties = Path.of("relative-effective.properties");
        Path runtimeProperties = writeProperties(tempDir.resolve("runtime.properties"), Map.of(
                "server.port", "0",
                "effective.properties", relativeEffectiveProperties.toString()
        ));
        Files.deleteIfExists(relativeEffectiveProperties);

        try {
            invokeRun(runtimeProperties, Map.of(), ignored -> { });

            assertThat(Files.exists(relativeEffectiveProperties)).isTrue();
        } finally {
            Files.deleteIfExists(relativeEffectiveProperties);
        }
    }

    @DisplayName("Save effective properties does nothing when no output path is configured")
    @Test
    void testSaveEffectivePropertiesDoesNothingWhenNoOutputPathIsConfigured() throws Exception {
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "server.port", "0",
                        "single", "ignored"
                ), "test-config").build())
                .build();

        invokeSaveEffectiveProperties(config);

        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @DisplayName("Run propagates external configuration errors")
    @Test
    void testRunPropagatesExternalConfigurationErrors() throws Exception {
        Path missingProperties = tempDir.resolve("missing.properties");
        AtomicBoolean starterCalled = new AtomicBoolean();

        assertThatThrownBy(() -> invokeRun(missingProperties, Map.of(), ignored -> starterCalled.set(true)))
                .isInstanceOf(ConfigException.class);
        assertThat(starterCalled.get()).isFalse();
    }

    private static void invokeSaveEffectiveProperties(
            Config config
    ) throws Exception {
        Method method = Class.forName("es.wakamiti.service.WakamitiServiceApplication")
                .getDeclaredMethod("saveEffectiveProperties", Config.class);
        method.setAccessible(true);
        method.invoke(null, config);
    }

    private static void invokeRun(
            Path runtimeProperties,
            Map<String, String> environment,
            Consumer<Config> starter
    ) throws Exception {
        Method method = Class.forName("es.wakamiti.service.WakamitiServiceApplication")
                .getDeclaredMethod("run", String.class, Map.class, Consumer.class);
        method.setAccessible(true);
        try {
            method.invoke(null, runtimeProperties.toString(), environment, starter);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static Path writeProperties(
            Path target,
            Map<String, String> values
    ) throws IOException {
        Properties properties = new Properties();
        values.forEach(properties::setProperty);
        try (var output = Files.newOutputStream(target)) {
            properties.store(output, "test");
        }
        return target;
    }

    private static Properties loadProperties(
            Path file
    ) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

}
