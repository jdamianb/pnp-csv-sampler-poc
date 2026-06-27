# Architecture Decision Records

## ADR-001: Plain Java CLI over Spring Boot

**Status:** Accepted  
**Context:** The PoC needs a minimal CLI tool. Spring Boot adds significant startup overhead, dependency footprint, and complexity for no benefit in a single-purpose CLI.  
**Decision:** Use a plain `main()` method with `java.io` classes.  
**Consequences:** No auto-configuration, no DI, no actuator — but also no bloat. The resulting JAR with shade plugin is ~2.5 MB instead of ~20+ MB with Spring Boot.

## ADR-002: Sampler does not parse CSV fields

**Status:** Accepted  
**Context:** The sampler's purpose is to provide raw data for a downstream LLM. Parsing would discard formatting, normalize content, and potentially lose information the LLM needs.  
**Decision:** Treat every line as an opaque `String`. Never split, trim, or interpret.  
**Consequences:** Lines are preserved byte-accurately. No parsing bugs. The output can be fed directly to an LLM without reconstruction.

## ADR-003: LLM integration deferred

**Status:** Accepted  
**Context:** Mixing LLM calls with the sampler creates coupling, slows iteration, and adds API key management complexity.  
**Decision:** Build the sampler as a standalone component. The LLM phase is explicitly out of scope.  
**Consequences:** The sampler is reusable, testable, and can be swapped for a different implementation without touching LLM logic.

## ADR-004: Jackson over manual JSON

**Status:** Accepted  
**Context:** The output JSON shape is small and stable. Manual `StringBuilder` JSON would avoid a dependency.  
**Decision:** Use Jackson for correctness and maintainability. Jackson handles escaping, pretty-printing, and schema evolution automatically.  
**Consequences:** One extra dependency (`jackson-databind` ~1 MB). No risk of malformed JSON from string concatenation.

## ADR-005: Ring buffer over full-file list

**Status:** Accepted  
**Context:** Files can be gigabytes in size. Storing all lines in memory is not viable.  
**Decision:** Use an `ArrayDeque` with a fixed maximum size as a ring buffer for the tail sample.  
**Consequences:** Memory usage is `O(firstCount + lastCount)` regardless of file size. Implementation is ~3 lines of code.
