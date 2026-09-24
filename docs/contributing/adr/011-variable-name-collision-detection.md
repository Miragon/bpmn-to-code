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

### Naming scopes (since 6.0)
Since the Process API became node-centric (ADR 022), each check mirrors the scope the generated name lives in:
- **Flow nodes** are named model-wide, because `Flow` is flat: every element, whatever its subprocess depth, becomes one nested object named `getRawName().toCamelCase()`. Ids such as `foo` and `-foo` fold to the same object name `Foo`, which would emit two `object Foo` and fail to compile. The same id declared in two scopes (root and subprocess interior) survives merging as two nodes and is reported as well.
- **Registries** (`ServiceTasks`, `Messages`, `Signals`, `Errors`, `Escalations`) are named model-wide on the `UPPER_SNAKE` basis.
- **Variables**, **sequence flows** and **call-activity mappings** are named per node — they live inside the node's `Variables`, `Flows`, `Inputs` / `Outputs` holders — so the same variable name on two different nodes is not a collision, while `userId` and `user_id` on one node is.

The former `Elements` and `Timers` bases disappeared with their sections. Names that would shadow the API's own holders or runtime types (`Flow`, `Next`, `Instance`, `ElementId`, …) are not collisions between elements and are rejected by the separate mandatory `reserved-element-name` rule.

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
