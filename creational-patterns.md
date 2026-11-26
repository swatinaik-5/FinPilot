# FinPilot – Creational Design Patterns

Creational patterns are about how objects get created. Three matter most for FinPilot: Factory, Builder and Singleton.

---

## 1. Factory

**Meaning:** Move the decision of *which* object to create (or which implementation to use) into one place, so callers do not use `if/else` or `new` on concrete classes.

**Problem in FinPilot:** transaction-service handles deposits, withdrawals and transfers. Each has different rules. Without a factory, the service fills up with `if (type == ...)` chains, which also breaks the Open/Closed Principle from Day 1.

**Design:**

```java
public interface TransactionHandler {
    TransactionType supports();
    Transaction handle(TransactionRequest request);
}

@Component
class DepositHandler implements TransactionHandler {
    public TransactionType supports() { return TransactionType.DEPOSIT; }
    public Transaction handle(TransactionRequest request) { /* deposit rules */ return null; }
}

@Component
class WithdrawalHandler implements TransactionHandler { /* ... */ }

@Component
class TransferHandler implements TransactionHandler { /* ... */ }
```

```java
@Component
public class TransactionHandlerFactory {

    private final Map<TransactionType, TransactionHandler> handlers;

    public TransactionHandlerFactory(List<TransactionHandler> all) {
        this.handlers = all.stream()
                .collect(Collectors.toMap(TransactionHandler::supports, h -> h));
    }

    public TransactionHandler forType(TransactionType type) {
        TransactionHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported transaction type: " + type);
        }
        return handler;
    }
}
```

Usage in the service: `factory.forType(request.type()).handle(request);`

**Why it helps:** adding a new transaction type means adding one `@Component` class. Nothing existing is edited, and Spring wires it in automatically.

**Trade-off:** more classes for a small number of types. It pays off once the types have real, different logic.

---

## 2. Builder

**Meaning:** Build a complex object step by step, with readable code and validation before the object exists.

**Problem in FinPilot:** a `Transaction` has many fields, some optional depending on type (see `database-design.md`: deposits have no source account, withdrawals have no destination). A constructor with ten parameters is easy to call in the wrong order.

**Design (written by hand, no Lombok needed):**

```java
public final class Transaction {
    private final UUID id;
    private final String referenceId;
    private final TransactionType type;
    private final UUID fromAccountId;
    private final UUID toAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionStatus status;
    private final UUID initiatedBy;
    private final String idempotencyKey;

    private Transaction(Builder b) {
        this.id = b.id;
        this.referenceId = b.referenceId;
        this.type = b.type;
        this.fromAccountId = b.fromAccountId;
        this.toAccountId = b.toAccountId;
        this.amount = b.amount;
        this.currency = b.currency;
        this.status = b.status;
        this.initiatedBy = b.initiatedBy;
        this.idempotencyKey = b.idempotencyKey;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String referenceId;
        private TransactionType type;
        private UUID fromAccountId;
        private UUID toAccountId;
        private BigDecimal amount;
        private String currency;
        private TransactionStatus status = TransactionStatus.PENDING;
        private UUID initiatedBy;
        private String idempotencyKey;

        public Builder type(TransactionType type) { this.type = type; return this; }
        public Builder from(UUID accountId) { this.fromAccountId = accountId; return this; }
        public Builder to(UUID accountId) { this.toAccountId = accountId; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder initiatedBy(UUID userId) { this.initiatedBy = userId; return this; }
        public Builder idempotencyKey(String key) { this.idempotencyKey = key; return this; }
        public Builder referenceId(String referenceId) { this.referenceId = referenceId; return this; }

        public Transaction build() {
            Objects.requireNonNull(type, "type is required");
            Objects.requireNonNull(amount, "amount is required");
            if (amount.signum() <= 0) {
                throw new IllegalStateException("amount must be positive");
            }
            if (type == TransactionType.TRANSFER
                    && (fromAccountId == null || toAccountId == null
                        || fromAccountId.equals(toAccountId))) {
                throw new IllegalStateException("transfer needs two different accounts");
            }
            return new Transaction(this);
        }
    }
}
```

Usage:

```java
Transaction txn = Transaction.builder()
        .type(TransactionType.TRANSFER)
        .from(fromId)
        .to(toId)
        .amount(new BigDecimal("150.00"))
        .currency("QAR")
        .initiatedBy(userId)
        .idempotencyKey(key)
        .build();
```

**Why it helps:** readable call sites, safe defaults, and validation that mirrors the database `CHECK` constraints, so invalid objects fail early in Java before reaching the database.

**Modern alternative:** for simple immutable data (requests, responses, DTOs) use Java records. Use a builder when there are many optional fields or validation rules.

**Trade-off:** more code than a constructor. Not worth it for classes with two or three fields.

---

## 3. Singleton

**Meaning:** Exactly one instance of a class exists, shared everywhere.

**Classic form (know it for interviews):**

```java
public enum AppClock {
    INSTANCE;
    public Instant now() { return Instant.now(); }
}
```

Other known forms are lazy holder and double-checked locking, but the enum form is the simplest thread-safe version in Java.

**In FinPilot you do not write singletons by hand.** Spring beans are singletons by default: one instance per application, shared by all requests.

```java
@Service
public class TransferService { /* one instance, injected where needed */ }

@Configuration
class TimeConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
```

**Rules that follow from this:**
- A singleton bean is shared by many threads, so it must be **stateless** or thread-safe. Never keep per-user data in a field of a `@Service`. This is the same stateless rule behind horizontal scaling in `system-design-notes.md`.
- Prefer injected beans over static singletons: static state is hard to replace in tests and hides dependencies (the Dependency Inversion Principle from Day 1).
- Injecting a `Clock` instead of calling `Instant.now()` directly makes time-based logic (token expiry, account lockout) testable.

**Trade-off:** singletons are global shared state, which is why they are often called an anti-pattern when written by hand. Spring's container-managed version avoids most of that.

---

## Where each pattern lives in FinPilot

| Pattern | Where | Why |
| --- | --- | --- |
| Factory | `TransactionHandlerFactory` in transaction-service | Choose deposit, withdrawal or transfer logic by type without `if/else` |
| Builder | `Transaction` (and `AuditLogEntry`) | Many optional fields and validation before the object exists |
| Singleton | Spring beans such as `TransferService` and `Clock` | One shared stateless instance, managed by the container |

---

## Interview cheat sheet

- **Factory vs Builder?** A factory decides *which* object to create. A builder controls *how* one complex object is assembled.
- **Is Spring's singleton the same as the Singleton pattern?** Similar idea, different mechanism. Spring guarantees one instance per container, not per JVM, and it is managed and injectable. The classic pattern enforces it with a static instance inside the class.
- **Danger of singletons?** Shared mutable state and thread-safety bugs, so keep them stateless.
- **Which pattern did you use and why?** Factory for transaction types: new types are added by adding a class, with no edits to existing code.

---

## Self-check

- [ ] I can explain Factory, Builder and Singleton in one sentence each
- [ ] I know where FinPilot uses each one
- [ ] I can explain why a `@Service` must not hold per-user state
- [ ] Committed and pushed to GitHub
