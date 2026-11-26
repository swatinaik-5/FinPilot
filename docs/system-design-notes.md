# FinPilot – System Design Notes

Each note follows the same shape: **Definition → Why it matters → FinPilot example → Trade-off.**

---

## 1. Horizontal vs vertical scaling

- **Definition:** Vertical scaling means giving one machine more CPU and memory. Horizontal scaling means running more copies of the service and sharing the load between them.
- **Why it matters:** Vertical scaling is simple but has a hard ceiling and leaves a single point of failure. Horizontal scaling can grow almost without limit and survives the loss of one copy, but only if services are stateless, meaning no user session is kept in a copy's memory.
- **FinPilot example:** Run three copies of transaction-service behind the gateway. Any copy can handle any request because the JWT carries the user's identity, so no copy needs to remember the user.
- **Trade-off:** Horizontal scaling adds operational complexity: load balancing, shared state in databases or caches, and more to monitor. For a small load, one bigger machine is often cheaper and simpler.

---

## 2. Load balancing

- **Definition:** A load balancer spreads incoming requests across several copies of a service using a strategy such as round robin (take turns) or least connections (pick the least busy copy).
- **Why it matters:** It prevents any one copy from being overwhelmed, and with health checks it stops sending traffic to a broken copy, so users do not see failures when one instance dies.
- **FinPilot example:** The api-gateway routes requests to transaction-service, and in Kubernetes a Service spreads traffic across its pods and removes pods that fail their readiness probe.
- **Trade-off:** The load balancer itself can become a single point of failure unless it is also redundant, and it adds a small amount of latency to every request.

---

## 3. Caching

- **Definition:** A cache keeps frequently read data in fast memory, such as Redis, so repeated reads skip the slower database.
- **Why it matters:** It cuts response time and database load, which helps meet the 500 ms performance target and protects the database during traffic spikes.
- **FinPilot example:** Cache answers to common banking FAQ questions and document lookups, which change rarely and are safe to serve slightly stale. Do not cache balances, or only with a very short expiry, because a stale balance can mislead a customer or allow an overdraft.
- **Trade-off:** The hard part is invalidation: the cache must be cleared or updated when the underlying data changes, otherwise users see stale data. Caching also adds another component to run and monitor.

---

## 4. CAP theorem and eventual consistency

- **Definition:** When a network partition splits a distributed system, it must choose between consistency (every read sees the latest write) and availability (every request gets an answer, possibly stale). Eventual consistency means replicas may disagree briefly but converge over time.
- **Why it matters:** Partitions and failures are normal in distributed systems, so this choice has to be made on purpose for each kind of data instead of discovered during an outage.
- **FinPilot example:** Money movement chooses consistency: refusing a transfer during a failure is better than showing a wrong balance or double-spending. Read-only features such as FAQ search can choose availability, and "transfer completed" notifications can be eventually consistent.
- **Trade-off:** Choosing consistency means some requests fail or wait during problems, which hurts availability. Choosing availability means accepting temporarily stale or conflicting data, which is unacceptable for balances.

---

## 5. Interview question

**"Why can't you use one database transaction across two microservices?"**

**My answer (about 60 seconds when spoken):**

In a microservices system each service owns its own database, so a normal ACID transaction cannot span two services: there is no single database to hold the transaction open across both. In FinPilot, a transfer between customers touches balances in account-service and a transfer record in transaction-service. If I debit one and then the other call fails, money could be taken from one account and never credited to the other, and there is no automatic rollback across the two databases.

The usual alternatives are two-phase commit, which is slow, fragile and tightly couples the services, or the Saga pattern. With a Saga, the transfer becomes a sequence of local transactions, and if a step fails, I run compensating actions, such as reversing the debit, to undo the earlier steps. I would pair that with an idempotency key on every transfer request so retries never apply twice, and with clear statuses like PENDING, COMPLETED and FAILED so the system always knows where a transfer stands. The cost is eventual consistency and more code, which is why I would only choose this design when the services genuinely need to be separate. I will build this in Week 4.

---

## Self-check

- [ ] I can explain each note aloud in under a minute without looking
- [ ] Every note has a FinPilot example, not just a definition
- [ ] Committed and pushed to GitHub
