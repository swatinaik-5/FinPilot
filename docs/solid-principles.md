# FinPilot – SOLID Principles

Each principle has: what it means, a bad example, a better design, and where it applies in FinPilot.

---

## S: Single Responsibility Principle

**Meaning:** A class should have one reason to change.

**Bad:** one class does everything for a transfer.

```java
public class TransferService {
    public void transfer(TransferRequest request) {
        // validate request
        // load accounts and check balance
        // update balances
        // save transaction record
        // write audit log
        // publish event to the message broker
    }
}
```

A change to validation rules, audit format or the broker all touch the same class.

**Better:** each concern has its own class, and `TransferService` coordinates them.

```java
public class TransferService {
    private final TransferValidator validator;
    private final AccountClient accountClient;
    private final TransactionRepository transactions;
    private final AuditLogger auditLogger;
    private final TransferEventPublisher events;

    public Transaction transfer(TransferRequest request) {
        validator.validate(request);
        Transaction txn = transactions.savePending(request);
        accountClient.debitAndCredit(request);
        transactions.markCompleted(txn);
        auditLogger.log("TRANSFER_COMPLETED", txn.getId());
        events.publishCompleted(txn);
        return txn;
    }
}
```

**FinPilot use:** `TransferValidator`, `AuditLogger`, `TransferEventPublisher` and the repositories are separate classes in transaction-service.

---

## O: Open/Closed Principle

**Meaning:** Code should be open for extension but closed for modification: add new behavior without editing tested code.

**Bad:** every new account type edits the same method.

```java
public BigDecimal calculateFee(Account account, BigDecimal amount) {
    if (account.getType() == AccountType.SAVINGS) {
        return amount.multiply(new BigDecimal("0.01"));
    } else if (account.getType() == AccountType.CURRENT) {
        return amount.multiply(new BigDecimal("0.02"));
    }
    throw new IllegalArgumentException("Unknown type");
}
```

**Better:** one strategy per type; adding a type means adding a class.

```java
public interface FeePolicy {
    AccountType supports();
    BigDecimal calculate(BigDecimal amount);
}

@Component
class SavingsFeePolicy implements FeePolicy {
    public AccountType supports() { return AccountType.SAVINGS; }
    public BigDecimal calculate(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.01"));
    }
}
```

**FinPilot use:** fee and limit rules per account type, and later per transfer type (this is also the Strategy pattern, covered on Day 4).

---

## L: Liskov Substitution Principle

**Meaning:** Anywhere a parent type is used, a subtype must work without surprises. A subtype must not break the promises of its parent.

**Bad:** a subtype refuses an operation its parent promises.

```java
public class Account {
    public void withdraw(BigDecimal amount) { /* normal withdrawal */ }
}

public class FixedDepositAccount extends Account {
    @Override
    public void withdraw(BigDecimal amount) {
        throw new UnsupportedOperationException("Locked until maturity");
    }
}
```

Code that loops over `Account` and calls `withdraw` now crashes.

**Better:** only types that can withdraw promise withdrawal.

```java
public interface Withdrawable {
    void withdraw(BigDecimal amount);
}

public class CurrentAccount implements Withdrawable { /* ... */ }
public class FixedDepositAccount { /* no withdraw method */ }
```

**FinPilot use:** model what an account can do, not just what it is. If FinPilot later adds fixed deposits, they must not inherit operations they cannot support.

---

## I: Interface Segregation Principle

**Meaning:** Clients should not be forced to depend on methods they do not use. Prefer several small interfaces to one large one.

**Bad:** one wide interface that everyone depends on.

```java
public interface AccountClient {
    AccountDto getAccount(UUID id);
    BigDecimal getBalance(UUID id);
    void debit(UUID id, BigDecimal amount);
    void credit(UUID id, BigDecimal amount);
}
```

The AI assistant gets access to `debit` and `credit` even though it must never move money.

**Better:** split by capability.

```java
public interface AccountReader {
    AccountDto getAccount(UUID id);
    BigDecimal getBalance(UUID id);
}

public interface AccountWriter {
    void debit(UUID id, BigDecimal amount);
    void credit(UUID id, BigDecimal amount);
}
```

**FinPilot use:** ai-assistant-service depends only on `AccountReader` (and a similar `TransactionReader`). The read-only rule is enforced by the type system, not only by convention.

---

## D: Dependency Inversion Principle

**Meaning:** High-level logic should depend on abstractions, not on concrete low-level details.

**Bad:** business logic is tied to one HTTP client and one broker.

```java
public class TransferService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final KafkaTemplate<String, String> kafka;
    // hard to test, hard to replace
}
```

**Better:** depend on interfaces and inject implementations.

```java
public interface AccountClient { /* debit, credit */ }
public interface TransferEventPublisher { void publishCompleted(Transaction txn); }

public class TransferService {
    private final AccountClient accountClient;
    private final TransferEventPublisher events;

    public TransferService(AccountClient accountClient, TransferEventPublisher events) {
        this.accountClient = accountClient;
        this.events = events;
    }
}
```

`RestAccountClient` and `KafkaTransferEventPublisher` implement them, and unit tests use mocks. Spring's dependency injection supplies the implementations.

**FinPilot use:** switching between Kafka and RabbitMQ (an open question in `architecture.md`) only changes one implementation class.

---

## Interview cheat sheet

| Principle | One-line answer | FinPilot example |
| --- | --- | --- |
| S | One reason to change per class | Validator, audit logger and publisher split out of `TransferService` |
| O | Extend by adding, not by editing | `FeePolicy` implementations per account type |
| L | Subtypes must honor the parent's contract | Fixed deposits do not extend a withdrawable account |
| I | Many small interfaces, not one big one | `AccountReader` vs `AccountWriter` for the assistant |
| D | Depend on abstractions | `AccountClient` and `TransferEventPublisher` interfaces |

**Common follow-up:** "Where did you apply SOLID in a real project?" Answer with the `AccountReader` / `AccountWriter` split: it is concrete, and it ties SOLID to the safety requirement that the assistant never moves money.

---

## Self-check

- [ ] I can explain each principle in one sentence without looking
- [ ] I can give a FinPilot example for each
- [ ] I can explain why the assistant uses `AccountReader` only
- [ ] Committed and pushed to GitHub
