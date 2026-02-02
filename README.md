# wakamiti-services

POM padre de Wakamiti Runtime, gestiona los módulos de servicio y CLI.

## Construcción y ejecución

### Construir JRI

Necesitas crear la JRI al iniciar el proyecto para poder usarla en las pruebas durante el desarrollo.
Para hacer esto, ejecuta:
```shell
mvnw -Pjlink initialize
```

También necesitarás configurar el IDE para usar esta JRI al ejecutar las pruebas.

## Arquitectura

```mermaid
sequenceDiagram
    participant Usuario
    participant CLI
    participant Servicio HTTP
    participant Servicio WS

    Usuario->>CLI: Ejecuta comando
    CLI->>Servicio HTTP: POST /comando
    Servicio HTTP-->>CLI: 202 Aceptado

    CLI->>Servicio WS: Abre Socket
    Servicio WS->>CLI: Salida de ejecución (stream)
    CLI->>Usuario: Muestra salida

    alt Usuario pulsa CTRL+C
        Usuario->>CLI: CTRL+C
        CLI->>Servicio WS: Envía "STOP"
        Servicio WS-->>Servicio HTTP: Detiene ejecución
    end

    Servicio WS->>CLI: Cierra Socket
    CLI->>Usuario: Finaliza
```
