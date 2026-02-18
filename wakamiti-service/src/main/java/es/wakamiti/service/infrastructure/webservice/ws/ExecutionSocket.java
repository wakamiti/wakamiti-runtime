/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.webservice.ws;


import es.wakamiti.service.WakamitiServiceApplication;
import es.wakamiti.service.domain.api.ExecutionService;
import es.wakamiti.service.domain.spi.ExecutionNotifier;
import es.wakamiti.service.domain.spi.LogEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * WebSocket endpoint to relay command output to clients.
 * <p>
 * This component allows clients to receive real-time logs and the result
 * of command execution sent via {@link es.wakamiti.service.infrastructure.webservice.http.ExecutionResource}.
 */
@ServerEndpoint("/exec/out")
@ApplicationScoped
public class ExecutionSocket {

    private static final Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);
    private static final String STOP_COMMAND = "STOP";

    private final ExecutionService service;
    private final LogEventPublisher<Session> publisher;
    private final ExecutionNotifier<Session> notifier;

    @Inject
    public ExecutionSocket(
            ExecutionService service,
            LogEventPublisher<Session> publisher,
            ExecutionNotifier<Session> notifier
    ) {
        this.service = service;
        this.publisher = publisher;
        this.notifier = notifier;
        LOGGER.trace("WebSocket server initialized");
    }

    /**
     * Executed when a client opens a new connection.
     * <p>
     * Registers the session with the log publisher and status notifier
     * to start receiving updates.
     */
    @OnOpen
    public void onOpen(
            Session session
    ) {
        LOGGER.trace("New WebSocket connection established: {}", session.getId());
        publisher.subscribe(session);
        notifier.addObserver(session);
    }

    /**
     * Processes messages received from the client.
     * <p>
     * Currently only supports the "STOP" command to halt active execution.
     */
    @OnMessage
    public void onMessage(
            String message,
            Session session
    ) {
        if (STOP_COMMAND.equals(message)) {
            LOGGER.info("Received stop command from client: {}", session.getId());
            service.stop();
        } else {
            throw new IllegalArgumentException("Invalid message received: " + message);
        }
    }

    /**
     * Handles errors in the WebSocket connection.
     */
    @OnError
    public void onError(
            Session session,
            Throwable error
    ) throws IOException {
        LOGGER.error("WebSocket error (session {}): {}", session.getId(), error.getMessage());
        session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, error.getMessage()));
    }

    /**
     * Cleans up registrations when a connection is closed.
     */
    @OnClose
    public void onClose(
            Session session,
            CloseReason reason
    ) {
        LOGGER.trace("WebSocket connection closed: {}", session.getId());
        publisher.unsubscribe(session);
        notifier.removeObserver(session);

        if (!reason.getCloseCode().equals(CloseReason.CloseCodes.NORMAL_CLOSURE)) {
            LOGGER.warn("Connection closed abnormally ({}): {}", reason.getCloseCode(), reason.getReasonPhrase());
        }
    }
}
