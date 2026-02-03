/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.logging;


import es.wakamiti.service.WakamitiServiceApplication;
import es.wakamiti.service.domain.spi.LogEventPublisher;
import es.wakamiti.service.domain.model.LogEventSubscriber;
import es.wakamiti.service.domain.spi.LogHistoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@ApplicationScoped
public class SessionLogEventPublisher implements LogEventPublisher<Session> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);

    private final Map<Session, LogEventSubscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<Session, Object> sessionLocks = new ConcurrentHashMap<>();

    private final LogHistoryRepository history;

    @Inject
    public SessionLogEventPublisher(
            LogHistoryRepository history
    ) {
        this.history = history;
    }

    @Override
    public void subscribe(
            Session session
    ) {
        history.find().forEach(msg -> sendMessage(session, msg));
        subscribers.put(session, msg -> sendMessage(session, msg));
    }

    /**
     * According to the Jakarta WebSocket specification, it is not
     * permitted to send a new message before the previous one has been
     * fully sent. Failure to do so could result in an
     * IllegalStateException being thrown.
     * Synchronization is used to ensure that only one message is sent
     * at a time per socket.
     */
    private void sendMessage(Session session, String message) {
        if (session.isOpen()) {
            synchronized (sessionLocks.computeIfAbsent(session, _ -> new Object())) {
                try {
                    session.getAsyncRemote().sendText(message);
                } catch (Exception _) {
                    LOGGER.error("WARN: Unable to send message: {}", message);
                }
            }
        }
    }


    @Override
    public void unsubscribe(
            Session session
    ) {
        subscribers.remove(session);
        sessionLocks.remove(session);
    }


    @Override
    public void publish(
            String message
    ) {
        history.save(message);
        subscribers.values().forEach(it -> it.onLogEvent(message));
    }


    @Override
    public void clear() {
        history.clear();
    }

}
