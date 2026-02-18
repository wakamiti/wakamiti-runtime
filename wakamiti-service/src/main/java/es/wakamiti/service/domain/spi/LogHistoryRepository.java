/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;


import java.util.List;


/**
 * Defines the contract for a repository that stores and retrieves log messages from a command execution.
 * <p>
 * This interface acts as an outbound port in a hexagonal architecture, abstracting the storage
 * mechanism for log history. Implementations could range from a simple in-memory list to a
 * more persistent storage solution like a file or a database.
 * <p>
 * The repository is designed to hold the logs for a single command execution and is typically
 * cleared before a new execution begins.
 */
public interface LogHistoryRepository {

    /**
     * Saves a log message to the repository.
     * <p>
     * This method is called to add a new log entry to the current execution's history.
     *
     * @param message The log message to be stored. Must not be null.
     */
    void save(
            String message
    );

    /**
     * Retrieves all log messages stored for the current execution.
     * <p>
     * This method returns a list of all log messages that have been saved since the
     * repository was last cleared. The messages should be in the order they were saved.
     *
     * @return A {@link List} containing all the log messages. If no messages have been
     *         saved, an empty list is returned.
     */
    List<String> find();

    /**
     * Clears all log messages from the repository.
     * <p>
     * This method is typically called at the beginning of a new command execution to
     * ensure that the log history is fresh and does not contain data from previous runs.
     */
    void clear();

    /**
     * Returns the number of log messages currently stored in the repository.
     *
     * @return The total count of saved log messages.
     */
    int size();

}
