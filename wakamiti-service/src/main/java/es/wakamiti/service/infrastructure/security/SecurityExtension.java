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


import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.Filter;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SecurityExtension implements Extension {

    private static final Logger LOGGER = LoggerFactory.getLogger("service.wakamiti");

    public void before(
            @Observes
            BeforeBeanDiscovery bbd
    ) {
        LOGGER.trace("SecurityExtension loaded via SPI");
    }

    public void onStart(
            @Observes
            @RuntimeStart
            @Priority(11)
            Config config,
            BeanManager manager
    ) {
        LOGGER.trace("Init security extension");
        String origin = config.get("server.auth.origin").asString().orElse("none");
        ServerCdiExtension server = manager.getExtension(ServerCdiExtension.class);

        Filter authFilter = (chain, req, res) -> {
            boolean authorized = req.headers()
                    .first(HeaderNames.ORIGIN)
                    .map(v -> v.equals(origin))
                    .orElse(false);

            if (!authorized) {
                res.status(Status.UNAUTHORIZED_401);
                res.send("Unauthorized: invalid origin");
                return;
            }

            chain.proceed();
        };

        server.serverRoutingBuilder().addFeature(routing -> routing.addFilter(authFilter));
    }

}
