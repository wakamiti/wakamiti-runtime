/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service;


import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.server.Server;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@ApplicationScoped
public class WakamitiServiceApplication {

    public static final String NAME = "service.wakamiti";

    static void main() {
        // Fix for the Service Registry exception regarding java.lang.Cloneable
        // This prevents the registry from failing when encountering standard marker interfaces
        System.setProperty("helidon.service-registry.check-unrecognized-contracts", "false");

        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.yml").build())
                .build();

        Map<String, String> overrides = config.get("envs")
                .detach() // removes prefix
                .asMap()
                .orElse(Map.of())
                .entrySet().stream()
                .filter(e -> Objects.nonNull(System.getenv(e.getKey())))
                .collect(Collectors.toMap(Map.Entry::getValue,
                        e -> System.getenv(e.getKey())));

        if (!overrides.isEmpty()) {
            config = Config.builder()
                    .sources(
                            ConfigSources.create(overrides, "env-mapped-overrides").build(),
                            ConfigSources.classpath("application.yml").build()
                    )
                    .build();
        }

        Server.builder()
                .config(config)
                .build()
                .start();
    }


}
