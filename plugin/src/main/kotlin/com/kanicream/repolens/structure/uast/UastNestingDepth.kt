package com.kanicream.repolens.structure.uast

import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Measures the deepest nesting of control-flow constructs in a method body.
 *
 * Counted constructs follow docs/design.md OD-02: if, switch/when, all loop forms, try,
 * and lambdas. An `else if` chain counts one level per `if`, since UAST models it as an
 * `if` nested in the else branch; the fixtures pin that behaviour down.
 */
internal object UastNestingDepth {

    fun of(body: UExpression?): Int {
        if (body == null) return 0
        val visitor = DepthVisitor()
        body.accept(visitor)
        return visitor.max
    }

    private class DepthVisitor : AbstractUastVisitor() {
        private var depth = 0
        var max = 0
            private set

        private fun enter(): Boolean {
            depth++
            if (depth > max) max = depth
            return false
        }

        private fun exit() {
            depth--
        }

        override fun visitIfExpression(node: UIfExpression) = enter()
        override fun afterVisitIfExpression(node: UIfExpression) = exit()

        override fun visitSwitchExpression(node: USwitchExpression) = enter()
        override fun afterVisitSwitchExpression(node: USwitchExpression) = exit()

        override fun visitForExpression(node: UForExpression) = enter()
        override fun afterVisitForExpression(node: UForExpression) = exit()

        override fun visitForEachExpression(node: UForEachExpression) = enter()
        override fun afterVisitForEachExpression(node: UForEachExpression) = exit()

        override fun visitWhileExpression(node: UWhileExpression) = enter()
        override fun afterVisitWhileExpression(node: UWhileExpression) = exit()

        override fun visitDoWhileExpression(node: UDoWhileExpression) = enter()
        override fun afterVisitDoWhileExpression(node: UDoWhileExpression) = exit()

        override fun visitTryExpression(node: UTryExpression) = enter()
        override fun afterVisitTryExpression(node: UTryExpression) = exit()

        override fun visitLambdaExpression(node: ULambdaExpression) = enter()
        override fun afterVisitLambdaExpression(node: ULambdaExpression) = exit()
    }
}
