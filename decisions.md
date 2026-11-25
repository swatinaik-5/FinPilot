# FinPilot – Architecture Decision Records

Each record states the context, the decision, the alternatives considered, and the consequences, so the reasoning can be defended later.

---

## ADR-001: Use a microservices architecture

**Status:** Accepted

**Context:** FinPilot is a banking application with accounts, transactions, security and an AI assistant. It is a portfolio project meant to demonstrate production-grade skills that remote Java roles ask for.

**Decision:** Build FinPilot as separate services: api-gateway, account-service, transaction-service and ai-assistant-service, each with its own database.

**Alternatives considered:**
- *Monolith:* simplest to build and deploy, but demonstrates little about distributed design.
- *Modular monolith:* the pragmatic choice for a system this size and the same module boundaries, but it would not exercise gateways, events, Saga or Kubernetes.

**Consequences:**
- (+) Each service can be developed, tested, deployed and scaled on its own.
- (+) The AI service, which has a different resource profile, is isolated from core banking.
- (-) More complexity than the problem strictly needs.
- (-) Transfers span services and databases, so consistency needs the Saga pattern, idempotency keys and careful failure handling.

---

## ADR-002: Use PostgreSQL for all services

**Status:** Accepted

**Context:** Banking data is structured and relational. Balances and transfers need strong consistency, and rules such as "balance never below zero" must hold even if application code has a bug.

**Decision:** Use PostgreSQL in every service, one database per service. Use the pgvector extension for the AI service's embeddings.

**Alternatives considered:**
- *MySQL:* comparable for relational data, but PostgreSQL's pgvector support lets one database technology cover both banking data and vector search.
- *MongoDB or another NoSQL store:* flexible schema and easy horizontal scaling, but weaker multi-row transaction and constraint support, which are the core needs here.
- *Dedicated vector database:* an extra component to run for a small document set.

**Consequences:**
- (+) ACID transactions, foreign keys and `CHECK` / `UNIQUE` constraints protect financial data at the database level.
- (+) One technology to learn, run in Docker, and test with Testcontainers; free and open source.
- (+) pgvector avoids adding a separate vector database.
- (-) Scaling writes horizontally is harder than with some NoSQL stores; not a concern at this project's scale.

---

## ADR-003: Use JWT for authentication and authorization

**Status:** Accepted

**Context:** Several services must know who the caller is and what role they have. The AI assistant must call other services on behalf of the user and be limited to that user's data.

**Decision:** account-service authenticates users and issues signed JWT access tokens (asymmetric signing, RS256) with a short expiry, plus a refresh token. The api-gateway validates tokens, and each service verifies the signature with the public key and enforces role and ownership rules. The assistant forwards the caller's own token.

**Alternatives considered:**
- *Server-side sessions:* easy to revoke, but they need shared session storage and make horizontal scaling harder.
- *Opaque tokens with a token introspection call:* easy to revoke, but add a network call to a central service on every request.
- *External identity provider (for example Keycloak):* production-grade, but adds a large component and learning load to this project.

**Consequences:**
- (+) Stateless: any service copy can validate a token without shared session storage, which supports horizontal scaling.
- (+) Asymmetric signing means services only need the public key and cannot forge tokens.
- (+) The assistant's access is naturally limited to the caller's data.
- (-) A JWT cannot be revoked before it expires, so access tokens stay short-lived (for example 15 minutes) and refresh tokens are stored so they can be revoked.
- (-) Keys and secrets must be managed carefully: loaded from environment variables or a secret store, never committed.
- (-) Tokens carry only minimal claims (user id, role) and no sensitive data.

---

## ADR-004: account-service owns balances

**Status:** Accepted

**Context:** A transfer changes balances and creates a transaction record. These live in different services, so one service has to be the source of truth for the balance.

**Decision:** account-service owns and updates balances. transaction-service owns the record of transactions and coordinates transfers by calling account-service to debit and credit.

**Alternatives considered:**
- *transaction-service owns a ledger and derives balances:* the more accurate accounting model, but heavier to build and query.
- *Both services hold balances:* creates duplicated, conflicting data.

**Consequences:**
- (+) A single source of truth for each balance; simple reads for the balance API and the AI assistant.
- (+) Overdraft protection sits next to the data: a `balance >= 0` constraint plus optimistic locking.
- (-) A transfer is a distributed operation, solved with a Saga and compensating actions (Week 4).
- (-) Balance history is rebuilt from transaction records rather than from a ledger.
