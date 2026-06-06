/*
 * Copyright (c) 2022-2026 Instituto Tecnológico de Informática (ITI)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.test.infraestructure.webservice.http;


import es.wakamiti.service.domain.api.ExecutionService;
import es.wakamiti.service.infrastructure.webservice.http.ExecutionResource;
import io.helidon.common.configurable.ResourceException;
import io.helidon.http.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;


class ExecutionResourceTest {

    @DisplayName("Execution resource returns accepted when command submission succeeds")
    @Test
    void testExecutionResourceReturnsAcceptedWhenCommandSubmissionSucceeds() {
        AtomicReference<List<String>> received = new AtomicReference<>();
        ExecutionResource resource = new ExecutionResource(new StubExecutionService() {
            @Override
            public void execute(List<String> argv) {
                received.set(argv);
            }
        });

        try (Response response = resource.execute(List.of("run", "suite"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
            assertThat(received.get()).containsExactly("run", "suite");
        }
    }

    @DisplayName("Execution resource returns bad request when command is invalid")
    @Test
    void testExecutionResourceReturnsBadRequestWhenCommandIsInvalid() {
        ExecutionResource resource = new ExecutionResource(new StubExecutionService() {
            @Override
            public void execute(List<String> argv) {
                throw new IllegalArgumentException("argv cannot be null or empty");
            }
        });

        assertErrorResponse(
                resource.execute(List.of()),
                Response.Status.BAD_REQUEST,
                "argv cannot be null or empty"
        );
    }

    @DisplayName("Execution resource returns not found when command does not exist")
    @Test
    void testExecutionResourceReturnsNotFoundWhenCommandDoesNotExist() {
        ExecutionResource resource = new ExecutionResource(new StubExecutionService() {
            @Override
            public void execute(List<String> argv) {
                throw new NotFoundException("Command not found");
            }
        });

        assertErrorResponse(
                resource.execute(List.of("waka-missing")),
                Response.Status.NOT_FOUND,
                "Command not found"
        );
    }

    @DisplayName("Execution resource returns too many requests when another execution is active")
    @Test
    void testExecutionResourceReturnsTooManyRequestsWhenAnotherExecutionIsActive() {
        ExecutionResource resource = new ExecutionResource(new StubExecutionService() {
            @Override
            public void execute(List<String> argv) {
                throw new ResourceException("busy");
            }
        });

        assertErrorResponse(
                resource.execute(List.of("run")),
                Response.Status.TOO_MANY_REQUESTS,
                "Maximum concurrent executions reached. Please try again later."
        );
    }

    @DisplayName("Execution resource returns internal server error when execution fails unexpectedly")
    @Test
    void testExecutionResourceReturnsInternalServerErrorWhenExecutionFailsUnexpectedly() {
        ExecutionResource resource = new ExecutionResource(new StubExecutionService() {
            @Override
            public void execute(List<String> argv) {
                throw new IllegalStateException("boom");
            }
        });

        assertErrorResponse(
                resource.execute(List.of("run")),
                Response.Status.INTERNAL_SERVER_ERROR,
                "Failed to submit command for execution: boom"
        );
    }

    private static void assertErrorResponse(
            Response response,
            Response.Status expectedStatus,
            String expectedBody
    ) {
        try (response) {
            assertThat(response.getStatus()).isEqualTo(expectedStatus.getStatusCode());
            assertThat(response.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
            assertThat(response.getEntity()).isEqualTo(expectedBody);
        }
    }

    private abstract static class StubExecutionService implements ExecutionService {

        @Override
        public void stop() {
        }
    }
}
