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


/**
 * Configures the Log4j2 logging system within the application context.
 * This class is application-scoped, meaning it is instantiated once per application lifecycle.
 * It integrates with the Log4j2 logging framework to dynamically configure appenders
 * and customize log message broadcasting via WebSocket connections.
 */
@ApplicationScoped
public class Log4j2Configurator {

    private final SessionLogEventPublisher publisher;

    @Inject
    public Log4j2Configurator(
            SessionLogEventPublisher publisher
    ) {
        this.publisher = publisher;
    }

    /**
     * Initializes the Log4j2 logging configuration for the application context.
     * This method is triggered when the application context is fully initialized and ready.
     * It locates the WebSocket appender ("WS") and associates it with the session log
     * event publisher to enable real-time log streaming via WebSocket.
     *
     * @param init an event object that indicates the application context has been initialized.
     *             It is observed to perform setup tasks upon application startup.
     */
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