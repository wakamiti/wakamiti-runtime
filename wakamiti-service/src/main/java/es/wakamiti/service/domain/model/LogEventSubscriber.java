/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.model;


import es.wakamiti.service.domain.spi.LogEventPublisher;


/**
 * Defines the contract for an object that subscribes to real-time log events.
 * <p>
 * This interface is part of the Publish-Subscribe design pattern, where implementations
 * of this interface act as the "Subscriber". They register with a {@link LogEventPublisher}
 * (the "Publisher") to receive log messages as they are generated within the application.
 * <p>
 * A primary use case for this interface is to stream live log data to external clients,
 * for instance, through a WebSocket connection.
 *
 * @see LogEventPublisher
 */
public interface LogEventSubscriber {

    /**
     * This method is called by the {@link LogEventPublisher} every time a new log message
     * is published.
     *
     * @param message The log message that was generated. This is typically a single line
     *                of output from the application's logging system.
     */
    void onLogEvent(
            String message
    );

}
