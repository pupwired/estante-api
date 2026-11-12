# estante-api

Ktor API for the Estante e-book reader and library app.

## Building & Running
To build or run the project locally:


| Command                             | Description                              |
|-------------------------------------|------------------------------------------|
| `docker compose up -d`              | Compose and run the PostgreSQL container |
| `./gradlew run`                     | Run the server                           |
| `curl http://localhost:8080/health` | Tests the server health                  |
| `./scripts/smoke-test.sh`           | Runs smoke tests                         |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
