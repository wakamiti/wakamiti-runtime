/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.exec;


import es.wakamiti.service.domain.spi.WakamitiRunner;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;


@ApplicationScoped
public class DummyWakamitiRunner implements WakamitiRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger("es.wakamiti.core");

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public int run(
            String command
    ) {
        started.set(true);

        try {
            Thread.sleep(Duration.ofSeconds(3));
            LOGGER.info("Executing command: {}", command);
            Thread.sleep(Duration.ofSeconds(1));
            LOGGER.trace("This should not be output to ws");
            LOGGER.info("One line");
            Thread.sleep(Duration.ofSeconds(2));
            LOGGER.info("Another line");
            Thread.sleep(Duration.ofSeconds(3));
            if (!started.get()) {
                return 1;
            }
            LOGGER.info("If execution has been cancelled, this line should not appear");
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return -1;
        }

        stop();
        return 0;
    }

    @Override
    public void stop() {
        started.set(false);
    }
}
