/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.spi;

import java.util.List;


/**
 * Defines the contract for a command execution engine.
 * <p>
 * This interface is a Service Provider Interface (SPI) that abstracts the mechanism
 * for running system commands. Implementations of this interface are responsible for
 * handling the entire lifecycle of a command execution, including starting the process,
 * managing its execution, and handling its termination.
 * <p>
 * In a hexagonal architecture, this interface acts as an outbound port, allowing the
 * application's core domain to delegate the "how" of command execution to an
 * infrastructure-layer component. This keeps the domain logic clean and independent
 * of the underlying execution technology (e.g., {@code ProcessBuilder}, a remote shell, etc.).
 *
 * @see es.wakamiti.service.application.service.ExecutionServiceImpl
 */
public interface WakamitiRunner {

    /**
     * Executes a given system command.
     * <p>
     * This method is expected to be a <strong>blocking operation</strong>. The thread
     * that calls this method will wait until the command has finished its execution.
     * The implementation should capture the exit code of the process and return it.
     *
     * @param argv The command plus its arguments to execute.
     *             Must not be null or empty.
     * @return The exit code of the executed command. By convention, {@code 0} indicates
     *         successful execution, while a non-zero value indicates an error.
     */
    int run(
            List<String> argv
    );

    /**
     * Attempts to stop the currently running command.
     * <p>
     * This method is called to gracefully or forcefully terminate the process
     * started by the {@link #run(List)} method. Implementations should handle
     * the logic for interrupting the execution, such as destroying the system process.
     * <p>
     * If no command is currently running, this method should do nothing and return
     * silently.
     */
    void stop();

}
