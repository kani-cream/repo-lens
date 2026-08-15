package com.kanicream.repolens

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind

/** Verifies structure extraction against real Java and Kotlin PSI through UAST. */
class UastStructureTest : BasePlatformTestCase() {

    private fun structureOf(path: String, text: String): CodeStructure? {
        val file = myFixture.addFileToProject(path, text).virtualFile
        return ReadAction.compute<CodeStructure?, RuntimeException> {
            CodeStructureProvider.structureOf(project, file)
        }
    }

    fun `test java class and methods are extracted with names and ranges`() {
        val structure = structureOf(
            "src/Sample.java",
            """
            public class Sample {
                public void first() {
                    int a = 1;
                }

                public int second(int x) {
                    return x;
                }
            }
            """.trimIndent(),
        )

        assertNotNull(structure)
        val types = structure!!.ofKind(DeclarationKind.TYPE)
        assertSize(1, types)
        assertEquals("Sample", types.single().displayName)
        assertEquals(1, types.single().startLine)

        val functions = structure.ofKind(DeclarationKind.FUNCTION)
        assertEquals(listOf("Sample.first()", "Sample.second()"), functions.map { it.displayName })
        assertEquals(2, functions.first().startLine)
    }

    fun `test method body line count excludes the signature`() {
        val structure = structureOf(
            "src/Body.java",
            """
            public class Body {
                public void run() {
                    int a = 1;
                    int b = 2;
                    int c = 3;
                }
            }
            """.trimIndent(),
        )

        val method = structure!!.ofKind(DeclarationKind.FUNCTION).single()
        // Body spans the brace lines 2..6 in the file; the signature is not counted twice.
        assertEquals(5, method.bodyLineCount)
        assertEquals(2, method.startLine)
        assertEquals(6, method.endLine)
    }

    fun `test nested classes are reported`() {
        val structure = structureOf(
            "src/Outer.java",
            """
            public class Outer {
                static class Inner {
                    void deep() {}
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("Outer", "Inner"),
            structure!!.ofKind(DeclarationKind.TYPE).map { it.displayName },
        )
        assertEquals(
            listOf("Inner.deep()"),
            structure.ofKind(DeclarationKind.FUNCTION).map { it.displayName },
        )
    }

    fun `test kotlin class and functions are extracted`() {
        val structure = structureOf(
            "src/Sample.kt",
            """
            class Sample {
                fun first() {
                    val a = 1
                }

                fun second(x: Int): Int {
                    return x
                }
            }
            """.trimIndent(),
        )

        assertNotNull("Kotlin should be convertible to UAST", structure)
        assertEquals(
            listOf("Sample"),
            structure!!.ofKind(DeclarationKind.TYPE).map { it.displayName },
        )
        // The implicit constructor Kotlin synthesizes for the class shares its source
        // element with the class and must not be reported as a method.
        assertEquals(
            listOf("Sample.first()", "Sample.second()"),
            structure.ofKind(DeclarationKind.FUNCTION).map { it.displayName },
        )
    }

    fun `test kotlin explicit constructor is reported`() {
        val structure = structureOf(
            "src/WithCtor.kt",
            """
            class WithCtor constructor(private val x: Int) {
                fun use() = x
            }
            """.trimIndent(),
        )

        val functions = structure!!.ofKind(DeclarationKind.FUNCTION).map { it.displayName }
        assertTrue(functions.toString(), functions.contains("WithCtor.use()"))
        assertTrue(functions.toString(), functions.any { it.contains("WithCtor.WithCtor()") })
    }

    fun `test kotlin top-level function is extracted`() {
        val structure = structureOf("src/Top.kt", "fun standalone() {\n    val a = 1\n}\n")

        val functions = structure!!.ofKind(DeclarationKind.FUNCTION)
        assertSize(1, functions)
        assertEquals(1, functions.single().startLine)
    }

    fun `test parameter count is extracted for java and kotlin`() {
        val java = structureOf(
            "src/Params.java",
            """
            public class Params {
                void wide(int a, int b, int c, int d) {}
            }
            """.trimIndent(),
        )
        assertEquals(
            4,
            java!!.ofKind(DeclarationKind.FUNCTION).single().parameterCount,
        )

        val kotlin = structureOf("src/Params.kt", "fun narrow(a: Int, b: Int) {}\n")
        assertEquals(2, kotlin!!.ofKind(DeclarationKind.FUNCTION).single().parameterCount)
    }

    fun `test nesting depth counts nested control flow`() {
        val structure = structureOf(
            "src/Nesting.java",
            """
            public class Nesting {
                void deep(int[] items) {
                    if (items != null) {
                        for (int item : items) {
                            if (item > 0) {
                                try {
                                    System.out.println(item);
                                } catch (RuntimeException e) {
                                }
                            }
                        }
                    }
                }

                void flat() {
                    int a = 1;
                }
            }
            """.trimIndent(),
        )

        val functions = structure!!.ofKind(DeclarationKind.FUNCTION)
        assertEquals(4, functions.single { it.displayName == "Nesting.deep()" }.maxNestingDepth)
        assertEquals(0, functions.single { it.displayName == "Nesting.flat()" }.maxNestingDepth)
    }

    fun `test kotlin when and lambda count toward nesting depth`() {
        val structure = structureOf(
            "src/KtNesting.kt",
            """
            class KtNesting {
                fun deep(values: List<Int>) {
                    when (values.size) {
                        0 -> Unit
                        else -> values.forEach { value ->
                            if (value > 0) {
                                println(value)
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
        )

        val deep = structure!!.ofKind(DeclarationKind.FUNCTION).single { it.displayName == "KtNesting.deep()" }
        // when(1) > lambda(2) > if(3)
        assertEquals(3, deep.maxNestingDepth)
    }

    fun `test package and imports are extracted with lines`() {
        val structure = structureOf(
            "src/app/a/Service.java",
            """
            package app.a;

            import java.util.List;
            import app.b.Helper;

            public class Service {}
            """.trimIndent(),
        )

        assertEquals("app.a", structure!!.packageName)
        assertEquals(
            listOf("java.util.List" to 3, "app.b.Helper" to 4),
            structure.imports.map { it.target to it.line },
        )
    }

    fun `test kotlin package and imports are extracted`() {
        val structure = structureOf(
            "src/Feature.kt",
            """
            package app.feature

            import app.core.Engine

            class Feature(private val engine: Engine)
            """.trimIndent(),
        )

        assertEquals("app.feature", structure!!.packageName)
        assertEquals(listOf("app.core.Engine"), structure.imports.map { it.target })
    }

    fun `test files without a uast language have no structure`() {
        assertNull(structureOf("docs/readme.txt", "just text\n"))
    }
}
