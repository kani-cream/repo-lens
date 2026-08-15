package com.kanicream.repolens

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind

/** Verifies JS/TS structure extraction against real JavaScript-plugin PSI. */
class JsTsStructureTest : BasePlatformTestCase() {

    private fun structureOf(path: String, text: String): CodeStructure? {
        val file = myFixture.addFileToProject(path, text).virtualFile
        return ReadAction.compute<CodeStructure?, RuntimeException> {
            CodeStructureProvider.structureOf(project, file)
        }
    }

    fun `test javascript functions and classes are extracted`() {
        val structure = structureOf(
            "src/app.js",
            """
            function topLevel(a, b) {
                return a + b;
            }

            class Widget {
                render(props) {
                    return props;
                }
            }
            """.trimIndent(),
        )

        assertNotNull(structure)
        assertEquals(listOf("Widget"), structure!!.ofKind(DeclarationKind.TYPE).map { it.displayName })
        val functions = structure.ofKind(DeclarationKind.FUNCTION).associateBy { it.displayName }
        assertEquals(setOf("topLevel()", "Widget.render()"), functions.keys)
        assertEquals(2, functions["topLevel()"]?.parameterCount)
        assertEquals(1, functions["Widget.render()"]?.parameterCount)
    }

    fun `test typescript class methods and interfaces are extracted`() {
        val structure = structureOf(
            "src/service.ts",
            """
            interface Repository {
                find(id: string): string;
            }

            export class Service {
                constructor(private repo: Repository) {}

                load(id: string, fallback: string): string {
                    return this.repo.find(id) ?? fallback;
                }
            }
            """.trimIndent(),
        )

        val types = structure!!.ofKind(DeclarationKind.TYPE).map { it.displayName }
        assertTrue(types.toString(), "Service" in types)
        val functions = structure.ofKind(DeclarationKind.FUNCTION).associateBy { it.displayName }
        assertEquals(2, functions["Service.load()"]?.parameterCount)
    }

    fun `test nested callbacks count toward nesting depth`() {
        val structure = structureOf(
            "src/nested.ts",
            """
            function deep(items: number[][]) {
                if (items) {                          // 1
                    items.forEach(group => {          // 2 (arrow callback)
                        for (const v of group) {      // 3
                            if (v > 0) {              // 4
                                try {                 // 5
                                    setTimeout(() => { // 6 (nested callback)
                                        console.log(v);
                                    }, 0);
                                } catch (e) {
                                }
                            }
                        }
                    });
                }
            }
            """.trimIndent(),
        )

        val deep = structure!!.ofKind(DeclarationKind.FUNCTION).single { it.displayName == "deep()" }
        assertEquals(6, deep.maxNestingDepth)
    }

    fun `test anonymous arrows are not declarations`() {
        val structure = structureOf(
            "src/arrows.ts",
            """
            const handler = (event: string) => {
                console.log(event);
            };

            function named() {
                return 1;
            }
            """.trimIndent(),
        )

        val names = structure!!.ofKind(DeclarationKind.FUNCTION).map { it.displayName }
        // The arrow bound to `handler` may or may not surface a name through the JS PSI;
        // what must hold is that `named()` is present and nothing is nameless.
        assertTrue(names.toString(), "named()" in names)
        assertTrue(names.toString(), names.none { it == "()" })
    }

    fun `test tsx component functions are extracted`() {
        val structure = structureOf(
            "src/View.tsx",
            """
            export function View(props: { title: string }) {
                if (!props.title) {
                    return null;
                }
                return <h1>{props.title}</h1>;
            }
            """.trimIndent(),
        )

        val view = structure!!.ofKind(DeclarationKind.FUNCTION).single { it.displayName == "View()" }
        assertEquals(1, view.parameterCount)
        assertEquals(1, view.maxNestingDepth)
    }

    fun `test plain text files are not claimed by the js provider`() {
        assertNull(structureOf("notes.txt", "function f() {}"))
    }
}
