/*
 * Copyright (c) 2022-2026 Instituto Tecnológico de Informática (ITI)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.test.infraestructure.webservice;


import es.wakamiti.service.domain.spi.LogHistoryRepository;
import io.helidon.http.HeaderNames;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static es.wakamiti.service.test.infraestructure.webservice.ExecutionTest.ORIGIN;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


@AddConfig(
        key = "server.auth.origin",
        value = ORIGIN
)
@HelidonTest
class ExecutionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("system");
    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();
    private static final AtomicInteger STATUS = new AtomicInteger(99);

    public static final String ORIGIN = "test";

    @Inject
    private WebTarget target;
    @Inject
    private LogHistoryRepository history;
    private URI uri;

    private Session openSession() throws Exception {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().configurator(
                new OriginConfigurator(ORIGIN)).build();
        return ContainerProvider.getWebSocketContainer().connectToServer(new Client(), config, uri);
    }

    private void assertNextMessage(String expected) throws InterruptedException {
        assertEquals(expected, MESSAGES.poll(10, TimeUnit.SECONDS));
    }

    private void awaitSessionClosed(Session session) {
        await().atMost(Duration.ofSeconds(20)).until(session::isOpen, is(false));
    }

    private Invocation.Builder request(String... path) {
        WebTarget t = target;
        for (String p : path) {
            t = t.path(p);
        }
        return t.request().header(HeaderNames.ORIGIN_NAME, ORIGIN);
    }

    @BeforeEach
    void setUp() {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        uri = URI.create("ws://%s:%s/exec/out".formatted(target.getUri().getHost(), target.getUri().getPort()));
    }

    @AfterEach
    void shutdown() {
        MESSAGES.clear();
        STATUS.set(99);
    }

    @Test
    void testHealth() {
        try (Response response = request("health").get()) {
            LOGGER.debug(response.readEntity(String.class));
            assertThat(response.getStatus(), is(200));
        }
    }

    @DisplayName("Execution with success")
    @Test
    void testExecutionWithSuccess() throws Exception {
        try (Response response = request("exec").post(
                Entity.entity("[\"run\",\"something\"]", MediaType.APPLICATION_JSON_TYPE))) {
            assertThat(response.getStatus(), is(202));
            try (Session session = openSession()) {
                try {
                    assertNextMessage("Executing command: run something" + System.lineSeparator());
                    assertNextMessage("One line" + System.lineSeparator());
                    assertNextMessage("Another line" + System.lineSeparator());
                    assertNextMessage("If execution has been cancelled, this line should not appear"
                            + System.lineSeparator());
                } finally {
                    awaitSessionClosed(session);
                }
            }
            assertEquals(0, history.size());
            assertEquals(0, STATUS.get());
        }
    }

    @DisplayName("Execution with bad request error")
    @ParameterizedTest(name = "[{index}] when entity={argumentsWithNames}")
    @MethodSource("bodies")
    void testExecutionWithBadRequestError(List<String> entity) {
        try (Response response = request("exec").post(Entity.entity(entity, MediaType.APPLICATION_JSON))) {
            assertThat(response.getStatus(), is(400));
        }
    }

    @DisplayName("Execution with unauthorized error")
    @Test
    void testExecutionWithUnauthorizedError() {
        try (Response response = target.path("exec").request().post(
                Entity.entity(List.of("run", "something"), MediaType.APPLICATION_JSON_TYPE))) {
            assertThat(response.getStatus(), is(401));
        }
    }

    @DisplayName("Execution with too many requests error")
    @Test
    void testExecutionWithTooManyRequestsError() throws Exception {
        var entity = Entity.entity(List.of("abc"), MediaType.APPLICATION_JSON_TYPE);
        try (Response response = request("exec").post(entity)) {
            assertThat(response.getStatus(), is(202));
        }
        try (Session session = openSession()) {
            try {
                assertNextMessage("Executing command: abc" + System.lineSeparator());
                try (Response response = request("exec").post(
                        Entity.entity(List.of("abc"), MediaType.APPLICATION_JSON_TYPE))) {
                    assertThat(response.getStatus(), is(429));
                }
                assertNextMessage("One line" + System.lineSeparator());
                assertNextMessage("Another line" + System.lineSeparator());
                assertNextMessage("If execution has been cancelled, this line should not appear"
                        + System.lineSeparator());
            } finally {
                awaitSessionClosed(session);
            }
        }
        assertEquals(0, history.size());
        assertEquals(0, STATUS.get());
    }

    @DisplayName("Execution Socket when send STOP with success")
    @Test
    void testExecutionSocketWhenSendStopWithSuccess() throws Exception {
        try (Response response = request("exec").post(
                Entity.entity(List.of("run", "something"), MediaType.APPLICATION_JSON_TYPE))) {
            assertThat(response.getStatus(), is(202));
            try (Session session = openSession()) {
                try {
                    assertNextMessage("Executing command: run something" + System.lineSeparator());
                    session.getBasicRemote().sendText("STOP");
                    assertNextMessage("One line" + System.lineSeparator());
                    assertNextMessage("Another line" + System.lineSeparator());
                } finally {
                    awaitSessionClosed(session);
                }
            }
            assertEquals(0, history.size());
            assertEquals(1, STATUS.get());
        }
    }

    @DisplayName("Execution Socket when send invalid message with success")
    @Test
    void testExecutionSocketWhenSendInvalidMessageWithSuccess() throws Exception {
        try (Session session = openSession()) {
            session.getBasicRemote().sendText("ABC");
            assertNextMessage("Invalid message received: ABC");
            awaitSessionClosed(session);
            assertFalse(session.isOpen());
        }
        assertEquals(0, history.size());
    }

    /**
     * Test client
     */
    public static class Client extends Endpoint {

        @Override
        public void onOpen(
                Session session,
                EndpointConfig config
        ) {
            LOGGER.trace("Opening client session {}", session.getId());
            session.addMessageHandler(
                    String.class, msg -> {
                        LOGGER.trace("Message received in client session {}: {}", session.getId(), msg);
                        MESSAGES.add(msg);
                    }
            );
        }

        @Override
        public void onError(
                Session session,
                Throwable error
        ) {
            LOGGER.trace("Error in client session {}", session.getId(), error);
        }

        @Override
        public void onClose(
                Session session,
                CloseReason reason
        ) {
            LOGGER.trace(
                    "Closing client session {}: {} - {}", session.getId(), reason.getCloseCode(),
                    reason.getReasonPhrase()
            );
            if (!reason.getCloseCode().equals(CloseReason.CloseCodes.NORMAL_CLOSURE)) {
                MESSAGES.add(reason.getReasonPhrase());
            } else {
                STATUS.set(Integer.parseInt(reason.getReasonPhrase()));
            }
        }
    }


    private static class OriginConfigurator extends ClientEndpointConfig.Configurator {

        private final String origin;

        public OriginConfigurator(String origin) {
            this.origin = origin;
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put(HeaderNames.ORIGIN_NAME, List.of(origin));
        }
    }

    static Stream<Arguments> bodies() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()), Arguments.of(List.of("")));
    }
}
