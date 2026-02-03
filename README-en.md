# wakamiti-services

[![en](https://img.shields.io/badge/lang-es-blue.svg)](https://github.com/wakamiti/wakamiti-runtime/blob/master/README.md)
[![Version](https://img.shields.io/github/v/release/wakamiti/wakamiti-runtime)](https://github.com/wakamiti/wakamiti-runtime/releases)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=wakamiti-runtime&metric=alert_status)](https://sonarcloud.io/dashboard?id=wakamiti-runtime)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=wakamiti-runtime&metric=coverage)](https://sonarcloud.io/dashboard?id=wakamiti-runtime)

Wakamiti Runtime is the execution engine for the Wakamiti testing platform. It consists of a background service
developed with Helidon MicroProfile and a command-line interface (CLI) built in Go. 
The CLI allows for launching test plans, managing plugins, and executing custom commands, communicating asynchronously
with the main service to orchestrate these tasks.

## Architecture

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant Service

    User->>CLI: Run command
    CLI->>Service: POST /command
    Service-->>CLI: 202 Accepted

    CLI->>Service: Socket open
    Service->>CLI: Execution output (stream)
    CLI->>User: Display output

    alt User presses CTRL+C
        User->>CLI: CTRL+C
        CLI->>Service: Sends "STOP"
        Service->>Service: Stops execution
    end

    Service->>CLI: Socket close
    CLI->>User: Finish
```
