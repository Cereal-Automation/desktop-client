package com.cereal.client.presentation.view.group

/**
 * Represents a hierarchical item that can be either a parent (group) or a child (script instance)
 */
sealed class HierarchicalListItem<P, C> {
    abstract val selected: Boolean

    data class Parent<P, C>(
        val content: GroupListItemContent<P>,
        val children: List<Child<P, C>>,
        val expanded: Boolean,
        override val selected: Boolean,
    ) : HierarchicalListItem<P, C>()

    data class Child<P, C>(
        val content: GroupListItemContent<C>,
        val parentId: P,
        override val selected: Boolean,
    ) : HierarchicalListItem<P, C>()
}
