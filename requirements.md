# FinPilot – Requirements

## 1. Purpose

FinPilot is a banking application that lets customers securely manage their bank accounts and perform basic banking operations: deposits, withdrawals, money transfers and transaction tracking.

It is a role-based system: customers manage their finances, admins verify customers and manage access, and an AI assistant helps users with banking-related questions.

All money movement is **simulated**. There is no real payment processing or integration with external banks.

## 2. Users

- **Customer:** registers, logs in, requests account opening, deposits, withdraws, transfers money, views balances and transaction history, and uses the AI assistant.
- **Admin:** verifies customer registrations, approves or rejects account opening requests, manages customer accounts and controls user permissions.

## 3. Functional Requirements

### Accounts

1. Customers can request to open a bank account after registering with FinPilot.
2. Admins can verify customers and approve or reject account opening requests. Verification is simulated (no real identity document checks).
3. Customers can view their account details, balances and list of active accounts.
4. Customers can deposit and withdraw money, subject to account status and balance rules. Deposits and withdrawals are simulated (for example, test funds credited through a simulated branch deposit).

### Transactions

5. Customers can transfer money between their own accounts and to other FinPilot customers. A recipient is identified by account number only; the system never exposes another customer's personal details.
6. The system maintains a transaction history containing transaction type, amount, date, status and reference ID. Statuses: `PENDING`, `COMPLETED`, `FAILED`.
7. The system rejects withdrawals and transfers that exceed the available balance.
8. The system prevents invalid and duplicate transactions. Every transfer request carries an idempotency key, so a retried request never executes twice.
9. All amounts use a single currency (QAR assumed; change if needed) and are stored with two decimal places using `BigDecimal`, never floating point.

### Security

10. Customers can securely register and log in.
11. Login uses tokens with an expiry, and accounts are temporarily locked after repeated failed login attempts.
12. Customers can access only their own accounts, transactions and personal information.
13. Admin operations are protected by role-based access control (RBAC); only authorized admins can perform administrative actions.

### AI Assistant

14. The AI assistant answers banking-related questions using approved banking documents and knowledge sources.
15. The AI assistant can retrieve a customer's balance and recent transactions through authorized, read-only access, without performing any financial operation.

## 4. Non-Functional Requirements

| Area | Target |
| --- | --- |
| Performance | 95% of API requests respond within 500 ms under normal load, excluding AI responses. |
| Availability | Design target of 99.5% service availability (design target only; the project runs locally). |
| Security | Passwords are securely hashed; every API enforces authentication and authorization. |
| Data integrity | Financial transactions are atomic and consistent; no partial transfers, no duplicate operations. |
| Scalability | Supports 100 concurrent users and 50 transfers per second in a local load test. |
| Reliability | A failed transfer leaves both account balances unchanged; requests are validated; failures are handled gracefully. |
| Maintainability | Modular architecture, clean code, automated tests with at least 70% coverage on service classes. |
| Auditability | All financial transactions and admin actions are traceable through audit logs. |
| Observability | Health checks, structured logs and request tracing across services. |

### AI-specific non-functional requirements

| Area | Target |
| --- | --- |
| Source citation | Every document-based answer cites its source document. |
| Honesty | The assistant says it does not know when the documents do not cover the question. |
| Access control | The assistant uses the caller's own login token, so it can only see that customer's data. |
| Privacy | Account numbers and personal data are never written to logs. |
| Safety | The assistant resists prompt-injection attempts and can never trigger a financial action. |
| Responsiveness | First token within 5 seconds in a local run. |

## 5. Out of Scope

- Real-world banking integration and actual payment processing
- Integration with external banks or payment gateways
- Real identity document verification
- Mobile application development (Android/iOS)
- Loan application, approval and repayment management
- Credit card management
- International money transfers
- Automated AI-initiated transactions or withdrawals
- Production banking compliance and regulatory certification
