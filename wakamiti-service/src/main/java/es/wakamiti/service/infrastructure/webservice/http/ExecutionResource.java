/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.webservice.http;


import es.wakamiti.service.WakamitiServiceApplication;
import es.wakamiti.service.domain.api.ExecutionService;
import io.helidon.common.configurable.ResourceException;
import io.helidon.http.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * REST resource for executing system commands asynchronously.
 *
 * <p>This resource provides a RESTful API endpoint for submitting system commands
 * for asynchronous execution. The commands are processed by the ExecutionService
 * and their output is streamed in real-time through WebSocket connections.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Asynchronous command execution - commands are submitted and processed in background</li>
 *   <li>Real-time output streaming via WebSocket (/execution endpoint)</li>
 *   <li>Comprehensive error handling with appropriate HTTP status codes</li>
 *   <li>Input validation and sanitization</li>
 *   <li>Rate limiting support to prevent system overload</li>
 * </ul>
 *
 * <p>HTTP Status Codes Explained:</p>
 * <ul>
 *   <li><strong>204 No Content</strong>: Command was successfully submitted for execution.
 *       This is the appropriate response for asynchronous operations where the client
 *       doesn't need response data and should monitor execution via WebSocket.</li>
 *   <li><strong>400 Bad Request</strong>: Invalid command format or empty/null command.
 *       Indicates client-side error in request formation.</li>
 *   <li><strong>404 Not Found</strong>: The requested command could not be found or is not available.</li>
 *   <li><strong>429 Too Many Requests</strong>: Rate limit exceeded. Prevents system
 *       overload by limiting concurrent command executions.</li>
 *   <li><strong>500 Internal Server Error</strong>: Unexpected server-side error during
 *       command submission or system failure.</li>
 * </ul>
 *
 * <p>Usage Example:</p>
 * <pre>{@code
 * POST /exec
 * Content-Type: text/plain
 *
 * ls -la /tmp
 * }</pre>
 */
@Path("/exec")
@OpenAPIDefinition(
        info = @Info(
                title = "Wakamiti Command Execution API",
                description = "RESTful API for executing system commands asynchronously with real-time output streaming via WebSocket. " +
                        "Submit commands via POST /exec and monitor execution output through WebSocket connection at /execution.",
                version = "1.0.0",
                license = @License(
                        name = "Mozilla Public License 2.0",
                        url = "https://mozilla.org/MPL/2.0/"
                )
        ),
        tags = {
                @Tag(name = "Command Execution", description = "Operations for executing system commands asynchronously")
        }
)
@Tag(name = "Command Execution")
@ApplicationScoped
public class ExecutionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(WakamitiServiceApplication.NAME);

    /**
     * Service responsible for handling asynchronous command execution.
     * Injected via CDI to ensure proper lifecycle management and thread safety.
     */
    private final ExecutionService executionService;

    @Inject
    public ExecutionResource(
            ExecutionService executionService
    ) {
        this.executionService = executionService;
        LOGGER.trace("Iniciando execution resource");
    }

    /**
     * Executes a system command asynchronously.
     *
     * <p>This endpoint accepts a command as plain text and submits it for asynchronous execution.
     * The command will be processed in the background by the ExecutionService, and its output
     * will be streamed in real-time through the WebSocket endpoint at '/execution'.</p>
     *
     * <p>The method implements proper error handling and returns appropriate HTTP status codes:</p>
     * <ul>
     *   <li><strong>204 No Content</strong>: Command successfully submitted for execution</li>
     *   <li><strong>400 Bad Request</strong>: Invalid or empty command</li>
     *   <li><strong>404 Not Found</strong>: Command not found</li>
     *   <li><strong>429 Too Many Requests</strong>: Rate limit exceeded</li>
     *   <li><strong>500 Internal Server Error</strong>: Server-side execution error</li>
     * </ul>
     *
     * <p>Concurrency Design Decision:</p>
     * <p>The choice to return 204 No Content immediately after command submission is deliberate
     * and follows best practices for asynchronous APIs. This approach provides several benefits:</p>
     * <ul>
     *   <li>Non-blocking client interaction - clients don't wait for command completion</li>
     *   <li>Scalability - server can handle multiple requests without blocking</li>
     *   <li>Real-time feedback - clients receive live output via WebSocket</li>
     *   <li>Resource efficiency - HTTP connection is freed immediately</li>
     * </ul>
     *
     * @param command the system command to execute (plain text format)
     * @return HTTP response indicating submission status
     * @throws IllegalArgumentException if command is null, empty, or invalid
     * @see ExecutionService#execute(String)
     */
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(
            operationId = "execution",
            summary = "Execute system command asynchronously",
            description = "Submits a system command for asynchronous execution. The command output will be " +
                    "streamed in real-time through the WebSocket endpoint at '/execution'. " +
                    "Common commands include shell commands, scripts, or system utilities."
    )
    @APIResponse(
            responseCode = "204",
            description = "Command successfully submitted for asynchronous execution. " +
                    "Monitor execution progress and output via WebSocket at '/execution'.",
            content = @Content()
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request - Invalid command format, empty command, or malformed request. " +
                    "Ensure the command is provided as plain text in the request body.",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(type = SchemaType.STRING),
                    examples = {
                            @ExampleObject(
                                    name = "Empty Command Error",
                                    value = "Command cannot be null or empty"
                            ),
                            @ExampleObject(
                                    name = "Invalid Format Error",
                                    value = "Invalid command format"
                            )
                    }
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Command not found - The requested command is not available in the system.",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(type = SchemaType.STRING),
                    examples = {
                            @ExampleObject(
                                    name = "Command Not Found Error",
                                    value = "Command not found"
                            )
                    }
            )
    )
    @APIResponse(
            responseCode = "429",
            description = "Too Many Requests - Rate limit exceeded. The system is currently processing " +
                    "the maximum number of concurrent commands. Please wait and try again.",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(type = SchemaType.STRING),
                    examples = {
                            @ExampleObject(
                                    name = "Rate Limit Error",
                                    value = "Maximum concurrent executions reached. Please try again later."
                            )
                    }
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Internal Server Error - Unexpected error occurred during command submission. " +
                    "This may indicate system resource issues or internal service failures.",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(type = SchemaType.STRING),
                    examples = {
                            @ExampleObject(
                                    name = "System Error",
                                    value = "Failed to submit command for execution due to system error"
                            )
                    }
            )
    )
    public Response execute(
            @RequestBody(
                    description = "The system command to execute. Provide the complete command as plain text, " +
                            "including any arguments and options. Examples: 'ls -la', 'echo Hello World', " +
                            "'find /tmp -name \"*.txt\"'",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            example = "ls -la /tmp",
                            schema = @Schema(type = SchemaType.STRING, minLength = 1, maxLength = 1000)
                    )
            )
            String command
    ) {

        try {
            executionService.execute(command);
        } catch (ResourceException _) {
            // Rate limiting - too many concurrent executions
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity("Maximum concurrent executions reached. Please try again later.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        } catch (NotFoundException ex) {
            // Not found - command does not exist
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ex.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        } catch (IllegalArgumentException ex) {
            // Invalid input - null, empty, or unknown command
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ex.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        } catch (Exception ex) {
            // Unexpected system error during command submission
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to submit command for execution: " + ex.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        return Response.noContent().build();
    }

}
