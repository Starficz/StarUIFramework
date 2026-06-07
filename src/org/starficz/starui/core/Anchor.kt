package org.starficz.starui.core

import com.fs.starfarer.api.ui.UIComponentAPI

/**
 * A sealed interface providing a fluent, type-safe, and sentence-like API for creating AnchorData.
 * This approach uses public interfaces to define the API contract and private classes for implementation,
 * ensuring proper encapsulation and IDE discoverability.
 */
sealed interface Anchor {

    sealed class AnchorReference {
        data object Previous : AnchorReference()
        data object Parent : AnchorReference()
        data class Sibling(val component: UIComponentAPI) : AnchorReference()
    }

    data class AnchorData(
        val reference: AnchorReference,
        val refAnchorX: Float,
        val refAnchorY: Float,
        val targetAnchorX: Float,
        val targetAnchorY: Float,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    )

    sealed interface VerticalAligner {
        fun matchingLeftEdge(ySpacing: Float = 0f): AnchorData
        fun matchingCenter(ySpacing: Float = 0f): AnchorData
        fun matchingRightEdge(ySpacing: Float = 0f): AnchorData
    }

    sealed interface HorizontalAligner {
        fun matchingTopEdge(xSpacing: Float = 0f): AnchorData
        fun matchingCenter(xSpacing: Float = 0f): AnchorData
        fun matchingBottomEdge(xSpacing: Float = 0f): AnchorData
    }

    sealed interface InPositioner // Common marker interface

    /** A positioner for "In" alignments that take both X and Y margins. */
    sealed interface InBiMarginPositioner : InPositioner {
        fun ofParent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        fun ofPreviousComponent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        fun ofSibling(c: UIComponentAPI, xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
    }

    /** A positioner for "In" alignments that only take a Y margin. */
    sealed interface InYMarginPositioner : InPositioner {
        fun ofParent(ySpacing: Float = 0f): AnchorData
        fun ofPreviousComponent(ySpacing: Float = 0f): AnchorData
        fun ofSibling(c: UIComponentAPI, ySpacing: Float = 0f): AnchorData
    }

    /** A positioner for "In" alignments that only take an X margin. */
    sealed interface InXMarginPositioner : InPositioner {
        fun ofParent(xSpacing: Float = 0f): AnchorData
        fun ofPreviousComponent(xSpacing: Float = 0f): AnchorData
        fun ofSibling(c: UIComponentAPI, xSpacing: Float = 0f): AnchorData
    }

    sealed interface MyPositioner {
        fun ofParent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        fun ofPreviousComponent(xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
        fun ofSibling(c: UIComponentAPI, xSpacing: Float = 0f, ySpacing: Float = 0f): AnchorData
    }

    sealed interface MyTargetSelector {
        val toTopLeft: MyPositioner
        val toTopCenter: MyPositioner
        val toTopRight: MyPositioner
        val toCenterLeft: MyPositioner
        val toCenter: MyPositioner
        val toCenterRight: MyPositioner
        val toBottomLeft: MyPositioner
        val toBottomCenter: MyPositioner
        val toBottomRight: MyPositioner
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
        override fun ofSibling(c: UIComponentAPI, xSpacing: Float, ySpacing: Float) = create(AnchorReference.Sibling(c), xSpacing, ySpacing)
    }

    private class InYMarginPositionerImpl(
        private val refX: Float, private val refY: Float, private val targetX: Float, private val targetY: Float, private val ySign: Float
    ) : InYMarginPositioner {
        private fun create(ref: AnchorReference, y: Float) = AnchorData(ref, refX, refY, targetX, targetY, 0f, y * ySign)
        override fun ofParent(ySpacing: Float) = create(AnchorReference.Parent, ySpacing)
        override fun ofPreviousComponent(ySpacing: Float) = create(AnchorReference.Previous, ySpacing)
        override fun ofSibling(c: UIComponentAPI, ySpacing: Float) = create(AnchorReference.Sibling(c), ySpacing)
    }

    private class InXMarginPositionerImpl(
        private val refX: Float, private val refY: Float, private val targetX: Float, private val targetY: Float, private val xSign: Float
    ) : InXMarginPositioner {
        private fun create(ref: AnchorReference, x: Float) = AnchorData(ref, refX, refY, targetX, targetY, x * xSign, 0f)
        override fun ofParent(xSpacing: Float) = create(AnchorReference.Parent, xSpacing)
        override fun ofPreviousComponent(xSpacing: Float) = create(AnchorReference.Previous, xSpacing)
        override fun ofSibling(c: UIComponentAPI, xSpacing: Float) = create(AnchorReference.Sibling(c), xSpacing)
    }

    private class MyPositionerImpl(private val myX: Float, private val myY: Float, private val refX: Float, private val refY: Float) : MyPositioner {
        private fun create(ref: AnchorReference, xSpacing: Float, ySpacing: Float) = AnchorData(ref, refX, refY, -myX, -myY, xSpacing, ySpacing)
        override fun ofParent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Parent, xSpacing, ySpacing)
        override fun ofPreviousComponent(xSpacing: Float, ySpacing: Float) = create(AnchorReference.Previous, xSpacing, ySpacing)
        override fun ofSibling(c: UIComponentAPI, xSpacing: Float, ySpacing: Float) = create(AnchorReference.Sibling(c), xSpacing, ySpacing)
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

    companion object {
        interface VerticalEntryPoint {
            val parent: VerticalAligner
            val previousComponent: VerticalAligner
            fun sibling(c: UIComponentAPI): VerticalAligner
        }

        interface HorizontalEntryPoint {
            val parent: HorizontalAligner
            val previousComponent: HorizontalAligner
            fun sibling(c: UIComponentAPI): HorizontalAligner
        }

        interface InEntryPoint {
            val topLeft: InBiMarginPositioner
            val topCenter: InYMarginPositioner
            val topRight: InBiMarginPositioner
            val centerLeft: InXMarginPositioner
            val center: InBiMarginPositioner
            val centerRight: InXMarginPositioner
            val bottomLeft: InBiMarginPositioner
            val bottomCenter: InYMarginPositioner
            val bottomRight: InBiMarginPositioner
        }

        interface MyEntryPoint {
            fun xy(x: Float, y: Float): MyTargetSelector
            val topLeft: MyTargetSelector
            val topCenter: MyTargetSelector
            val topRight: MyTargetSelector
            val centerLeft: MyTargetSelector
            val center: MyTargetSelector
            val centerRight: MyTargetSelector
            val bottomLeft: MyTargetSelector
            val bottomCenter: MyTargetSelector
            val bottomRight: MyTargetSelector
        }

        val above: VerticalEntryPoint = object : VerticalEntryPoint {
            override val parent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Parent, 1.0f, 0.0f, 1f)
            override val previousComponent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Previous, 1.0f, 0.0f, 1f)
            override fun sibling(c: UIComponentAPI): VerticalAligner =
                VerticalAlignerImpl(AnchorReference.Sibling(c), 1.0f, 0.0f, 1f)
        }

        val below: VerticalEntryPoint = object : VerticalEntryPoint {
            override val parent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Parent, 0.0f, -1.0f, -1f)
            override val previousComponent: VerticalAligner
                get() = VerticalAlignerImpl(AnchorReference.Previous, 0.0f, -1.0f, -1f)
            override fun sibling(c: UIComponentAPI): VerticalAligner =
                VerticalAlignerImpl(AnchorReference.Sibling(c), 0.0f, -1.0f, -1f)
        }

        val leftOf: HorizontalEntryPoint = object : HorizontalEntryPoint {
            override val parent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Parent, 0.0f, -1.0f, -1f)
            override val previousComponent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Previous, 0.0f, -1.0f, -1f)
            override fun sibling(c: UIComponentAPI): HorizontalAligner =
                HorizontalAlignerImpl(AnchorReference.Sibling(c), 0.0f, -1.0f, -1f)
        }

        val rightOf: HorizontalEntryPoint = object : HorizontalEntryPoint {
            override val parent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Parent, 1.0f, 0.0f, 1f)
            override val previousComponent: HorizontalAligner get() =
                HorizontalAlignerImpl(AnchorReference.Previous, 1.0f, 0.0f, 1f)
            override fun sibling(c: UIComponentAPI): HorizontalAligner =
                HorizontalAlignerImpl(AnchorReference.Sibling(c), 1.0f, 0.0f, 1f)
        }

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

        val None = AnchorData(AnchorReference.Parent, 0f,0f,0f,0f)
    }
}