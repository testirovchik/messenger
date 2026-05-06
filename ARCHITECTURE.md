# Backend System Architecture

This diagram illustrates the internal flow of the messenger backend, including service discovery, asynchronous messaging, and the observability stack.

```mermaid
graph LR
    %% API Entry Point
    Ingress[Nginx Ingress Controller]

    subgraph "Kubernetes Backend Services"
        %% Microservices
        AuthSvc[Auth Service]
        ChatSvc[Chat Service]
        Notifier[Notifier Service]
        
        %% Databases & Persistence
        AuthDB[(PostgreSQL - Auth)]
        ChatDB[(PostgreSQL - Chat)]
        Redis[(Redis - Cache/PubSub)]
        S3[AWS S3 / MinIO Storage]
        
        %% Message Broker
        Kafka{Kafka Broker}

        %% Communication Links
        Ingress -->|/auth| AuthSvc
        Ingress -->|/api/chats| ChatSvc
        Ingress -->|/ws| ChatSvc

        AuthSvc --> AuthDB
        ChatSvc --> ChatDB
        ChatSvc --> Redis
        ChatSvc -->|Presigned URLs| S3
        
        %% Async Messaging
        ChatSvc -->|Produces Events| Kafka
        Kafka -->|Consumes Events| Notifier
    end

    subgraph "Observability Stack"
        Prom[Prometheus]
        Jaeger[Jaeger/Zipkin]
        Loki[Loki]
        Grafana[Grafana UI]

        %% Scraping & Tracing
        AuthSvc & ChatSvc & Notifier -.->|Metrics| Prom
        AuthSvc & ChatSvc & Notifier -.->|Traces| Jaeger
        AuthSvc & ChatSvc & Notifier -.->|Logs| Loki
        
        %% Visualization
        Prom & Jaeger & Loki --> Grafana
    end

    %% Visual Styles
    style Kafka fill:#f96,stroke:#333,stroke-width:2px
    style Redis fill:#ff4d4d,stroke:#333,stroke-width:2px
    style Grafana fill:#9f9,stroke:#333,stroke-width:2px
    style Ingress fill:#f9f,stroke:#333,stroke-width:2px