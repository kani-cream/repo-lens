package com.kanicream.repolens

import com.intellij.DynamicBundle
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.Severity
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.RepoLensBundle"

/**
 * UI strings, resolved against the IDE's display language.
 *
 * Only UI chrome is localized (buttons, labels, settings, status and error messages).
 * Finding reasons, check names and everything that flows into the copy formats stay
 * English on purpose: they are part of the exported evidence, and a copy pasted into a
 * review thread or an AI chat should not change with the reviewer's IDE language.
 */
object RepoLensBundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}

/** Localized scope name for UI surfaces; [AnalysisScopeType.displayName] stays English for copies. */
fun AnalysisScopeType.uiName(): String = RepoLensBundle.message("scope.$name")

/** Localized severity name for UI surfaces; [Severity.displayName] stays English for copies. */
fun Severity.uiName(): String = RepoLensBundle.message("severity.$name")
