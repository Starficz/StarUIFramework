package org.starficz.starui

import com.fs.starfarer.api.ui.UIComponentAPI

/**
 * Where a child of a [CustomPanel][org.starficz.starui.components.CustomPanel] sits: relative to its parent, to the
 * component added just before it, or to a sibling. An anchor is the argument of `Modifier.anchor`, and reads as a
 * sentence:
 *
 * ```kotlin
 * Anchor.inside.topLeft.ofParent(10f, 10f)          // 10 in from the parent's top left corner
 * Anchor.below.sibling(title).matchingLeftEdge(5f)  // under `title`, 5 below it, left edges lined up
 * Anchor.rightOf.previousComponent.matchingTopEdge(5f)
 * Anchor.my.bottomRight.toTopLeft.ofParent()        // my bottom right corner on the parent's top left
 * ```
 *
 * Each sentence ends in an [AnchorData], the anchor itself. It starts at one of five words:
 * - [inside]: a corner, an edge's middle or the centre of the reference, with the spacing measured inward;
 * - [above] and [below]: next to the reference vertically, with the spacing as the gap between them;
 * - [leftOf] and [rightOf]: next to the reference horizontally, with the spacing as the gap between them;
 * - [my]: any point of the component on any point of the reference, with a raw offset.
 *
 * The anchor is vanilla's own `relativeTo`, applied again each time the parent is laid out: the component follows
 * its parent, and a sibling it is anchored to when the parent lays out again. A sibling that moves by itself,
 * outside a layout, does not take the component along.
 */
sealed interface Anchor {

    /** What an anchor places the component against. */
    sealed class AnchorReference {
        /** The component added to the same parent just before this one. */
        data object Previous : AnchorReference()
        /** The component's parent. */
        data object Parent : AnchorReference()
        /**
         * Another child of the same parent.
         *
         * @property component the sibling
         */
        data class Sibling(val component: UIComponentAPI) : AnchorReference()
    }

    /**
     * An anchor: the point of [reference] and the point of the component that are placed on each other, and an offset
     * between them. Make one with the [Anchor] sentences rather than by hand.
     *
     * @property reference what the component is placed against
     * @property refAnchorX the point on the reference, as a fraction of its width from its left edge
     * @property refAnchorY the point on the reference, as a fraction of its height from its bottom edge
     * @property targetAnchorX the point on the component, as vanilla's `relativeTo` takes it: minus the fraction of the
     *   component's width from its left edge
     * @property targetAnchorY the point on the component, as vanilla's `relativeTo` takes it: minus the fraction of the
     *   component's height from its bottom edge
     * @property offsetX the horizontal offset, in pixels, positive to the right
     * @property offsetY the vertical offset, in pixels, positive upwards (Starsector's y points up)
     */
    data class AnchorData(
        val reference: AnchorReference,
        val refAnchorX: Float,
        val refAnchorY: Float,
        val targetAnchorX: Float,
        val targetAnchorY: Float,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    )

    /** The last word of an [above] or [below] anchor: how the component lines up with the reference horizontally. */
    sealed interface VerticalAligner {
        /**
         * Lines up the left edges.
         *
         * @param ySpacing the gap between the component and the reference
         */
        fun matchingLeftEdge(ySpacing: Float = 0f): AnchorData
        /**
         * Centres the component horizontally on the reference.
         *
         * @param ySpacing the gap between the component and the reference
         */
        fun matchingCenter(ySpacing: Float = 0f): AnchorData
        /**
         * Lines up the right edges.
         *
         * @param ySpacing the gap between the component and the reference
         */
        fun matchingRightEdge(ySpacing: Float = 0f): AnchorData
    }

    /** The last word of a [leftOf] or [rightOf] anchor: how the component lines up with the reference vertically. */
    sealed interface HorizontalAligner {
        /**
         * Lines up the top edges.
         *
         * @param xSpacing the gap between the component and the reference
         */
        fun matchingTopEdge(xSpacing: Float = 0f): AnchorData
        /**
         * Centres the component vertically on the reference.
         *
         * @param xSpacing the gap between the component and the reference
         */
        fun matchingCenter(xSpacing: Float = 0f): AnchorData
        /**
         * Lines up the bottom edges.
         *
         * @param xSpacing the gap between the component and the reference
         */
        fun matchingBottomEdge(xSpacing: Float = 0f): AnchorData
    }

    /** The last word of an [inside] anchor: what the component is placed inside. */
    sealed interface InPositioner // Common marker interface

    /**
     * The last word of an [inside] anchor at a corner or the centre, which takes a spacing on both axes, measured
     * inward from the reference's edges. At the centre, the spacing moves the component right and up.
     */
    sealed interface InBiMarginPositioner : InPositioner {
        /**
         * Inside the parent.
         *
         * @param xSpacing the horizontal spacing, in pixels
         * @param ySpacing the vertical spacing, in pixels
         */
        fun ofParent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        /**
         * Inside the component added just before this one.
         *
         * @param xSpacing the horizontal spacing, in pixels
         * @param ySpacing the vertical spacing, in pixels
         */
        fun ofPreviousComponent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        /**
         * Inside [sibling].
         *
         * @param sibling another child of the same parent
         * @param xSpacing the horizontal spacing, in pixels
         * @param ySpacing the vertical spacing, in pixels
         */
        fun ofSibling(sibling: UIComponentAPI, xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
    }

    /**
     * The last word of an [inside] anchor at the middle of the top or bottom edge, which takes only a vertical spacing,
     * measured inward from that edge.
     */
    sealed interface InYMarginPositioner : InPositioner {
        /**
         * Inside the parent.
         *
         * @param ySpacing the spacing from the edge, in pixels
         */
        fun ofParent(ySpacing: Float = 0f): AnchorData
        /**
         * Inside the component added just before this one.
         *
         * @param ySpacing the spacing from the edge, in pixels
         */
        fun ofPreviousComponent(ySpacing: Float = 0f): AnchorData
        /**
         * Inside [sibling].
         *
         * @param sibling another child of the same parent
         * @param ySpacing the spacing from the edge, in pixels
         */
        fun ofSibling(sibling: UIComponentAPI, ySpacing: Float = 0f): AnchorData
    }

    /**
     * The last word of an [inside] anchor at the middle of the left or right edge, which takes only a horizontal
     * spacing, measured inward from that edge.
     */
    sealed interface InXMarginPositioner : InPositioner {
        /**
         * Inside the parent.
         *
         * @param xSpacing the spacing from the edge, in pixels
         */
        fun ofParent(xSpacing: Float = 0f): AnchorData
        /**
         * Inside the component added just before this one.
         *
         * @param xSpacing the spacing from the edge, in pixels
         */
        fun ofPreviousComponent(xSpacing: Float = 0f): AnchorData
        /**
         * Inside [sibling].
         *
         * @param sibling another child of the same parent
         * @param xSpacing the spacing from the edge, in pixels
         */
        fun ofSibling(sibling: UIComponentAPI, xSpacing: Float = 0f): AnchorData
    }

    /** The last word of a [my] anchor: the reference, with a raw offset. */
    sealed interface MyPositioner {
        /**
         * Against the parent.
         *
         * @param xSpacing the horizontal offset, in pixels, positive to the right
         * @param ySpacing the vertical offset, in pixels, positive upwards
         */
        fun ofParent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        /**
         * Against the component added just before this one.
         *
         * @param xSpacing the horizontal offset, in pixels, positive to the right
         * @param ySpacing the vertical offset, in pixels, positive upwards
         */
        fun ofPreviousComponent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        /**
         * Against [sibling].
         *
         * @param sibling another child of the same parent
         * @param xSpacing the horizontal offset, in pixels, positive to the right
         * @param ySpacing the vertical offset, in pixels, positive upwards
         */
        fun ofSibling(sibling: UIComponentAPI, xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
    }

    /**
     * The middle word of a [my] anchor: the point of the reference that the component's point goes on, one of the
     * nine named points or any point with [toXY].
     */
    sealed interface MyTargetSelector {
        /** On the reference's top left corner. */
        val toTopLeft: MyPositioner
        /** On the reference's top edge's middle. */
        val toTopCenter: MyPositioner
        /** On the reference's top right corner. */
        val toTopRight: MyPositioner
        /** On the reference's left edge's middle. */
        val toCenterLeft: MyPositioner
        /** On the reference's centre. */
        val toCenter: MyPositioner
        /** On the reference's right edge's middle. */
        val toCenterRight: MyPositioner
        /** On the reference's bottom left corner. */
        val toBottomLeft: MyPositioner
        /** On the reference's bottom edge's middle. */
        val toBottomCenter: MyPositioner
        /** On the reference's bottom right corner. */
        val toBottomRight: MyPositioner
        /**
         * On any point of the reference.
         *
         * @param refX the point, as a fraction of the reference's width from its left edge
         * @param refY the point, as a fraction of the reference's height from its bottom edge
         */
        fun toXY(refX: Float, refY: Float): MyPositioner
    }

    private class VerticalAlignerImpl(
        private val ref: AnchorReference, private val refY: Float,
        private val targetY: Float, private val ySign: Float
    ) : VerticalAligner {
        override fun matchingLeftEdge(ySpacing: Float) = AnchorData(ref, 0.0f, refY, 0.0f, targetY, 0f, ySpacing * ySign)
        override fun matchingCenter(ySpacing: Float) = AnchorData(ref, 0.5f, refY, -0.5f, targetY, 0f, ySpacing * ySign)
        override fun matchingRightEdge(ySpacing: Float) = AnchorData(ref, 1.0f, refY, -1.0f, targetY, 0f, ySpacing * ySign)
    }

    private class HorizontalAlignerImpl(
        private val ref: AnchorReference, private val refX: Float,
        private val targetX: Float, private val xSign: Float
    ) : HorizontalAligner {
        override fun matchingTopEdge(xSpacing: Float) = AnchorData(ref, refX, 1.0f, targetX, -1.0f, xSpacing * xSign, 0f)
        override fun matchingCenter(xSpacing: Float) = AnchorData(ref, refX, 0.5f, targetX, -0.5f, xSpacing * xSign, 0f)
        override fun matchingBottomEdge(xSpacing: Float) = AnchorData(ref, refX, 0.0f, targetX, 0.0f, xSpacing * xSign, 0f)
    }

    private class InBiMarginPositionerImpl(
        private val refX: Float, private val refY: Float, private val targetX: Float, private val targetY: Float,
        private val xSign: Float, private val ySign: Float
    ) : InBiMarginPositioner {
        private fun create(ref: AnchorReference, x: Float, y: Float) = AnchorData(ref, refX, refY, targetX, targetY, x * xSign, y * ySign)
        override fun ofParent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Parent, xSpacing, ySpacing)
        override fun ofPreviousComponent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Previous, xSpacing, ySpacing)
        override fun ofSibling(sibling: UIComponentAPI, xSpacing: Float, ySpacing: Float) = create(AnchorReference.Sibling(sibling), xSpacing, ySpacing)
    }

    private class InYMarginPositionerImpl(
        private val refX: Float, private val refY: Float, private val targetX: Float, private val targetY: Float, private val ySign: Float
    ) : InYMarginPositioner {
        private fun create(ref: AnchorReference, y: Float) = AnchorData(ref, refX, refY, targetX, targetY, 0f, y * ySign)
        override fun ofParent(ySpacing: Float) = create(AnchorReference.Parent, ySpacing)
        override fun ofPreviousComponent(ySpacing: Float) = create(AnchorReference.Previous, ySpacing)
        override fun ofSibling(sibling: UIComponentAPI, ySpacing: Float) = create(AnchorReference.Sibling(sibling), ySpacing)
    }

    private class InXMarginPositionerImpl(
        private val refX: Float, private val refY: Float, private val targetX: Float, private val targetY: Float, private val xSign: Float
    ) : InXMarginPositioner {
        private fun create(ref: AnchorReference, x: Float) = AnchorData(ref, refX, refY, targetX, targetY, x * xSign, 0f)
        override fun ofParent(xSpacing: Float) = create(AnchorReference.Parent, xSpacing)
        override fun ofPreviousComponent(xSpacing: Float) = create(AnchorReference.Previous, xSpacing)
        override fun ofSibling(sibling: UIComponentAPI, xSpacing: Float) = create(AnchorReference.Sibling(sibling), xSpacing)
    }

    private class MyPositionerImpl(private val myX: Float, private val myY: Float, private val refX: Float, private val refY: Float) : MyPositioner {
        private fun create(ref: AnchorReference, xSpacing: Float, ySpacing: Float) = AnchorData(ref, refX, refY, -myX, -myY, xSpacing, ySpacing)
        override fun ofParent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Parent, xSpacing, ySpacing)
        override fun ofPreviousComponent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Previous, xSpacing, ySpacing)
        override fun ofSibling(sibling: UIComponentAPI, xSpacing: Float, ySpacing: Float) = create(AnchorReference.Sibling(sibling), xSpacing, ySpacing)
    }

    private class MyTargetSelectorImpl(private val myX: Float, private val myY: Float) : MyTargetSelector {
        override val toTopLeft: MyPositioner get() = MyPositionerImpl(myX, myY, 0.0f, 1.0f)
        override val toTopCenter: MyPositioner get() = MyPositionerImpl(myX, myY, 0.5f, 1.0f)
        override val toTopRight: MyPositioner get() = MyPositionerImpl(myX, myY, 1.0f, 1.0f)
        override val toCenterLeft: MyPositioner get() = MyPositionerImpl(myX, myY, 0.0f, 0.5f)
        override val toCenter: MyPositioner get() = MyPositionerImpl(myX, myY, 0.5f, 0.5f)
        override val toCenterRight: MyPositioner get() = MyPositionerImpl(myX, myY, 1.0f, 0.5f)
        override val toBottomLeft: MyPositioner get() = MyPositionerImpl(myX, myY, 0.0f, 0.0f)
        override val toBottomCenter: MyPositioner get() = MyPositionerImpl(myX, myY, 0.5f, 0.0f)
        override val toBottomRight: MyPositioner get() = MyPositionerImpl(myX, myY, 1.0f, 0.0f)
        override fun toXY(refX: Float, refY: Float): MyPositioner = MyPositionerImpl(myX, myY, refX, refY)
    }

    /** The first words of every anchor: [inside], [above], [below], [leftOf], [rightOf] and [my]. */
    companion object {
        /** The second word of an [above] or [below] anchor: the reference. */
        interface VerticalEntryPoint {
            /** The parent. */
            val parent: VerticalAligner
            /** The component added to the same parent just before this one. */
            val previousComponent: VerticalAligner
            /**
             * Another child of the same parent.
             *
             * @param sibling the sibling
             */
            fun sibling(sibling: UIComponentAPI): VerticalAligner
        }

        /** The second word of a [leftOf] or [rightOf] anchor: the reference. */
        interface HorizontalEntryPoint {
            /** The parent. */
            val parent: HorizontalAligner
            /** The component added to the same parent just before this one. */
            val previousComponent: HorizontalAligner
            /**
             * Another child of the same parent.
             *
             * @param sibling the sibling
             */
            fun sibling(sibling: UIComponentAPI): HorizontalAligner
        }

        /**
         * The second word of an [inside] anchor: where inside the reference the component goes. A corner or the centre
         * takes spacing on both axes; the middle of an edge takes spacing from that edge only.
         */
        interface InEntryPoint {
            /** In the top left corner. */
            val topLeft: InBiMarginPositioner
            /** At the middle of the top edge. */
            val topCenter: InYMarginPositioner
            /** In the top right corner. */
            val topRight: InBiMarginPositioner
            /** At the middle of the left edge. */
            val centerLeft: InXMarginPositioner
            /** In the centre. */
            val center: InBiMarginPositioner
            /** At the middle of the right edge. */
            val centerRight: InXMarginPositioner
            /** In the bottom left corner. */
            val bottomLeft: InBiMarginPositioner
            /** At the middle of the bottom edge. */
            val bottomCenter: InYMarginPositioner
            /** In the bottom right corner. */
            val bottomRight: InBiMarginPositioner
        }

        /** The second word of a [my] anchor: the point of the component that is placed. */
        interface MyEntryPoint {
            /**
             * Any point of the component.
             *
             * @param x the point, as a fraction of the component's width from its left edge
             * @param y the point, as a fraction of the component's height from its bottom edge
             */
            fun xy(x: Float, y: Float): MyTargetSelector
            /** The component's top left corner. */
            val topLeft: MyTargetSelector
            /** The component's top edge's middle. */
            val topCenter: MyTargetSelector
            /** The component's top right corner. */
            val topRight: MyTargetSelector
            /** The component's left edge's middle. */
            val centerLeft: MyTargetSelector
            /** The component's centre. */
            val center: MyTargetSelector
            /** The component's right edge's middle. */
            val centerRight: MyTargetSelector
            /** The component's bottom left corner. */
            val bottomLeft: MyTargetSelector
            /** The component's bottom edge's middle. */
            val bottomCenter: MyTargetSelector
            /** The component's bottom right corner. */
            val bottomRight: MyTargetSelector
        }

        /** Above the reference: `Anchor.above.parent.matchingLeftEdge(5f)`. */
        val above: VerticalEntryPoint = object : VerticalEntryPoint {
            override val parent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Parent, 1.0f, 0.0f, 1f)
            override val previousComponent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Previous, 1.0f, 0.0f, 1f)
            override fun sibling(sibling: UIComponentAPI): VerticalAligner =
                VerticalAlignerImpl(AnchorReference.Sibling(sibling), 1.0f, 0.0f, 1f)
        }

        /** Below the reference: `Anchor.below.sibling(title).matchingCenter(5f)`. */
        val below: VerticalEntryPoint = object : VerticalEntryPoint {
            override val parent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Parent, 0.0f, -1.0f, -1f)
            override val previousComponent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Previous, 0.0f, -1.0f, -1f)
            override fun sibling(sibling: UIComponentAPI): VerticalAligner =
                VerticalAlignerImpl(AnchorReference.Sibling(sibling), 0.0f, -1.0f, -1f)
        }

        /** To the left of the reference: `Anchor.leftOf.previousComponent.matchingTopEdge(5f)`. */
        val leftOf: HorizontalEntryPoint = object : HorizontalEntryPoint {
            override val parent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Parent, 0.0f, -1.0f, -1f)
            override val previousComponent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Previous, 0.0f, -1.0f, -1f)
            override fun sibling(sibling: UIComponentAPI): HorizontalAligner =
                HorizontalAlignerImpl(AnchorReference.Sibling(sibling), 0.0f, -1.0f, -1f)
        }

        /** To the right of the reference: `Anchor.rightOf.previousComponent.matchingCenter(5f)`. */
        val rightOf: HorizontalEntryPoint = object : HorizontalEntryPoint {
            override val parent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Parent, 1.0f, 0.0f, 1f)
            override val previousComponent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Previous, 1.0f, 0.0f, 1f)
            override fun sibling(sibling: UIComponentAPI): HorizontalAligner =
                HorizontalAlignerImpl(AnchorReference.Sibling(sibling), 1.0f, 0.0f, 1f)
        }

        /** Inside the reference: `Anchor.inside.topRight.ofParent(10f, 10f)`. */
        val inside: InEntryPoint = object : InEntryPoint {
            override val topLeft
                get() = InBiMarginPositionerImpl(0.0f, 1.0f, 0.0f, -1.0f, 1f, -1f)
            override val topCenter
                get() = InYMarginPositionerImpl(0.5f, 1.0f, -0.5f, -1.0f, -1f)
            override val topRight
                get() = InBiMarginPositionerImpl(1.0f, 1.0f, -1.0f, -1.0f, -1f, -1f)
            override val centerLeft
                get() = InXMarginPositionerImpl(0.0f, 0.5f, 0.0f, -0.5f, 1f)
            override val center
                get() = InBiMarginPositionerImpl(0.5f, 0.5f, -0.5f, -0.5f, 1f, 1f)
            override val centerRight
                get() = InXMarginPositionerImpl(1.0f, 0.5f, -1.0f, -0.5f, -1f)
            override val bottomLeft
                get() = InBiMarginPositionerImpl(0.0f, 0.0f, 0.0f, 0.0f, 1f, 1f)
            override val bottomCenter
                get() = InYMarginPositionerImpl(0.5f, 0.0f, -0.5f, 0.0f, 1f)
            override val bottomRight
                get() = InBiMarginPositionerImpl(1.0f, 0.0f, -1.0f, 0.0f, -1f, 1f)
        }

        /** A point of the component on a point of the reference: `Anchor.my.bottomRight.toTopLeft.ofParent()`. */
        val my: MyEntryPoint = object : MyEntryPoint {
            override fun xy(x: Float, y: Float): MyTargetSelector = MyTargetSelectorImpl(x, y)
            override val topLeft: MyTargetSelector
                get() = MyTargetSelectorImpl(0.0f, 1.0f)
            override val topCenter: MyTargetSelector
                get() = MyTargetSelectorImpl(0.5f, 1.0f)
            override val topRight: MyTargetSelector
                get() = MyTargetSelectorImpl(1.0f, 1.0f)
            override val centerLeft: MyTargetSelector
                get() = MyTargetSelectorImpl(0.0f, 0.5f)
            override val center: MyTargetSelector
                get() = MyTargetSelectorImpl(0.5f, 0.5f)
            override val centerRight: MyTargetSelector
                get() = MyTargetSelectorImpl(1.0f, 0.5f)
            override val bottomLeft: MyTargetSelector
                get() = MyTargetSelectorImpl(0.0f, 0.0f)
            override val bottomCenter: MyTargetSelector
                get() = MyTargetSelectorImpl(0.5f, 0.0f)
            override val bottomRight: MyTargetSelector
                get() = MyTargetSelectorImpl(1.0f, 0.0f)
        }

        /** The component's bottom left corner on its parent's bottom left corner, with no offset. */
        val None = AnchorData(AnchorReference.Parent, 0f,0f,0f,0f)
    }
}