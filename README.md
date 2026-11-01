# TaskForge API Service

The core control plane and REST API microservice for the TaskForge workflow automation platform.

## Features
- **Authentication & RBAC:** JWT authentication, user registration/login, workspace multi-tenancy, and member role management.
- **Workflow Management:** Workflow definitions, visual DAG representation, validation, cycle detection, and JSON schema verification.
- **Trigger Execution:** Triggers workflow execution runs and records transactional outbox events to Kafka.
- **Flyway Migrations:** Automated PostgreSQL schema migrations for workspaces, users, workflows, and outbox tables.

## Tech Stack
- Java 21 & Spring Boot 3.3.4
- Spring Data JPA & PostgreSQL
- Spring Security & JJWT
- Spring Kafka
- Flyway Database Migrations
- Lombok

## Getting Started

### Prerequisites
- JDK 21
- PostgreSQL
- Apache Kafka

### Running Locally
```bash
./mvnw spring-boot:run
```

Or build the runnable jar:
```bash
./mvnw clean package
java -jar target/taskforge-api-service-0.1.0-SNAPSHOT.jar
```

### Docker
```bash
docker build -t taskforge-api-service .
docker run -p 8080:8080 taskforge-api-service
```
