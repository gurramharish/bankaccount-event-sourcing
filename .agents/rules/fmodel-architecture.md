---
trigger: always_on
---

---
trigger: always_on
description: Architectural constraints for Kotlin Event Sourcing
---
# Kotlin Event Sourcing & CQRS Standards

When writing Kotlin code for this project, you must adhere to the following strict architectural rules:

1. **Purely Functional Domain:** Use the `fmodel` library. The domain must be modeled using pure functions. Use `data class` for State, Commands, and Events. 
2. **The Decider Pattern:** Business logic must reside in an Fmodel `Decider`. Do NOT mutate state inside classes. Always return a list of events from a command, and return a new state from an event fold.
3. **Strict CQRS:** 
   - **Write Side:** Only interacts with EventStoreDB. No current state is ever saved here.
   - **Read Side:** Only reads from PostgreSQL. 
4. **Dependencies:** Prefer lightweight Kotlin libraries. Use Ktor for APIs (if required), Kotlinx Serialization, and Kotlin Coroutines/Flow for asynchronous event subscriptions.