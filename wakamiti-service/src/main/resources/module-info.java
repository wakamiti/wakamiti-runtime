import io.helidon.webserver.http1.spi.Http1UpgradeProvider;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
module wakamiti.service {

    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;

    requires io.helidon.config;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.tyrus;

    requires microprofile.config.api;
    requires microprofile.openapi.api;
    requires org.slf4j;
    requires io.helidon.webserver;

    opens es.wakamiti.service.application.service to
            io.helidon.microprofile.cdi,
            weld.core.impl;
    opens es.wakamiti.service.infrastructure.config to
            io.helidon.microprofile.cdi,
            weld.core.impl;
    opens es.wakamiti.service.infrastructure.exec to
            io.helidon.microprofile.cdi,
            weld.core.impl;
    opens es.wakamiti.service.infrastructure.logging to
            io.helidon.microprofile.cdi,
            weld.core.impl;
    opens es.wakamiti.service.infrastructure.webservice.http to
            io.helidon.microprofile.cdi,
            weld.core.impl;
    opens es.wakamiti.service.infrastructure.webservice.ws to
            io.helidon.microprofile.cdi,
            weld.core.impl;

    exports es.wakamiti.service to
            weld.core.impl;
    exports es.wakamiti.service.infrastructure.config to
            weld.core.impl;
    exports es.wakamiti.service.infrastructure.exec to
            weld.core.impl;
    exports es.wakamiti.service.infrastructure.logging;
    exports es.wakamiti.service.application.service to
            weld.core.impl;

    exports es.wakamiti.service.domain.spi;
//    exports es.wakamiti.service.domain.api;
//    exports es.wakamiti.service.domain.model;

    exports es.wakamiti.service.infrastructure.webservice.http to
            jersey.server;
    exports es.wakamiti.service.infrastructure.webservice.ws to
            org.glassfish.tyrus.server,
            org.glassfish.tyrus.core;

    provides Http1UpgradeProvider with
            es.wakamiti.service.infrastructure.config.FixedTyrusUpgradeProvider;
}