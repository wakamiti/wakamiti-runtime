/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.domain.api;


import io.helidon.common.configurable.ResourceException;


/**
 * Service interface for asynchronous command execution with real-time
 * output streaming.
 * 
 * <p>This service defines the contract for executing system commands
 * asynchronously while providing real-time streaming of command output
 * to connected WebSocket clients. It's designed to handle long-running
 * commands without blocking the calling thread and provides immediate
 * feedback to clients about command submission status.</p>
 * 
 * <p>Key Responsibilities:</p>
 * <ul>
 *   <li>Validate and execute system commands asynchronously</li>
 *   <li>Stream command output in real-time to WebSocket clients</li>
 *   <li>Handle command lifecycle events (start, output, completion,
 *   errors)</li>
 *   <li>Manage process resources and ensure proper cleanup</li>
 *   <li>Provide comprehensive error reporting and status updates</li>
 * </ul>
 * 
 * <p>Integration Architecture:</p>
 * <ul>
 *   <li><strong>REST Layer</strong>: CommandResource accepts HTTP
 *   requests and delegates to this service</li>
 *   <li><strong>WebSocket Layer</strong>: ExecutionSocket manages
 *   client connections for real-time updates</li>
 *   <li><strong>Streaming Layer</strong>: WebSocketAppender broadcasts
 *   execution events to connected clients</li>
 *   <li><strong>Execution Layer</strong>: This service coordinates
 *   command execution and event broadcasting</li>
 * </ul>
 * 
 * <p>Execution Flow:</p>
 * <ol>
 *   <li>Client submits command via REST API (POST /exec)</li>
 *   <li>CommandResource calls {@link #execute(String)} method</li>
 *   <li>Command is queued for asynchronous execution</li>
 *   <li>HTTP 204 No Content response is returned immediately</li>
 *   <li>Command execution begins in background thread</li>
 *   <li>Execution events are broadcast to WebSocket clients via /execution endpoint</li>
 *   <li>Process output is streamed in real-time as it's generated</li>
 *   <li>Completion status and cleanup notifications are sent to clients</li>
 * </ol>
 * 
 * <p>Error Handling Strategy:</p>
 * <ul>
 *   <li><strong>Validation Errors</strong>: Thrown as IllegalArgumentException for immediate HTTP 400 response</li>
 *   <li><strong>Resource Errors</strong>: Thrown as ResourceException for HTTP 429 rate limiting</li>
 *   <li><strong>Execution Errors</strong>: Caught and broadcast to clients, logged for debugging</li>
 *   <li><strong>System Errors</strong>: Thrown as RuntimeException for HTTP 500 response</li>
 * </ul>
 * 
 * <p>Performance Characteristics:</p>
 * <ul>
 *   <li><strong>Non-blocking</strong>: Method returns immediately after command validation and queuing</li>
 *   <li><strong>Scalable</strong>: Uses ExecutorService for concurrent command execution</li>
 *   <li><strong>Resource-safe</strong>: Automatic cleanup of processes and streams</li>
 *   <li><strong>Real-time</strong>: Output streaming without buffering delays</li>
 * </ul>
 * 
 * <p>Thread Safety:</p>
 * <p>Implementations of this interface must be thread-safe to handle concurrent
 * command submissions from multiple clients. The service should use appropriate
 * synchronization mechanisms and thread-safe collections.</p>
 * 
 * <p>Usage Example:</p>
 * <pre>{@code
 * @Inject
 * private ExecutionService executionService;
 * 
 * // In REST endpoint
 * try {
 *     executionService.execute("ls -la /tmp");
 *     return Response.noContent().build(); // 204 - Command queued successfully
 * } catch (IllegalArgumentException e) {
 *     return Response.status(400).entity(e.getMessage()).build();
 * } catch (ResourceException e) {
 *     return Response.status(429).entity("Too many concurrent executions").build();
 * }
 * }</pre>
 */
public interface ExecutionService {

    /**
     * Executes a command.
     * 
     * <p>This method validates the provided command and submits it for asynchronous
     * execution. The method returns immediately after validation and queuing,
     * allowing the calling thread to continue without blocking. Command execution
     * happens in a background thread managed by an ExecutorService.</p>
     * 
     * <p>Real-time Streaming:</p>
     * <p>As the command executes, its output is continuously streamed to all
     * connected WebSocket clients through the WebSocketAppender. Clients can
     * connect to the /execution WebSocket endpoint to receive live updates.</p>
     * 
     * <p>Command Validation:</p>
     * <ul>
     *   <li>Command must not be null</li>
     *   <li>Command must not be empty or contain only whitespace</li>
     *   <li>Command should be a valid system command or script</li>
     * </ul>
     * 
     * <p>Execution Lifecycle Events:</p>
     * <ol>
     *   <li><strong>EXECUTION_START</strong>: Command execution begins</li>
     *   <li><strong>PROCESS_STARTED</strong>: System process created with PID</li>
     *   <li><strong>PROCESS_OUTPUT</strong>: Each line of command output (real-time)</li>
     *   <li><strong>EXECUTION_SUCCESS/WARNING</strong>: Command completion with exit code</li>
     *   <li><strong>EXECUTION_COMPLETE</strong>: Resource cleanup finished</li>
     * </ol>
     * 
     * <p>Error Events:</p>
     * <ul>
     *   <li><strong>EXECUTION_ERROR</strong>: IOException or unexpected errors</li>
     *   <li><strong>EXECUTION_INTERRUPTED</strong>: Command execution was interrupted</li>
     *   <li><strong>PROCESS_TERMINATE</strong>: Process force termination</li>
     * </ul>
     * 
     * <p>Process Management:</p>
     * <ul>
     *   <li>Commands are executed using ProcessBuilder for security and control</li>
     *   <li>Standard error is merged with standard output for unified streaming</li>
     *   <li>Processes are monitored for completion and cleaned up automatically</li>
     *   <li>Long-running processes can be force-terminated during shutdown</li>
     * </ul>
     * 
     * <p>Concurrency Design:</p>
     * <p>The method uses a single-threaded ExecutorService to ensure commands are
     * executed sequentially. This design choice prevents resource conflicts and
     * maintains predictable execution order, while still providing asynchronous
     * behavior for the REST API clients.</p>
     * 
     * @param command the system command to execute (must not be null or empty)
     * 
     * @throws IllegalArgumentException if command is null, empty, or contains only whitespace.
     *         This exception should be caught by the REST layer and converted to HTTP 400.
     * @throws ResourceException if the system is currently
     *         at maximum capacity for concurrent executions. This should be caught by the
     *         REST layer and converted to HTTP 429.
     * @throws RuntimeException if an unexpected system error occurs during command submission
     *         or if there are issues with the execution infrastructure. This should be caught
     *         by the REST layer and converted to HTTP 500.
     *
     */
    void execute(
            String command
    );

    /**
     * Stops the execution service and releases resources.
     * <p>
     * This method should be called when the application is shutting down or when
     * the service is no longer needed. It ensures that any running processes are
     * terminated and that the executor service is shut down gracefully.
     */
    void stop();
}
