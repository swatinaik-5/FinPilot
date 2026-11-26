/**
 * Domain layer: entities, value objects (Money, AccountNumber), aggregates (Account),
 * repository interfaces, domain events and domain exceptions.
 *
 * <p>Rule: depends on nothing outside the domain. No Kafka, REST or controller code. JPA
 * annotations on entities are the one accepted compromise (see docs/clean-architecture-ddd.md).
 */
package com.finpilot.account.domain;
