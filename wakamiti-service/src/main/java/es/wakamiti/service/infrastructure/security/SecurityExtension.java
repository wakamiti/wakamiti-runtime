/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.security;


import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.Filter;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static es.wakamiti.service.infrastructure.security.TokenManager.HEADER;


public class SecurityExtension implements Extension {

    private static final Logger LOGGER = LoggerFactory.getLogger("service.wakamiti");

    public void before(@Observes BeforeBeanDiscovery bbd) {
        LOGGER.trace("SecurityExtension loaded via SPI");
    }

    public void onStart(@Observes
                        @Priority(11)
                        @Initialized(ApplicationScoped.class)
                        Object event,
                        BeanManager manager,
                        TokenManager tokenManager
    ) {
        LOGGER.trace("Init security extension");
        tokenManager.init();

        ServerCdiExtension server = manager.getExtension(ServerCdiExtension.class);

        Filter authFilter = (chain, req, res) -> {

            boolean authorized = req.headers().first(HeaderNames.create(HEADER))
                    .map(tokenManager::validateToken)
                    .orElse(false);
            if (!authorized) {
                res.status(Status.UNAUTHORIZED_401);
                res.send("Unauthorized: Missing or invalid token");
                return;
            }

            chain.proceed();
        };

        server.serverRoutingBuilder()
                .addFeature(routing -> routing.addFilter(authFilter));
    }

}
