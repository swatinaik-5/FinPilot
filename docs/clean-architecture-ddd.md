# FinPilot – Clean Architecture and DDD Layers

## 1. Clean architecture in one idea

Organize each service in layers, and let dependencies point **inward only**: the business rules in the middle know nothing about databases, HTTP or message brokers.

```
 api  ─────►  application  ─────►  domain  ◄─────  infrastructure
(controllers)   (use cases)     (business rules)   (JPA, REST clients, Kafka)
```

| Layer | Contains | May depend on |
| --- | --- | --- |
| **domain** | Entities, value objects, domain services, repository *interfaces*, domain events | Nothing outside the domain |
| **application** | Use cases (for example `TransferMoney`), transaction boundaries, DTO mapping | domain |
| **infrastructure** | JPA repositories, REST clients and adapters, broker publishers, config | domain and application interfaces |
| **api** | REST controllers, request and response models, exception handlers | application |

The key rule is **Dependency Inversion** (Day 1): the domain defines an interface such as `AccountRepository`, and infrastructure implements it. The business rules never import Spring Data or Kafka classes directly.

## 2. Package structure for account-service

```
com.finpilot.account
├── api
│   ├── AccountController.java
│   ├── dto/            (request and response records)
│   └── error/          (global exception handler)
├── application
│   ├── OpenAccountUseCase.java
│   ├── ApproveAccountRequestUseCase.java
│   └── DebitAccountUseCase.java
├── domain
│   ├── model/          (Account, Customer, Money, AccountNumber)
│   ├── repository/     (AccountRepository interface)
│   ├── event/          (AccountOpenedEvent)
│   └── exception/      (InsufficientFundsException)
└── infrastructure
    ├── persistence/    (JPA repository implementation)
    ├── messaging/      (event publisher implementation)
    └── config/         (security, beans)
```

Use the same structure in transaction-service, so every service reads the same way.

## 3. DDD building blocks in FinPilot

| DDD concept | Meaning | FinPilot example |
| --- | --- | --- |
| **Bounded context** | A boundary inside which a model and its words have one meaning | Each microservice: "account" means something different to account-service and to the AI assistant |
| **Entity** | Object with an identity that persists over time | `Account` (identified by its id) |
| **Value object** | Immutable object defined only by its values, no identity | `Money`, `AccountNumber` |
| **Aggregate** | A cluster of objects changed as one unit, behind one root | `Account` as the aggregate root; all balance changes go through it |
| **Repository** | Collection-like interface for loading and saving aggregates | `AccountRepository` (interface in domain, implementation in infrastructure) |
| **Domain service** | Business logic that does not belong to a single entity | `TransferPolicy` checking rules across two accounts |
| **Domain event** | A fact that happened | `AccountOpenedEvent`, `TransferCompletedEvent` |
| **Application service / use case** | Orchestrates domain objects for one user action | `TransferMoney` |

## 4. Value object: Money

Money is not a bare `BigDecimal`. A value object keeps the amount and currency together and rejects invalid states.

```java
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}
```

This makes "never use floating point, always two decimals, never mix currencies" a property of the type instead of a habit.

## 5. Aggregate root: Account enforces its own rules

Business rules live inside the entity, not scattered across services and controllers.

```java
public class Account {

    private final UUID id;
    private final AccountNumber accountNumber;
    private Money balance;
    private AccountStatus status;

    public Account(UUID id, AccountNumber accountNumber, Money balance, AccountStatus status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
    }

    public void deposit(Money amount) {
        requireActive();
        requirePositive(amount);
        this.balance = balance.add(amount);
    }

    public void withdraw(Money amount) {
        requireActive();
        requirePositive(amount);
        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException(id);
        }
        this.balance = balance.subtract(amount);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id);
        }
    }

    private void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public Money getBalance() { return balance; }
    public UUID getId() { return id; }
}
```

The overdraft rule is now in one place. The database `CHECK (balance >= 0)` from `database-design.md` remains as a second line of defense.

## 6. A use case in the application layer

```java
@Service
public class DebitAccountUseCase {

    private final AccountRepository accounts;   // domain interface

    public DebitAccountUseCase(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public void debit(UUID accountId, Money amount) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.withdraw(amount);
        accounts.save(account);
    }
}
```

The use case loads, calls domain behavior, and saves. It contains no business rules itself and no database code.

## 7. Honest trade-offs

- **JPA annotations:** with Spring Data JPA it is common to put `@Entity` annotations directly on the domain entity. That is a pragmatic compromise. A strict version keeps a separate JPA entity in infrastructure and maps to the domain object, at the cost of extra mapping code. For FinPilot, start pragmatic and keep the domain free of any other framework code.
- **Over-engineering:** simple CRUD (for example reading a customer profile) does not need a full use case and aggregate. Use the rich model where there are real rules: balances, transfers and approvals.
- **Layers cost code:** more files and mapping between DTOs, domain objects and entities. The payoff is code you can test without Spring or a database.

## 8. Testing benefit

- **Domain tests:** plain JUnit, no Spring, no database. For example, `Account.withdraw` rejects an overdraft.
- **Use case tests:** mock `AccountRepository`.
- **Infrastructure tests:** Testcontainers with PostgreSQL.

This maps directly to Week 3's testing days.

---

## Interview cheat sheet

- **What is clean architecture?** Layers with dependencies pointing inward, so business rules do not depend on frameworks, databases or transport.
- **Entity vs value object?** An entity is identified by its id and can change. A value object is defined by its values, is immutable, and two equal ones are interchangeable.
- **What is an aggregate?** A group of objects treated as one unit with a single root that enforces the rules for changes. `Account` is the root for balance changes.
- **Why a `Money` class?** It prevents floating-point errors and currency mix-ups by design.
- **Where do business rules live?** In the domain model, not in controllers or repositories.

---

## Self-check

- [ ] I can draw the four layers and the direction of dependencies
- [ ] I can explain entity, value object, aggregate and repository using FinPilot examples
- [ ] I can explain why `Account.withdraw` holds the overdraft rule
- [ ] Committed and pushed to GitHub
