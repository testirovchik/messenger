# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.14 messenger application (Java 17, Maven). Currently a skeleton — no controllers, services, or persistence layers have been implemented yet.

## Commands

```bash
# Run the application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MessengerApplicationTests

# Build a JAR
./mvnw clean package

# Build an OCI image
./mvnw spring-boot:build-image
```

## Architecture

**Entry point**: `com.erik.messenger.MessengerApplication`

**Package root**: `com.erik.messenger`

**Configuration**: `src/main/resources/application.yaml` — currently only sets `spring.application.name: messenger`.

The project follows standard Spring Boot layered conventions. As features are added, expect packages like `controller`, `service`, `repository`, and `model` under the root package.

**Test framework**: JUnit 5 (Jupiter) via `spring-boot-starter-test`, which also includes Mockito and AssertJ.

## Key Dependencies

- `spring-boot-starter` — core Spring Boot (no web layer added yet)
- `spring-boot-starter-test` — testing (test scope)
