# FinPilot

A banking platform built as microservices, with a read-only AI assistant. A portfolio project for Java backend and Gen AI roles.

## Services

| Service | Responsibility | Status |
| --- | --- | --- |
| account-service | Registration, login, customers, accounts, admin approvals | Skeleton |
| transaction-service | Deposits, withdrawals, transfers, history | Planned (Week 4) |
| ai-assistant-service | Chat, RAG, read-only tool calling | Planned (Weeks 7-9) |
| api-gateway | Routing, JWT validation | Planned (Week 4) |

## Tech stack

Java 21, Spring Boot, Maven, PostgreSQL, Flyway. Later: Docker, Kubernetes, Spring AI, pgvector.

## Documentation

All design documents are in [`docs/`](docs/):

- [Requirements](docs/requirements.md)
- [Architecture](docs/architecture.md)
- [Database design](docs/database-design.md)
- [Decision records](docs/decisions.md)
- [System design notes](docs/system-design-notes.md)
- [SOLID principles](docs/solid-principles.md)
- [Creational patterns](docs/creational-patterns.md)
- [Structural patterns](docs/structural-patterns.md)
- [Behavioral patterns](docs/behavioral-patterns.md)
- [Clean architecture and DDD](docs/clean-architecture-ddd.md)

## Code structure (per service)

```
com.finpilot.<service>
├── api              controllers, DTOs, exception handler
├── application      use cases, transaction boundaries
├── domain           entities, value objects, repository interfaces
└── infrastructure   persistence, messaging, REST adapters, config
```

Dependencies point inward: api -> application -> domain <- infrastructure.

## Getting started

Not runnable yet: the database setup arrives in Week 3, Day 1.

```bash
mvn -q clean verify
```

Configuration is read from environment variables; see `.env.example`. Never commit `.env`.

## Roadmap

15-week plan, Week 1 (architecture) and Week 2 (design principles and patterns) complete when this skeleton is committed.
