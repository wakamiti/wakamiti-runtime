/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
open module wakamiti.service.test {

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;
    requires hamcrest.all;
    requires io.helidon.microprofile.testing.junit5;
    requires wakamiti.service;
    requires jakarta.websocket.client;

//    exports es.wakamiti.service.infrastructure.webservice.http;

//    opens es.wakamiti.service.infrastructure.webservice.http to
//            io.helidon.microprofile.testing,
//            org.junit.platform.commons;
//    opens es.wakamiti.service.infrastructure.config to
//            io.helidon.microprofile.testing,
//            org.junit.platform.commons;

    // Dependencias de Helidon MP y CDI
//    requires io.helidon.microprofile.cdi;
//    requires io.helidon.microprofile.server;
//    requires io.helidon.microprofile.testing;  // Módulo de testing de Helidon
//    requires weld.core.impl;                   // Implementación de CDI (Weld)

    // Exportar paquetes que necesiten ser accedidos por Helidon/Weld
    exports es.wakamiti.service.test.infraestructure.webservice.http to io.helidon.microprofile.cdi;
    exports es.wakamiti.service.test.infraestructure.webservice.ws to io.helidon.microprofile.cdi;

    // Abrir paquetes para reflexión (solo si es necesario)
//    opens es.wakamiti.service.test.infraestructure.webservice.http to weld.core.impl, io.helidon.microprofile.cdi;

    // Si usas JAX-RS o otros estándares
//    requires jakarta.ws.rs;
}