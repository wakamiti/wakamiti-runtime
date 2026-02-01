/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.exec;


import es.wakamiti.service.domain.model.ExecutionObserver;
import es.wakamiti.service.domain.spi.ExecutionNotifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@ApplicationScoped
public class SessionExecutionNotifier implements ExecutionNotifier<Session> {

    private final Map<Session, ExecutionObserver> observers = new ConcurrentHashMap<>();

    @Override
    public void notify(
            Integer status
    ) {
        observers.values().forEach(it -> it.onStatus(status));
    }

    @Override
    public void addObserver(
            Session session
    ) {
        this.observers.put(session, status -> {
            if (session.isOpen()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, status.toString()));
                } catch (IOException _) {
                }
            }
        });
    }

    @Override
    public void removeObserver(
            Session session
    ) {
        this.observers.remove(session);
    }

}
