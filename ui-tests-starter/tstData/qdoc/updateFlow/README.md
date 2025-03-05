# Spring Boot REST API: A Simple and Robust Microservice Template

This Spring Boot application provides a lightweight REST API template with built-in health monitoring and data management capabilities. It offers a production-ready foundation for building microservices with comprehensive health checks, status monitoring, and data operations.

The application implements a RESTful service with health monitoring endpoints and a flexible data management interface. It features configurable logging levels, actuator endpoints for operational insights, and a clean architecture that separates concerns between controllers and data models. Built with Spring Boot 3.2.0, it leverages modern Java 17 features and includes comprehensive test support through JUnit.

## Repository Structure
```
.
├── build.gradle              # Gradle build configuration with Spring Boot 3.2.0 and dependencies
├── config/
│   └── application-local.yml # Local environment configuration (port, app name, logging)
└── src/
    └── com/example/
        ├── App.java         # Main application entry point with Spring Boot configuration
        ├── controller/      # REST API endpoint definitions
        │   ├── HealthController.java  # Health and status monitoring endpoints
        │   └── SampleController.java  # Data management endpoints
        └── model/
            └── DataItem.java          # Data model for API operations
```

## Usage Instructions

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Gradle 8.x or higher
- Basic understanding of Spring Boot and REST APIs


### Quick Start
1. Start the application:
```bash
./gradlew bootRun
```

2. Verify the application is running by accessing the health endpoint:
```bash
curl http://localhost:8080/api/health
```

### More Detailed Examples

1. Check Application Status
```bash
curl http://localhost:8080/api/status
```
Expected response:
```json
{
    "timestamp": "2024-01-01T12:00:00",
    "service": "sample-rest-app",
    "status": "running"
}
```

2. Health Check
```bash
curl http://localhost:8080/api/health
```
Expected response:
```json
{
    "status": "UP",
    "message": "Service is healthy"
}
```

### Troubleshooting

1. Application Won't Start
- **Problem**: Application fails to start with port binding issues
- **Solution**:
  ```bash
  # Check if port 8080 is already in use
  lsof -i :8080
  # Modify port in config/application-local.yml if needed
  ```

2. Debugging
- Enable debug logging by modifying `config/application-local.yml`:
  ```yaml
  logging:
    level:
      com.example: DEBUG
  ```
- Check logs in console output for detailed information
- Use Spring Boot Actuator endpoints for health monitoring:
  ```bash
  curl http://localhost:8080/actuator/health
  ```

## Data Flow

The application processes HTTP requests through a layered architecture, transforming REST calls into data operations with proper error handling and response formatting.

```ascii
Client Request → Controller Layer → Data Processing → Response
     ↑              |                    |              ↓
     └──────────────┴────────Error Handling─────────────┘
```

Key component interactions:
1. Controllers receive HTTP requests and validate inputs
2. Request data is mapped to internal data models
3. Business logic processes the data operations
4. Responses are formatted as JSON and returned to the client
5. Error handling is managed across all layers
6. Health monitoring provides real-time system status
7. Logging captures operation details at configurable levels
