/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.test.infraestructure.webservice;


import es.wakamiti.service.domain.spi.LogHistoryRepository;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;


@HelidonTest
class ExecutionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("system");
    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();
    private static final AtomicInteger STATUS = new AtomicInteger(99);

    @Inject
    private WebTarget target;
    @Inject
    private LogHistoryRepository history;
    private URI uri;

    @BeforeEach
    void setUp() {
        uri = URI.create("ws://%s:%s/exec".formatted(target.getUri().getHost(), target.getUri().getPort()));
    }

    @AfterEach
    void shutdown() {
        MESSAGES.clear();
        STATUS.set(99);
    }

    @Test
    void testHealth() {
        try (Response response = target
                .path("health")
                .request()
                .get()) {
            LOGGER.debug(response.readEntity(String.class));
            assertThat(response.getStatus(), is(200));
        }
    }

    @DisplayName("Execution with success")
    @Test
    void testExecutionWithSuccess() throws Exception {
        try (Response response = target
                .path("exec")
                .request()
                .post(Entity.entity("run something", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(204));
            try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
                try {
                    assertEquals("Ejecutando comando: run something" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Una línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Otra línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Si se ha cancelado la ejecución, esta línea no debería salir" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                } finally {
                    await().atMost(Duration.ofSeconds(20))
                            .until(session::isOpen, is(false));
                }
            }
            assertEquals(0, history.size());
            assertEquals(0, STATUS.get());
        }
    }

    @DisplayName("Execution with bad request error")
    @ParameterizedTest(name = "[{index}] when entity={argumentsWithNames}")
    @NullAndEmptySource
    void testExecutionWithBadRequestError(String entity) {
        try (Response response = target
                .path("exec")
                .request()
                .post(Entity.entity(entity, MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(400));
        }
    }

    @DisplayName("Execution with too many requests error")
    @Test
    void testExecutionWithTooManyRequestsError() throws Exception {
        try (Response response = target
                .path("exec")
                .request()
                .post(Entity.entity("abc", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(204));
        }
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
        try (Response response = target
                .path("exec")
                .request()
                .post(Entity.entity("abc", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(429));
            try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
                try {
                    assertEquals("Ejecutando comando: abc" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Una línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Otra línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Si se ha cancelado la ejecución, esta línea no debería salir" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                } finally {
                    await().atMost(Duration.ofSeconds(20))
                            .until(session::isOpen, is(false));
                }
            }
            assertEquals(0, history.size());
            assertEquals(0, STATUS.get());
        }
    }

    @DisplayName("Execution Socket when send STOP with success")
    @Test
    void testExecutionSocketWhenSendStopWithSuccess() throws Exception {
        try (Response response = target
                .path("exec")
                .request()
                .post(Entity.entity("run something", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(response.getStatus(), is(204));
            try (Session session = ContainerProvider.getWebSocketContainer()
                    .connectToServer(Client.class, uri)) {
                try {
                    session.getBasicRemote().sendText("STOP");
                    assertEquals("Ejecutando comando: run something" + System.lineSeparator(),
                                 MESSAGES.poll(15, TimeUnit.SECONDS));
                    assertEquals("Una línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                    assertEquals("Otra línea" + System.lineSeparator(),
                                 MESSAGES.poll(10, TimeUnit.SECONDS));
                } finally {
                    await().atMost(Duration.ofSeconds(20))
                            .until(session::isOpen, is(false));
                }
            }
            assertEquals(0, history.size());
            assertEquals(1, STATUS.get());
        }
    }

    @DisplayName("Execution Socket when send invalid message with success")
    @Test
    void testExecutionSocketWhenSendInvalidMessageWithSuccess() throws Exception {
        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
            session.getBasicRemote().sendText("ABC");
            assertEquals("Invalid message received: ABC", MESSAGES.poll(10, TimeUnit.SECONDS));
            assertFalse(session.isOpen());
        }
        assertEquals(0, history.size());
    }

    /**
     * Test client
     */
    @ClientEndpoint
    public static class Client {

        @OnOpen
        public void open(Session session) {
            LOGGER.trace("Opening client session {}", session.getId());
            await().pollDelay(1, TimeUnit.SECONDS).until(() -> true);
        }

        @OnMessage
        public void message(Session session, String msg) {
            LOGGER.trace("Message received in client session {}: {}", session.getId(), msg);
            MESSAGES.add(msg);
        }

        @OnError
        public void onError(Session session, Throwable error) {
            LOGGER.trace("Error in client session {}", session.getId(), error);
        }

        @OnClose
        public void onClose(
                Session session,
                CloseReason reason
        ) {
            LOGGER.trace("Closing client session {}: {} - {}",
                         session.getId(), reason.getCloseCode(), reason.getReasonPhrase());
            if (!reason.getCloseCode().equals(CloseReason.CloseCodes.NORMAL_CLOSURE)) {
                MESSAGES.add(reason.getReasonPhrase());
            } else {
                STATUS.set(Integer.parseInt(reason.getReasonPhrase()));
            }
            await().pollDelay(1, TimeUnit.SECONDS).until(() -> true);
        }
    }

}
