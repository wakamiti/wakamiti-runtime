/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;


/**
 * Defines the contract for a component that notifies observers about the
 * completion status of a command execution.
 * <p>
 * This interface follows the Observer design pattern and acts as an outbound port
 * in a hexagonal architecture. It allows the core application to signal the outcome
 * of a command execution without being coupled to the specific notification mechanism
 * (e.g., WebSocket, email, etc.).
 * <p>
 * The generic type {@code T} represents the type of the observer that will be
 * registered to receive notifications.
 *
 * @param <T> The type of the observers that will listen for notifications.
 * @see es.wakamiti.service.application.service.ExecutionServiceImpl
 */
public interface ExecutionNotifier<T> {

    /**
     * Notifies all registered observers about the completion of a command execution.
     * <p>
     * This method is called by the application core when a command finishes. The
     * implementation is responsible for iterating through all observers and
     * delivering the status to them.
     *
     * @param status The exit code of the command. By convention, {@code 0} means
     *               success, and any other number indicates an error or a specific
     *               non-successful status.
     */
    void notify(
            Integer status
    );

    /**
     * Registers a new observer to receive execution status notifications.
     * <p>
     * Observers should be added when a client or component expresses interest in
     * the outcome of command executions (e.g., a new WebSocket connection is established).
     *
     * @param observer The observer instance to add. Must not be null.
     */
    void addObserver(
            T observer
    );

    /**
     * Removes a previously registered observer.
     * <p>
     * This should be called when an observer is no longer interested in receiving
     * notifications (e.g., a WebSocket connection is closed).
     *
     * @param observer The observer instance to remove. Must not be null.
     */
    void removeObserver(
            T observer
    );

}
