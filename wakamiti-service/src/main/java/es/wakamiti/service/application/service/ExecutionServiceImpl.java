/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.application.service;


import es.wakamiti.service.domain.api.ExecutionService;
import es.wakamiti.service.domain.spi.ExecutionNotifier;
import es.wakamiti.service.domain.spi.LogEventPublisher;
import es.wakamiti.service.domain.spi.WakamitiRunner;
import io.helidon.common.configurable.ResourceException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Implementation of the ExecutionService that handles asynchronous command
 * execution.
 */
@ApplicationScoped
public class ExecutionServiceImpl implements ExecutionService {

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    private ExecutionNotifier<?> notifier;
    @Inject
    private WakamitiRunner runner;
    @Inject
    private LogEventPublisher<?> publisher;


    /**
     * Executes a system command asynchronously and streams output to logger.
     *
     * <p>The method uses a single-threaded ExecutorService to ensure commands
     * are executed sequentially. This design choice prevents resource conflicts
     * and maintains predictable execution order, while still providing
     * asynchronous behavior for the REST API clients.</p>
     *
     * @param command the system command to execute
     *
     * @throws IllegalArgumentException if the command is null, empty or wrong
     *                                  commands
     * @throws ResourceException        if there is already an execution in
     *                                  progress
     */
    @Override
    public void execute(
            String command
    ) throws IllegalArgumentException, ResourceException {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        if (running.getAndSet(true)) {
            throw new ResourceException("Maximum concurrent executions reached. Please try again later.");
        }

        CompletableFuture.supplyAsync(() -> runner.run(command))
                .thenAccept(notifier::notify)
                .thenRun(() -> running.set(false))
                .thenRun(() -> publisher.clear())
        ;
    }

    public void stop() {
        runner.stop();
    }

}
