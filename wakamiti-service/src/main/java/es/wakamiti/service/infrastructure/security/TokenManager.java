/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.infrastructure.security;


import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@ApplicationScoped
public class TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("service.wakamiti");
    public static final String HEADER = "X-Wakamiti-Token";

    private final Path systemPath;

    @Inject
    public TokenManager(@ConfigProperty(name = "server.system.path") Path systemPath) {
        this.systemPath = systemPath;
    }

    private volatile String token;
    private volatile Path tokenFile;

    @PostConstruct
    public void init() {
        LOGGER.trace("Init token resource");
        try {
            if (!Files.exists(systemPath)) {
                Files.createDirectories(systemPath);
            }
            tokenFile = systemPath.resolve("head.token");
            if (!Files.exists(tokenFile)) {
                writeAtomically(tokenFile, generateToken(32));
                set600IfPosix(tokenFile);
            }
            token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot init token file '%s'".formatted(tokenFile), ex);
        }

    }

    public boolean validateToken(String test) {
        return Optional.ofNullable(test)
                .map(t -> t.equals(token))
                .orElse(false);
    }

    public String getToken() {
        return token;
    }

    private static String generateToken(
            int bytes
    ) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static void writeAtomically(
            Path file,
            String content
    ) throws IOException {
        Path tmp = Files.createTempFile(file.getParent(), UUID.randomUUID().toString(), ".tmp");
        Files.writeString(tmp, content + "\n", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOGGER.trace("File '{}' created", file);
    }

    private static void set600IfPosix(
            Path file
    ) {
        try {
            var view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (view != null) {
                Files.setPosixFilePermissions(
                        file,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                );
            }
        } catch (Exception _) {
        }
    }

}
