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
import es.wakamiti.service.infrastructure.logging.WebSocketAppender;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * WebSocket endpoint for streaming command execution output to clients.
 *
 * <p>This WebSocket endpoint provides real-time streaming of command execution output
 * to connected clients. It integrates with the WebSocketAppender to broadcast log
 * messages and command output to all connected WebSocket sessions.</p>
 *
 * @author mgalbis
 * @see WebSocketAppender
 * @see ServerEndpoint
 */
@ServerEndpoint("/exec")
@ApplicationScoped
public class ExecutionSocket {

    private static final Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);
    private static final String STOP = "STOP";

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
        LOGGER.trace("WebSocket created");
    }

    /**
     * Handles new WebSocket connection establishment.
     *
     * <p>When a client connects to the /exec WebSocket endpoint, this method
     * is called to set up the session for receiving command execution output.
     * The session is automatically registered with the WebSocketAppender to
     * start receiving broadcast messages.</p>
     *
     * <p>Registration Process:</p>
     * <ul>
     *   <li>Validates that the session is properly opened</li>
     *   <li>Registers session with WebSocketAppender for message broadcasting</li>
     *   <li>Sends initial connection confirmation to the client</li>
     *   <li>Logs connection establishment for monitoring</li>
     * </ul>
     *
     * @param session the WebSocket session that was just established
     */
    @OnOpen
    public void onOpen(
            Session session
    ) {
        LOGGER.trace("WebSocket open for session {}", session.getId());
        publisher.subscribe(session);
        notifier.addObserver(session);
    }

    /**
     * Handles messages received from WebSocket clients.
     *
     * @param message the message received from the client
     * @param session the WebSocket session that sent the message
     */
    @OnMessage
    public void onMessage(
            String message,
            Session session
    ) {
        if (STOP.equals(message)) {
            service.stop();
        } else {
            throw new IllegalArgumentException("Invalid message received: " + message);
        }
    }

    /**
     * Handles WebSocket connection errors.
     *
     * @param session the WebSocket session that encountered an error
     * @param error   the throwable error that occurred
     */
    @OnError
    public void onError(
            Session session,
            Throwable error
    ) throws IOException {
        LOGGER.error("WebSocket error occurred for session {}", session.getId(), error);
        session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, error.getMessage()));
    }

    /**
     * Handles WebSocket connection closure.
     *
     * <p>This method is called when a WebSocket connection is closed, either by
     * the client, server, or due to network issues. It ensures proper cleanup
     * of the session and logs the closure reason for monitoring.</p>
     *
     * <p>Cleanup Process:</p>
     * <ul>
     *   <li>Removes session from WebSocketAppender to stop message broadcasting</li>
     *   <li>Logs closure reason and code for debugging</li>
     *   <li>Handles different closure scenarios (normal, abnormal, error)</li>
     *   <li>Ensures no resource leaks occur</li>
     * </ul>
     *
     * <p>Closure Reasons:</p>
     * <ul>
     *   <li><strong>Normal Closure</strong>: Client disconnected properly</li>
     *   <li><strong>Abnormal Closure</strong>: Network issues or unexpected disconnection</li>
     *   <li><strong>Protocol Error</strong>: WebSocket protocol violation</li>
     * </ul>
     *
     * @param session the WebSocket session being closed
     * @param reason  the reason for connection closure
     */
    @OnClose
    public void onClose(
            Session session,
            CloseReason reason
    ) {
        LOGGER.trace("WebSocket closed for session {}", session.getId());
        publisher.unsubscribe(session);
        notifier.removeObserver(session);

        if (!reason.getCloseCode().equals(CloseReason.CloseCodes.NORMAL_CLOSURE)) {
            LOGGER.warn("WebSocket connection closed abnormally for session {}: {} - {}",
                        session.getId(), reason.getCloseCode(), reason.getReasonPhrase());
        }
    }

}

