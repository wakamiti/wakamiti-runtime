/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.config;


import ch.qos.logback.classic.LoggerContext;
import es.wakamiti.service.infrastructure.logging.SessionLogEventPublisher;
import es.wakamiti.service.infrastructure.logging.WebSocketAppender;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.LoggerFactory;

import java.util.Objects;


@ApplicationScoped
public class Log4j2Configurator {

    @Inject
    private SessionLogEventPublisher publisher;

    public void initialize(
            @Observes @Initialized(ApplicationScoped.class) Object init
    ) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLoggerList().stream()
                .map(logger -> logger.getAppender("WS"))
                .filter(Objects::nonNull)
                .findFirst()
                .map(WebSocketAppender.class::cast)
                .ifPresent(appender -> appender.setPublisher(publisher));
    }
}