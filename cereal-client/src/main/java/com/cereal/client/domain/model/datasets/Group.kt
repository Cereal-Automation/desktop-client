package com.cereal.client.domain.model.datasets

/**
 * A named collection of records that a script can iterate over to spawn multiple concurrent tasks
 * (e.g. a proxy group or a custom dataset group).
 *
 * ## Contract
 * [numberOfItems] MUST equal the number of elements produced by [items]. The count is kept as a
 * separate eagerly-known field precisely because [items] is a single-consumption [Sequence]: callers
 * routinely need the size for capacity/concurrency decisions without draining (and thereby destroying)
 * the sequence.
 *
 * This invariant is intentionally **not** enforced in a shared `init` block, because doing so would
 * have to consume [items] to count it, breaking laziness and any single-use backing sequence.
 * Concrete implementations that build a [Group] from an eagerly-known collection SHOULD validate the
 * invariant at their construction boundary instead.
 */
interface Group<T> {
    val id: String
    val name: String
    val numberOfItems: Int
    val items: Sequence<T>
}
