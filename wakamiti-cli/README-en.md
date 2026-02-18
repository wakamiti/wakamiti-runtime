# wakamiti-cli

`waka` is the Wakamiti Runtime command-line client. It does not run testing logic locally; it acts as a proxy to `wakamiti-service`.

## How it works

1. Uses CLI arguments as an ordered list of strings.
2. Sends `POST /exec`.
3. Opens a WebSocket connection to `/exec/out`.
4. Prints each incoming line to `stdout`.
5. When the server closes the socket, maps the close reason to the process exit code.

If the user presses `Ctrl+C`, the client sends `STOP` through WebSocket and waits for a graceful close.

## Configuration

The client loads configuration in this order:

1. `wakamiti.properties` from the current directory.
2. If missing, `wakamiti.properties` next to the executable.
3. If `effective.properties` is declared, it merges that file on top.

Required keys:

- `server.host`
- `server.port`
- `server.auth.origin`

## Properties parsing

The parser supports:

- `#` comments.
- Escaped characters (`\:`, `\=`, `\ `, `\\`, `\#`, `\!`).
- `${user.home}` replacement.
- Simple `${other.property}` replacement over multiple iterations.

## Exit codes

- `255`: failed to submit `POST /exec`.
- `3`: WebSocket stream/client-side error.
- `N`: backend-provided exit code (numeric WebSocket close reason).

## Development and tests

From repository root:

```bash
./mvnw -pl wakamiti-cli test
./mvnw -pl wakamiti-cli package
```

On Windows:

```bat
mvnw.cmd -pl wakamiti-cli test
mvnw.cmd -pl wakamiti-cli package
```

The binary is generated in `wakamiti-cli/target/` as `waka` or `waka.exe` depending on platform.
