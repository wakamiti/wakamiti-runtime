# wakamiti-services

Wakamiti Runtime parent POM, manages service and CLI modules.

## Build and run

### Building a Custom Runtime Image

You need to create the JRI when starting the project so that you can use it in tests during development. 
To do this, run: 
```shell
mvnw -Pjlink initialize
```

You will also need to configure the IDE to use this JRI when running tests.

## Architecture

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant Service HTTP
    participant Service WS

    User->>CLI: Run command
    CLI->>Service HTTP: POST /command
    Service HTTP-->>CLI: 202 Accepted

    CLI->>Service WS: Socket open
    Service WS->>CLI: Execution output (stream)
    CLI->>User: Display output

    alt User presses CTRL+C
        User->>CLI: CTRL+C
        CLI->>Service WS: Sends "STOP"
        Service WS->>Service HTTP: Stops execution
    end
    
    Service WS->>CLI: Socket close
    CLI->>User: Finish
```
