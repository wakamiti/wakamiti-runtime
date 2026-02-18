/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;


/**
 * Defines the contract for a component that publishes log messages to subscribers in real-time.
 * <p>
 * This interface follows a Publish-Subscribe pattern and serves as an outbound port in a
 * hexagonal architecture. It enables the application's logging infrastructure (e.g., Logback)
 * to broadcast log events to interested parties, such as WebSocket clients, without direct
 * coupling.
 * <p>
 * The generic type {@code T} represents the type of the subscriber that will receive the log messages.
 *
 * @param <T> The type of the subscribers that will listen for log events.
 */
public interface LogEventPublisher<T> {

    /**
     * Adds a new subscriber to receive log messages.
     * <p>
     * Once subscribed, the subscriber will receive all log messages published via the
     * {@link #publish(String)} method.
     *
     * @param subscriber The subscriber instance to add. Must not be null.
     */
    void subscribe(
            T subscriber
    );

    /**
     * Removes a subscriber, so it no longer receives log messages.
     * <p>
     * This should be called when a subscriber is no longer interested in log events,
     * for example, when a WebSocket connection is closed.
     *
     * @param subscriber The subscriber instance to remove. Must not be null.
     */
    void unsubscribe(
            T subscriber
    );

    /**
     * Publishes a log message to all currently subscribed listeners.
     * <p>
     * This method is typically called by a custom logging appender that is integrated
     * with the application's logging framework (e.g., Logback).
     *
     * @param message The log message to be broadcast.
     */
    void publish(
            String message
    );

    /**
     * Clears any internal state of the publisher, such as cached messages or resources.
     * <p>
     * This method can be used to reset the publisher's state, for example, after a
     * command execution has completed.
     */
    void clear();

}
