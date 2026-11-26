/**
 * Application layer: use cases (for example OpenAccountUseCase, DebitAccountUseCase) and
 * transaction boundaries.
 *
 * <p>Rule: may depend on the domain layer. Loads aggregates, calls domain behavior, saves.
 * Contains no business rules itself.
 */
package com.finpilot.account.application;
