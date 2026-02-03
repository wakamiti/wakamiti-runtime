# wakamiti-services

[![en](https://img.shields.io/badge/lang-en-blue.svg)](https://github.com/wakamiti/wakamiti-runtime/blob/master/README-en.md)
[![Version](https://img.shields.io/github/v/release/wakamiti/wakamiti-runtime)](https://github.com/wakamiti/wakamiti-runtime/releases)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=wakamiti-runtime&metric=alert_status)](https://sonarcloud.io/dashboard?id=wakamiti-runtime)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=wakamiti-runtime&metric=coverage)](https://sonarcloud.io/dashboard?id=wakamiti-runtime)

Wakamiti Runtime es el motor de ejecución para la plataforma de pruebas Wakamiti. Se compone de un servicio en 
background desarrollado con Helidon MicroProfile y una interfaz de línea de comandos (CLI) construida en Go. 
El CLI permite lanzar planes de prueba, gestionar plugins y ejecutar comandos personalizados, comunicándose de forma
asíncrona con el servicio principal para orquestar las tareas.

## Arquitectura

```mermaid
sequenceDiagram
    participant Usuario
    participant CLI
    participant Servicio

    Usuario->>CLI: Ejecuta comando
    CLI->>Servicio: POST /comando
    Servicio-->>CLI: 202 Aceptado

    CLI->>Servicio: Abre Socket
    Servicio->>CLI: Salida de ejecución (stream)
    CLI->>Usuario: Muestra salida

    alt Usuario pulsa CTRL+C
        Usuario->>CLI: CTRL+C
        CLI->>Servicio: Envía "STOP"
        Servicio-->>Servicio: Detiene ejecución
    end

    Servicio->>CLI: Cierra Socket
    CLI->>Usuario: Finaliza
```
