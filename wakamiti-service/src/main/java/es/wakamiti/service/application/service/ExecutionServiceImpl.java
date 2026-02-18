/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.application.service;


import es.wakamiti.service.WakamitiServiceApplication;
import es.wakamiti.service.domain.api.ExecutionService;
import es.wakamiti.service.domain.spi.ExecutionNotifier;
import es.wakamiti.service.domain.spi.LogEventPublisher;
import es.wakamiti.service.domain.spi.WakamitiRunner;
import io.helidon.common.configurable.ResourceException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Implementation of the ExecutionService that handles asynchronous command
 * execution.
 */
@ApplicationScoped
public class ExecutionServiceImpl implements ExecutionService {

    private final static Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutionNotifier<?> notifier;
    private final WakamitiRunner runner;
    private final LogEventPublisher<?> publisher;

    @Inject
    public ExecutionServiceImpl(
            ExecutionNotifier<?> notifier,
            WakamitiRunner runner,
            LogEventPublisher<?> publisher
    ) {
        this.notifier = notifier;
        this.runner = runner;
        this.publisher = publisher;
    }

    /**
     * Executes a system command asynchronously and outputs the result to the log.
     * <p>
     * The flow is as follows:
     * 1. Validates that the command is not empty.
     * 2. Checks if an execution is already in progress (only one is allowed).
     * 3. Launches the execution in a separate thread (CompletableFuture).
     * 4. Upon completion, notifies the result and clears the log event publisher.
     *
     * @param command The system command to execute.
     * @throws IllegalArgumentException If the command is null or empty.
     * @throws ResourceException        If an execution is already active.
     */
    @Override
    public void execute(
            String command
    ) throws IllegalArgumentException, ResourceException {
        validateCommand(command);
        checkConcurrency();

        LOGGER.info("Starting command execution: {}", command);

        CompletableFuture.supplyAsync(() -> runner.run(command))
                .handle((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Error during command execution: {}", command, ex);
                        notifier.notify(1); // Notify failure (default code 1)
                    } else {
                        LOGGER.info("Command finished with result: {}", result);
                        notifier.notify(result);
                    }
                    return null;
                })
                .whenComplete((_, _) -> cleanup());
    }

    /**
     * Stops the current execution if possible.
     */
    public void stop() {
        LOGGER.info("Requested stop of current command.");
        runner.stop();
    }

    private void validateCommand(
            String command
    ) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
    }

    private void checkConcurrency() {
        if (running.getAndSet(true)) {
            throw new ResourceException("An execution is already in progress. Please wait for it to finish.");
        }
    }

    private void cleanup() {
        try {
            publisher.clear();
        } finally {
            running.set(false);
        }
    }
}