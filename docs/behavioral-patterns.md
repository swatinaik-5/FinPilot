# FinPilot – Behavioral Design Patterns

Behavioral patterns are about how objects interact and share responsibility. Three matter most for FinPilot: Strategy, Observer and Template Method.

One-line difference to remember:
- **Strategy:** swap an algorithm at runtime.
- **Observer:** notify interested parties when something happens.
- **Template Method:** fix the steps of a process, and let subclasses fill in some of them.

---

## 1. Strategy

**Meaning:** Put each interchangeable algorithm behind a common interface and choose one at runtime.

**Problem in FinPilot:** fees and transfer limits differ by account type. A long `if/else` would need editing every time a rule or type is added. (`FeePolicy` appeared on Day 1 as an Open/Closed example. It is a Strategy.)

**Design:**

```java
public interface FeePolicy {
    AccountType supports();
    BigDecimal calculate(BigDecimal amount);
}

@Component
class SavingsFeePolicy implements FeePolicy {
    public AccountType supports() { return AccountType.SAVINGS; }
    public BigDecimal calculate(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
    }
}

@Component
class CurrentFeePolicy implements FeePolicy {
    public AccountType supports() { return AccountType.CURRENT; }
    public BigDecimal calculate(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
    }
}
```

```java
@Service
public class FeeService {

    private final Map<AccountType, FeePolicy> policies;

    public FeeService(List<FeePolicy> all) {
        this.policies = all.stream().collect(Collectors.toMap(FeePolicy::supports, p -> p));
    }

    public BigDecimal feeFor(AccountType type, BigDecimal amount) {
        FeePolicy policy = policies.get(type);
        if (policy == null) {
            throw new IllegalArgumentException("No fee policy for " + type);
        }
        return policy.calculate(amount);
    }
}
```

Rounding is explicit (`setScale` with `RoundingMode`), as money requires.

**Other FinPilot uses:** transfer limit rules per account type, and a list of validation rules (`SufficientBalanceRule`, `ActiveAccountRule`, `DailyLimitRule`) that a validator runs one after another.

**Strategy vs Factory:** the factory (Day 2) chooses *which object to use*. The strategy *is* the interchangeable behavior. They often work together, as above.

**Trade-off:** more small classes. Not worth it for a rule that will never vary.

---

## 2. Observer

**Meaning:** When something happens, the subject notifies its listeners, without knowing who they are.

**Problem in FinPilot:** after a transfer completes, several things must happen: write an audit entry, send a notification, update statistics. Putting all of that inside `TransferService` couples it to everything and breaks the Single Responsibility Principle.

**Design inside one service (Spring events):**

```java
public record TransferCompletedEvent(UUID transactionId, UUID fromAccountId,
                                     UUID toAccountId, BigDecimal amount) {}
```

```java
@Service
public class TransferService {

    private final ApplicationEventPublisher events;

    public TransferService(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Transactional
    public Transaction complete(Transaction txn) {
        // ... mark the transaction as COMPLETED ...
        events.publishEvent(new TransferCompletedEvent(
                txn.getId(), txn.getFromAccountId(), txn.getToAccountId(), txn.getAmount()));
        return txn;
    }
}
```

```java
@Component
class AuditListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TransferCompletedEvent event) { /* write audit entry */ }
}

@Component
class NotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TransferCompletedEvent event) { /* publish to the message broker */ }
}
```

`AFTER_COMMIT` matters: listeners run only if the database transaction actually committed, so you never announce a transfer that was rolled back.

**Across services:** the message broker (Kafka or RabbitMQ) is the same idea at a larger scale: transaction-service publishes "TransferCompleted" and other services subscribe, without transaction-service knowing who they are.

**Trade-off:** control flow is harder to follow because the effects are indirect, and failed listeners can fail silently. For events that must not be lost, the broker route needs retries, and a durable pattern such as an outbox (a later topic) for strong guarantees.

---

## 3. Template Method

**Meaning:** A base class defines the fixed sequence of steps; subclasses supply the steps that differ.

**Problem in FinPilot:** deposits, withdrawals and transfers all follow the same skeleton: validate, create a pending record, execute, mark the result. Only the middle differs.

**Design:**

```java
public abstract class AbstractTransactionHandler implements TransactionHandler {

    private final TransactionRepository transactions;

    protected AbstractTransactionHandler(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    // The template: fixed order, cannot be overridden
    @Override
    public final Transaction handle(TransactionRequest request) {
        validate(request);
        Transaction txn = transactions.savePending(request);
        try {
            execute(txn);
            return transactions.markCompleted(txn);
        } catch (RuntimeException e) {
            transactions.markFailed(txn, e.getMessage());
            throw e;
        }
    }

    // Steps that differ per type
    protected abstract void validate(TransactionRequest request);
    protected abstract void execute(Transaction txn);
}
```

```java
@Component
class DepositHandler extends AbstractTransactionHandler {
    DepositHandler(TransactionRepository transactions) { super(transactions); }

    public TransactionType supports() { return TransactionType.DEPOSIT; }
    protected void validate(TransactionRequest request) { /* amount > 0, account active */ }
    protected void execute(Transaction txn) { /* credit the account */ }
}
```

**Why it helps:** the order of steps and the failure handling are written once, so no handler can forget to record a failure.

**Trade-off:** it uses inheritance, which couples subclasses to the base class. When the variation is only one or two steps, a Strategy (composition) is often cleaner. Use Template Method when the skeleton is genuinely stable.

---

## Where each pattern lives in FinPilot

| Pattern | Where | Why |
| --- | --- | --- |
| Strategy | `FeePolicy` implementations in `FeeService`; validation rules | Rules vary by account type and grow over time |
| Observer | `TransferCompletedEvent` with listeners; the message broker across services | Audit, notification and statistics react without being called directly |
| Template Method | `AbstractTransactionHandler` | One fixed transaction skeleton shared by deposit, withdrawal and transfer |

---

## Interview cheat sheet

- **Strategy vs Template Method?** Strategy swaps a whole algorithm through composition. Template Method fixes the skeleton in a base class and varies steps through inheritance.
- **Why `AFTER_COMMIT` for listeners?** So effects such as notifications only happen once the data is safely committed.
- **Observer vs message broker?** Same idea; the broker works across processes and services, and adds delivery concerns such as retries and ordering.
- **Where did you use these?** Strategy for fee and limit rules per account type, Observer for post-transfer audit and notifications, Template Method for the shared transaction flow.

---

## Self-check

- [ ] I can explain Strategy, Observer and Template Method in one sentence each
- [ ] I know where FinPilot uses each one
- [ ] I can explain why listeners run after commit
- [ ] Committed and pushed to GitHub
