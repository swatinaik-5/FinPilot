# FinPilot – Structural Design Patterns

Structural patterns are about how classes and objects are put together. Three matter most for FinPilot: Adapter, Facade and Decorator.

One-line difference to remember:
- **Adapter** changes an interface so two things that do not fit can work together.
- **Facade** hides a complicated set of things behind one simple interface.
- **Decorator** adds behavior to an object while keeping the same interface.

---

## 1. Adapter

**Meaning:** Convert one interface into the interface the caller expects.

**Problem in FinPilot:** transaction-service must call account-service, whose REST responses use account-service's own JSON shape. If transaction-service uses those response classes everywhere, any change in account-service ripples through the whole transaction code.

**Design:** transaction-service defines the interface it wants, and an adapter translates to and from the remote API.

```java
// What transaction-service wants to see (its own model)
public record AccountSnapshot(UUID id, BigDecimal balance, String currency, boolean active) {}

public interface AccountClient {
    AccountSnapshot getAccount(UUID accountId);
    void debit(UUID accountId, BigDecimal amount, String idempotencyKey);
    void credit(UUID accountId, BigDecimal amount, String idempotencyKey);
}
```

```java
// The adapter: speaks account-service's REST API, returns transaction-service's model
@Component
class RestAccountClientAdapter implements AccountClient {

    private final RestClient restClient;

    RestAccountClientAdapter(RestClient.Builder builder,
                             @Value("${services.account.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public AccountSnapshot getAccount(UUID accountId) {
        AccountResponse response = restClient.get()
                .uri("/accounts/{id}", accountId)
                .retrieve()
                .body(AccountResponse.class);
        return new AccountSnapshot(
                response.id(),
                response.balance(),
                response.currency(),
                "ACTIVE".equals(response.status()));
    }

    // debit(...) and credit(...) call POST endpoints in the same way
    @Override public void debit(UUID id, BigDecimal amount, String key) { /* ... */ }
    @Override public void credit(UUID id, BigDecimal amount, String key) { /* ... */ }

    record AccountResponse(UUID id, BigDecimal balance, String currency, String status) {}
}
```

**Why it helps:** the rest of transaction-service sees only `AccountClient` and `AccountSnapshot`. If account-service renames a field or changes an endpoint, only the adapter changes. This also gives you a clean place to mock in tests.

**Also useful for:** wrapping different message brokers behind `TransferEventPublisher`, which makes the Kafka-or-RabbitMQ open question in `architecture.md` a one-class change.

**Trade-off:** an extra layer of translation code. Worth it at every service boundary.

---

## 2. Facade

**Meaning:** Provide one simple entry point in front of several components.

**Problem in FinPilot:** the AI assistant's "show me my financial summary" tool needs account details, balances and recent transactions, which come from two services. If the tool-calling code calls each client directly, it gets cluttered and error handling is repeated.

**Design:**

```java
@Component
public class CustomerOverviewFacade {

    private final AccountReader accountReader;          // from Day 1: read-only
    private final TransactionReader transactionReader;  // read-only

    public CustomerOverviewFacade(AccountReader accountReader,
                                  TransactionReader transactionReader) {
        this.accountReader = accountReader;
        this.transactionReader = transactionReader;
    }

    public CustomerOverview overview(String callerToken) {
        List<AccountDto> accounts = accountReader.listAccounts(callerToken);
        List<TransactionDto> recent = transactionReader.recent(callerToken, 10);
        return new CustomerOverview(accounts, recent);
    }
}
```

The assistant's tool calls `overview(token)` and never deals with two clients, two failure modes or two response formats.

**Another FinPilot use:** the api-gateway itself acts as a facade in front of the services for clients.

**Why it helps:** one simple method for callers, one place to handle partial failures and timeouts. The facade depends only on read-only interfaces, so the assistant still cannot move money.

**Trade-off:** a facade can grow into a "god class" that does too much. Keep it to coordination, with real logic left in the services behind it.

---

## 3. Decorator

**Meaning:** Wrap an object to add behavior before or after its work, using the same interface as the original.

**Problem in FinPilot:** every transaction handler (deposit, withdrawal, transfer) must write an audit log entry. Copying audit code into each handler breaks the Single Responsibility Principle and invites mistakes.

**Design:** one decorator adds auditing to any handler.

```java
public class AuditingTransactionHandler implements TransactionHandler {

    private final TransactionHandler delegate;
    private final AuditLogger auditLogger;

    public AuditingTransactionHandler(TransactionHandler delegate, AuditLogger auditLogger) {
        this.delegate = delegate;
        this.auditLogger = auditLogger;
    }

    @Override
    public TransactionType supports() {
        return delegate.supports();
    }

    @Override
    public Transaction handle(TransactionRequest request) {
        try {
            Transaction result = delegate.handle(request);
            auditLogger.log("TRANSACTION_" + result.getStatus(), result.getId());
            return result;
        } catch (RuntimeException e) {
            auditLogger.log("TRANSACTION_FAILED", null);
            throw e;
        }
    }
}
```

It plugs into the factory from Day 2:

```java
@Component
public class TransactionHandlerFactory {

    private final Map<TransactionType, TransactionHandler> handlers;

    public TransactionHandlerFactory(List<TransactionHandler> all, AuditLogger auditLogger) {
        this.handlers = all.stream().collect(Collectors.toMap(
                TransactionHandler::supports,
                h -> new AuditingTransactionHandler(h, auditLogger)));
    }
    // forType(...) unchanged
}
```

The handlers stay focused on business rules, and auditing is added in one place.

**Other FinPilot decorators:** a retry-and-timeout wrapper around `AccountClient`, a metrics wrapper that records latency, and a short-lived cache around FAQ lookups.

**Related:** Spring's `@Transactional` and `@Cacheable` work through proxies that wrap your beans in the same spirit. Knowing this helps you explain why those annotations do not work on a method called from inside the same class.

**Trade-off:** many small wrappers can make the call path harder to trace. Name them clearly and keep the order of wrapping consistent.

---

## Where each pattern lives in FinPilot

| Pattern | Where | Why |
| --- | --- | --- |
| Adapter | `RestAccountClientAdapter` in transaction-service | Isolates transaction-service from account-service's API shape |
| Facade | `CustomerOverviewFacade` in ai-assistant-service | One simple call over two read-only services |
| Decorator | `AuditingTransactionHandler` | Adds audit logging to every handler without touching them |

---

## Interview cheat sheet

- **Adapter vs Facade?** An adapter makes an incompatible interface fit the one you need, usually one-to-one. A facade gives a simpler front for a group of components.
- **Decorator vs inheritance?** A decorator adds behavior at runtime by wrapping, and wrappers can be combined. Inheritance fixes the behavior at compile time and leads to class explosions.
- **Decorator vs Proxy?** Both wrap an object with the same interface. A decorator adds behavior; a proxy controls access (security, lazy loading, transactions).
- **Where did you use these?** Adapter around account-service so API changes stay in one class, and a decorator for audit logging so no handler repeats it.

---

## Self-check

- [ ] I can explain Adapter, Facade and Decorator in one sentence each
- [ ] I know where FinPilot uses each one
- [ ] I can explain the difference between Adapter and Facade
- [ ] Committed and pushed to GitHub
