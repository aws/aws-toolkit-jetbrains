# Spring Boot REST API for Simple Data Management

This Spring Boot application provides a lightweight REST API for managing data items with a simple key-value storage mechanism. It offers a clean and efficient way to store and retrieve data items through RESTful endpoints with built-in monitoring capabilities via Spring Actuator.

The application implements a RESTful service with in-memory storage, making it ideal for prototyping, testing, or scenarios requiring temporary data persistence. It features comprehensive logging configuration, health monitoring through Spring Actuator, and follows Spring Boot best practices for configuration management. The service is built using Java 17 and managed with Gradle, ensuring modern Java features and reliable dependency management.

## Repository Structure
```
.
├── build.gradle                 # Gradle build configuration with Spring Boot dependencies
├── config/
│   └── application-local.yml    # Local environment configuration (port, logging, app name)
└── src/
    └── com/example/
        ├── App.java             # Main application entry point with Spring Boot configuration
        ├── controller/
        │   └── SampleController.java    # REST endpoints for data management
        └── model/
            └── DataItem.java    # Data model class for storing items
```

## Usage Instructions

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Gradle 8.x (or use the included Gradle wrapper)

2. The application will be available at `http://localhost:8080`

### More Detailed Examples

#### Storing a Data Item
```bash
curl -X PUT http://localhost:8080/api/data/123 \
  -H "Content-Type: application/json" \
  -d '{"content": "Sample content"}'
```

Expected response:
```json
{
  "id": "123",
  "content": "Sample content"
}
```

#### Retrieving a Data Item
```bash
curl http://localhost:8080/api/data/123
```

Expected response:
```json
{
  "id": "123",
  "content": "Sample content"
}
```

### Troubleshooting

#### Common Issues

1. Application fails to start
- **Problem**: Port 8080 already in use
- **Solution**: Modify the port in `config/application-local.yml`:
```yaml
server:
  port: 8081
```

2. Logging issues
- **Problem**: Insufficient logging information
- **Solution**: Adjust logging levels in `application-local.yml`:
```yaml
logging:
  level:
    com.example: DEBUG
```

#### Debugging
- Enable debug logging by adding `--debug` flag:
```bash
./gradlew bootRun --debug
```
- View logs in console output
- Application logs are written to standard output and can be redirected to a file:
```bash
./gradlew bootRun > application.log
```

## Data Flow

The application implements a simple data flow where REST requests are processed through controllers and stored in an in-memory map structure.

```ascii
Client Request → REST Controller → In-Memory Storage
     ↑                                    |
     └────────────────────────────────────┘
         Response with stored data
```

Key component interactions:
1. REST requests are received by `SampleController`
2. Controller methods handle GET and PUT operations
3. Data is stored in an in-memory HashMap within the controller
4. Responses are wrapped in Spring's ResponseEntity for proper HTTP status codes
5. Spring Boot handles JSON serialization/deserialization automatically
