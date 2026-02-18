/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.model;


import es.wakamiti.service.domain.spi.ExecutionNotifier;


/**
 * Defines the contract for an object that observes the completion status of a command execution.
 * <p>
 * This interface is part of the Observer design pattern, where implementations of this
 * interface act as the "Observer". They register with an {@link ExecutionNotifier} (the "Subject")
 * to be notified when a command execution is complete.
 * <p>
 * In the context of this application, a typical implementation would be a WebSocket endpoint
 * that forwards the status notification to a connected client.
 *
 * @see ExecutionNotifier
 */
public interface ExecutionObserver {

    /**
     * This method is called by the {@link ExecutionNotifier} when a command execution finishes.
     *
     * @param status The exit code of the completed command. By convention, a status of {@code 0}
     *               indicates success, while a non-zero value indicates an error or abnormal
     *               termination.
     */
    void onStatus(
            Integer status
    );

}
