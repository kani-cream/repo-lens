package com.kanicream.repolens.platform

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Maps between VFS files and the paths stored in the domain model.
 *
 * Findings carry project-relative paths (with `/` separators) so that exported Markdown
 * stays machine-independent; files outside the project base directory fall back to
 * their absolute path.
 */
internal object ProjectPaths {

    fun relativePath(project: Project, file: VirtualFile): String {
        val basePath = project.basePath ?: return file.path
        return FileUtil.getRelativePath(basePath, file.path, '/')
            ?.takeUnless { it.startsWith("..") }
            ?: file.path
    }

    fun resolve(project: Project, path: String): VirtualFile? {
        if (FileUtil.isAbsolute(path)) {
            LocalFileSystem.getInstance().findFileByPath(path)?.let { return it }
        } else {
            project.basePath?.let { base ->
                LocalFileSystem.getInstance().findFileByPath("$base/$path")?.let { return it }
            }
        }
        // Fallback covers multi-root projects and non-local file systems (e.g. tests).
        return ProjectRootManager.getInstance(project).contentRoots.firstNotNullOfOrNull { root ->
            root.findFileByRelativePath(path) ?: root.fileSystem.findFileByPath(path)
        }
    }
}
