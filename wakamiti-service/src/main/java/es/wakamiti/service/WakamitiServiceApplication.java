/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service;


import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.Source;
import io.helidon.microprofile.server.Server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class WakamitiServiceApplication {

    public static final String NAME = "service.wakamiti";

    private WakamitiServiceApplication() {
        // Private constructor
    }

    static void main() {
        // Fix for the Service Registry exception regarding java.lang.Cloneable
        // This prevents the registry from failing when encountering standard marker interfaces
        System.setProperty("helidon.service-registry.check-unrecognized-contracts", "false");

        try {
            var sources = new LinkedList<>(List.of(
                    ConfigSources.systemProperties(),
                    ConfigSources.file(System.getProperty("wakamiti.properties.file")).optional().build(),
                    ConfigSources.classpath("application.yml").build()
            ));
            Config config = Config.builder().sources(sources).build();

            Map<String, String> overrides = config.get("envs").detach() // removes prefix
                    .asMap().orElse(Map.of()).entrySet().stream().filter(
                            e -> Objects.nonNull(System.getenv(e.getKey()))).collect(
                            Collectors.toMap(Map.Entry::getValue, e -> System.getenv(e.getKey())));

            if (!overrides.isEmpty()) {
                sources.addFirst(ConfigSources.create(overrides, "env-mapped-overrides").build());
                config = Config.builder().sources(sources).build();
            }

            Properties props = new Properties();
            config.asMap().orElse(Map.of()).entrySet().stream().filter(e -> e.getKey().contains(".")).forEach(
                    e -> props.put(e.getKey(), e.getValue()));
            Path effectiveProperties = Path.of(props.getProperty("effective.properties"));
            if (Files.notExists(effectiveProperties.getParent())) {
                effectiveProperties.getParent().toFile().mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(effectiveProperties.toFile())) {
                props.store(out, "Wakamiti Effective Configuration");
            } catch (IOException e) {
                throw new IOException("Could not save effective properties", e);
            }

            Server.builder()
                    .config(config)
                    .build()
                    .start();

        } catch (Exception ex) {
            System.err.println("Wakamiti Service Application Failed: " + ex.getMessage());
            System.exit(1);
        }
    }


}
