/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.exec;


import es.wakamiti.service.domain.spi.LogHistoryRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;


@ApplicationScoped
public class InMemoryLogHistoryRepository implements LogHistoryRepository {

    /**
     * Thread-safe deque for storing log messages in chronological order.
     */
    private final Deque<String> BUFFER = new ConcurrentLinkedDeque<>();

    @Override
    public void save(
            String message
    ) {
        BUFFER.addLast(message);
    }

    @Override
    public List<String> find() {
        return new ArrayList<>(BUFFER);
    }

    @Override
    public void clear() {
        BUFFER.clear();
    }

    @Override
    public int size() {
        return BUFFER.size();
    }
}
