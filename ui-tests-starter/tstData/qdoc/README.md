# Spring Boot REST API for Simple Data Management

This Spring Boot application provides a RESTful API for managing data items with in-memory storage. It offers a lightweight and efficient way to store and retrieve data items through HTTP endpoints, making it ideal for prototyping and development environments.

The application is built using Spring Boot 3.2.0 and Java 17, featuring a clean architecture with separate controller and model layers. It includes built-in monitoring capabilities through Spring Actuator and comprehensive logging configuration. The REST API supports basic CRUD operations with JSON payloads, making it easy to integrate with various client applications.

## Repository Structure
```
.
├── build.gradle                 # Gradle build configuration with Spring Boot dependencies
├── config/
│   └── application-local.yml    # Local environment configuration (port, app name, logging)
└── src/
    └── com/example/
        ├── App.java            # Main application entry point with Spring Boot configuration
        ├── controller/
        │   └── SampleController.java    # REST endpoints for data management
        └── model/
            └── DataItem.java   # Data model class for storing items
```

## Usage Instructions

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Gradle 8.x (or use the included Gradle wrapper)

### Installation
1. Clone the repository:
```bash
git clone <repository-url>
cd <repository-name>
```

2. Build the application:
```bash
./gradlew build
```

### Quick Start
1. Start the application:
```bash
./gradlew bootRun
```

2. The application will be available at `http://localhost:8080`

### More Detailed Examples

#### Creating or Updating a Data Item
```bash
curl -X PUT http://localhost:8080/api/data/1 \
  -H "Content-Type: application/json" \
  -d '{"content": "Sample content"}'
```

Response:
```json
{
    "id": "1",
    "content": "Sample content"
}
```

#### Retrieving a Data Item
```bash
curl http://localhost:8080/api/data/1
```

Response:
```json
{
    "id": "1",
    "content": "Sample content"
}
```

### Troubleshooting

#### Common Issues

1. Application Fails to Start
- Problem: Port 8080 is already in use
- Error message: `Web server failed to start. Port 8080 was already in use.`
- Solution: Modify the port in `config/application-local.yml`:
```yaml
server:
  port: 8081
```

2. Debugging
- Enable debug logging by modifying `config/application-local.yml`:
```yaml
logging:
  level:
    com.example: DEBUG
```
- Debug logs will be available in the console output
- Monitor actuator endpoints at `http://localhost:8080/actuator` for application health

## Data Flow

The application implements a simple data flow where REST requests are processed through controllers and stored in an in-memory map structure.

```ascii
Client Request → REST Controller → In-Memory Storage
     ↑                                    |
     └────────────────────────────────────┘
         Response with stored data
```

Component interactions:
1. Client sends HTTP requests to REST endpoints
2. SampleController handles request validation and routing
3. Data is stored in an in-memory HashMap within the controller
4. Responses are returned as JSON using Spring's ResponseEntity
5. Error handling is managed through HTTP status codes (200 OK, 404 Not Found)