# wakamiti-cli

`waka` es el cliente de linea de comandos de Wakamiti Runtime. No ejecuta la logica de testing localmente: actua como proxy hacia `wakamiti-service`.

## Como funciona

1. Usa los argumentos CLI como una lista ordenada de strings.
2. Envia `POST /exec`.
3. Abre WebSocket en `/exec/out`.
4. Muestra en `stdout` cada linea recibida.
5. Cuando el servidor cierra el socket, traduce el motivo de cierre a codigo de salida del proceso.

Si el usuario pulsa `Ctrl+C`, el cliente envia `STOP` por WebSocket y espera cierre controlado.

## Configuracion

El cliente lee configuracion en este orden:

1. `wakamiti.properties` en el directorio actual.
2. Si no existe, `wakamiti.properties` junto al ejecutable.
3. Si la clave `effective.properties` existe, mezcla ese archivo encima.

Claves requeridas:

- `server.host`
- `server.port`
- `server.auth.origin`

## Carga de propiedades

El parser soporta:

- Comentarios con `#`.
- Escape de caracteres (`\:`, `\=`, `\ `, `\\`, `\#`, `\!`).
- Sustitucion `${user.home}`.
- Sustitucion simple de propiedades `${otra.propiedad}` en varias iteraciones.

## Codigos de salida

- `255`: fallo al enviar `POST /exec`.
- `3`: error de stream WebSocket cliente/red.
- `N`: codigo devuelto por el backend (motivo de cierre numerico del WebSocket).

## Desarrollo y pruebas

Desde raiz del repo:

```bash
./mvnw -pl wakamiti-cli test
./mvnw -pl wakamiti-cli package
```

En Windows:

```bat
mvnw.cmd -pl wakamiti-cli test
mvnw.cmd -pl wakamiti-cli package
```

El binario queda en `wakamiti-cli/target/` como `waka` o `waka.exe` segun plataforma.
