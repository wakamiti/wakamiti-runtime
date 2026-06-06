/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service;


import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.server.Server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;


/**
 * Main entry point for the Wakamiti service.
 * <p>
 * This class is responsible for initializing the Helidon server, configuring the system properties,
 * and managing the persistence of the effective configuration.
 */
public class WakamitiServiceApplication {

    /**
     * Service identifier name, mainly used for the logging system.
     */
    public static final String NAME = "service.wakamiti";

    private WakamitiServiceApplication() {
        // Private constructor to prevent instantiation of this utility class.
    }

    /**
     * Main method that starts the application.
     * <p>
     * Follows this flow:
     * 1. Configures system properties required for Helidon.
     * 2. Loads configuration from multiple sources (system, external file, classpath).
     * 3. Applies environment variable mappings if they exist.
     * 4. Saves a copy of the resulting properties ("effective properties").
     * 5. Starts the Helidon MicroProfile server.
     */
    static void main() {
        try {
            var sources = new LinkedList<>(List.of(
                    ConfigSources.systemProperties(),
                    ConfigSources.file(System.getProperty("wakamiti.properties.file")).build(),
                    ConfigSources.classpath("application.yml").build()
            ));
            Config config = Config.builder().sources(sources).build();

            Map<String, String> overrides = getEnvironmentOverrides(config);
            if (!overrides.isEmpty()) {
                sources.addFirst(ConfigSources.create(overrides, "env-mapped-overrides").build());
                config = Config.builder().sources(sources).build();
            }

            saveEffectiveProperties(config);
            Server.builder().config(config).build().start();
        } catch (Exception ex) {
            System.err.println("The Wakamiti Service application has failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Extracts environment variable mappings defined in the configuration.
     * <p>
     * Searches in the 'envs' section of the configuration and, if the corresponding
     * environment variable exists, creates a map to override the internal property.
     */
    private static Map<String, String> getEnvironmentOverrides(
            Config config
    ) {
        return config.get("envs").detach().asMap().orElse(Map.of()).entrySet().stream().filter(
                e -> System.getenv(e.getKey()) != null).collect(
                Collectors.toMap(Map.Entry::getValue, e -> System.getenv(e.getKey())));
    }

    /**
     * Saves the current properties to a physical file defined by 'effective.properties'.
     * <p>
     * This makes it easier for administrators and developers to know exactly what
     * values are being used at runtime.
     */
    private static void saveEffectiveProperties(
            Config config
    ) throws IOException {
        Properties props = new Properties();
        config.asMap().orElse(Map.of()).forEach((key, value) -> {
            if (key.contains(".")) {
                props.put(key, value);
            }
        });

        String effectivePath = props.getProperty("effective.properties");
        if (effectivePath == null) {
            return;
        }

        Path path = Path.of(effectivePath);
        if (path.getParent() != null && Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            props.store(out, "Wakamiti Effective Configuration");
        }
    }
}
