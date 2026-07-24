# ADR 011: Variable Name Collision Detection

## Status
Accepted

## Context
BPMN element IDs are converted to UPPER_SNAKE_CASE constants in generated APIs using `StringUtils.toUpperSnakeCase()`. This normalization replaces separators (hyphens, dots) with underscores and standardizes casing, which can cause silent data loss when different element IDs normalize to the same constant name.

**Examples of collisions:**
- `endEvent_dataProcessed` and `endEvent-dataProcessed` both → `END_EVENT_DATA_PROCESSED`
- `eventData` and `event-data` both → `EVENT_DATA`

Previously, when collisions occurred, only the first value was used during code generation, and subsequent values were silently ignored, leading to incorrect API mappings.

## Decision
Implement comprehensive collision detection that:

1. **Detects all collisions** across all variable mapping types (flow nodes, messages, signals, errors, timers, service tasks, variables) before throwing a single exception with complete details
2. **Distinguishes collisions from duplicates**:
   - **Collision**: Different source IDs normalize to same constant name → Error
   - **Duplicate**: Same source ID across models → Allowed (expected merging behavior)
3. **Provides actionable error messages** grouping collisions by process and showing all conflicting IDs
4. **Halts generation** to force users to fix BPMN modeling issues at the source

### Implementation

- Created `CollisionDetectionService` as a dedicated domain service following hexagonal architecture to detect collisions and throw errors

### Identifier-folding basis (flow nodes)
Flow nodes additionally generate **PascalCase** object names (`Variables`, `CallActivities`) via `StringUtils.toCamelCase()`, which folds away empty segments from leading/trailing/collapsed separators. Ids such as `foo` and `-foo` keep distinct `UPPER_SNAKE` constants (`FOO` / `_FOO`) but fold to the same object name `Foo`, which would emit two `object Foo` and fail to compile. Flow-node collision detection therefore checks **both** bases:
- the `UPPER_SNAKE` constant basis (`Elements`), and
- the PascalCase object-name basis (`getRawName().toCamelCase()`).

Neither basis subsumes the other (`fooBar` / `fooBAR` collide only in `UPPER_SNAKE`; `foo` / `-foo` only in folding), so both are needed. Collisions surfacing on both bases are de-duplicated and reported once, preferring the `UPPER_SNAKE` name. Call-activity ids are a subset of flow-node ids, so a single flow-node check covers `CallActivities` objects too.

## Consequences

### Positive
- Users see all collision issues in one error message (single fix iteration)
- Clear distinction between true duplicates (valid) and collisions (invalid)
- Prevents silent data loss from undetected ID normalization conflicts
- Structured error messages with process context and conflicting ID lists
- Non-breaking for valid BPMN models (only detects actual problems)

### Negative
- Breaking change for BPMN models with existing (previously silent) collisions
- Users with collision issues must fix BPMN models before generation succeeds
- Additional validation overhead during model merging (minimal performance impact)

## Example Error Message
```
Variable name collisions detected in 2 processes:

Process: NewsletterSubscription
  [FlowNode] END_EVENT_DATA_PROCESSED
    Conflicting IDs: endEvent-dataProcessed, endEvent_dataProcessed
  [Message] MESSAGE_FORM_SUBMITTED
    Conflicting IDs: message-formSubmitted, message_formSubmitted

Process: UserRegistration
  [Signal] SIGNAL_REGISTRATION_COMPLETE
    Conflicting IDs: signal.registrationComplete, signalRegistrationComplete

Please update your BPMN files to use consistent naming.
```
