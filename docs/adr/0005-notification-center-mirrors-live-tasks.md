# Notification center mirrors live tasks rather than keeping a durable log

---
Status: accepted
---

The in-app notification center is a read view over `NotificationHistory`, which is
`ON DELETE CASCADE`-tied to its task. We deliberately kept that cascade rather than
decoupling notifications into a standalone, durable audit log: when a user deletes a
task, that task's notifications leave the center with it.

## Considered Options

- **Keep CASCADE (chosen)** — a notification's meaning is bound to its task ("Task
  *Nike Restock* found an item"). A user who deletes a task is done with it; keeping its
  notifications around would be noise, and there are no dangling task references to render.
- **Decouple (rejected)** — drop/null the task FK so notifications outlive their task,
  giving a permanent audit log. Rejected because it forces the UI to handle notifications
  whose task no longer exists ("which task was this?") and makes us own orphan cleanup,
  for a durability nobody asked for.

## Consequences

- The center reflects *currently live* tasks only; it is not an audit trail.
- Volume is bounded by two independent mechanisms — task deletion (CASCADE) and a
  time-based 30-day startup prune (`pruneOlderThan`) — plus a `LIMIT` on the center's query.
- Reversing this later means an FK migration **and** new UI for orphaned-task notifications.
