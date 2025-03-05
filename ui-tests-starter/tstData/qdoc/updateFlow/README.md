# Spring Boot REST API Service with Health Monitoring and Data Management

This Spring Boot application provides a RESTful API service with health monitoring capabilities and simple data management functionality. It offers a robust foundation for building microservices with built-in health checks and a flexible data storage interface.

The service implements health monitoring endpoints for infrastructure integration and a data management API for storing and retrieving items. Built with Spring Boot 3.2.0 and Java 17, it includes production-ready features through Spring Actuator and comprehensive testing support. The application uses an in-memory data store for demonstration purposes and can be easily extended for production use cases.

## Repository Structure
```
ui-tests-starter/tstData/qdoc/updateFlow/
├── build.gradle                 # Gradle build configuration with Spring Boot 3.2.0 dependencies
├── config/
│   └── application-local.yml    # Local environment configuration with server and logging settings
└── src/com/zetcode/tancode/
    ├── App.java                 # Main Spring Boot application entry point
    ├── controller/
    │   ├── HealthController.java    # Health and status monitoring endpoints
    │   └── SampleController.java    # Data management REST endpoints
    └── model/
        └── DataItem.java            # Data model for item storage
```

## Usage Instructions
### Prerequisites
- Java Development Kit (JDK) 17 or later
- Gradle 7.x or later
- Port 8080 available on the host machine


### Quick Start
1. Start the application:
```bash
./gradlew bootRun
```

2. Verify the service is running:
```bash
curl http://localhost:8080/api/health
```

### More Detailed Examples
1. Check service status:
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

2. Store a data item:
```bash
curl -X PUT http://localhost:8080/api/data/1 \
     -H "Content-Type: application/json" \
     -d '{"content": "Sample content"}'
```

3. Retrieve a data item:
```bash
curl http://localhost:8080/api/data/1
```

### Troubleshooting
1. Service Not Starting
- Problem: Application fails to start
- Diagnosis:
  * Check if port 8080 is already in use
  * Verify Java version with `java -version`
- Solution:
  * Change port in `config/application-local.yml`
  * Update Java installation if needed

2. Debug Mode
- Enable debug logging:
  * Set `logging.level.com.example=DEBUG` in application-local.yml
- Log location: Standard output when running locally
- Monitor application logs:
```bash
tail -f logs/application.log
```

## Data Flow
The application processes REST requests through controllers, managing data items in an in-memory store while providing health monitoring capabilities.

```ascii
Client Request
     │
     ▼
[Spring Boot Server :8080]
     │
     ├─── /api/health, /api/status
     │         │
     │    HealthController
     │
     └─── /api/data/{id}
              │
         SampleController
              │
         In-Memory Store
```

Component Interactions:
1. REST endpoints receive HTTP requests on port 8080
2. HealthController provides system status and health information
3. SampleController manages data items through GET and PUT operations
4. DataItem objects are stored in an in-memory HashMap
5. All responses are returned as JSON with appropriate HTTP status codes
6. Health checks return UP status when service is operational
7. Data operations are synchronized through Spring's request handling
