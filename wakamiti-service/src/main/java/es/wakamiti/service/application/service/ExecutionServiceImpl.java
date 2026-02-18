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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Implementation of {@link ExecutionService} that manages command execution asynchronously.
 * <p>
 * This class acts as the orchestrator between the user's request and the actual system execution,
 * ensuring that only one command is executed at a time to prevent resource conflicts.
 */
@ApplicationScoped
public class ExecutionServiceImpl implements ExecutionService {

    private final static Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);

    /** Concurrency control to ensure sequential execution. */
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
     * @param argv The command and arguments to execute.
     * @throws IllegalArgumentException If the command list is null or empty.
     * @throws ResourceException        If an execution is already active.
     */
    @Override
    public void execute(
            List<String> argv
    ) throws IllegalArgumentException, ResourceException {
        validateCommand(argv);
        checkConcurrency();

        LOGGER.info("Starting command execution: {}", String.join(" ", argv));

        CompletableFuture.supplyAsync(() -> runner.run(argv))
                .handle((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Error during command execution: {}", String.join(" ", argv), ex);
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
            List<String> argv
    ) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("argv cannot be null or empty");
        }
        if (argv.getFirst().trim().isEmpty()) {
            throw new IllegalArgumentException("argv[0] cannot be empty");
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
