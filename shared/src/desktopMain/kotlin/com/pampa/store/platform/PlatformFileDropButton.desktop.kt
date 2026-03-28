package com.pampa.store.platform

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.LineBorder

@Composable
actual fun PlatformFileDropButton(
    emptyLabel: String,
    readyLabel: String,
    selectedPath: String?,
    extensions: List<String>,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onFileDropped: (String) -> Unit,
    modifier: Modifier
) {
    val currentOnSelectClick = rememberUpdatedState(onSelectClick)
    val currentOnFileDropped = rememberUpdatedState(onFileDropped)
    val currentExtensions = rememberUpdatedState(
        extensions.map { it.lowercase().removePrefix(".") }
    )

    SwingPanel(
        factory = {
            DropZonePanel(
                onSelectClick = currentOnSelectClick,
                onFileDropped = currentOnFileDropped,
                extensions = currentExtensions
            ).apply {
                updateContent(
                    emptyLabel = emptyLabel,
                    readyLabel = readyLabel,
                    selectedPath = selectedPath,
                    enabled = enabled
                )
            }
        },
        update = { panel ->
            panel.updateContent(
                emptyLabel = emptyLabel,
                readyLabel = readyLabel,
                selectedPath = selectedPath,
                enabled = enabled
            )
        },
        modifier = modifier.heightIn(min = 108.dp)
    )
}

private class DropZonePanel(
    private val onSelectClick: State<() -> Unit>,
    private val onFileDropped: State<(String) -> Unit>,
    private val extensions: State<List<String>>
) : JPanel() {

    private val titleLabel = JLabel("", SwingConstants.CENTER)
    private val subtitleLabel = JLabel("", SwingConstants.CENTER)
    private val hintLabel = JLabel("", SwingConstants.CENTER)
    private var dropState: DropUiState = DropUiState.IDLE
    private var hasSelection = false
    private var enabledState = true
    private var lastSuccessfulDropAtMillis = 0L

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isOpaque = true

        titleLabel.alignmentX = Component.CENTER_ALIGNMENT
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 13f)

        subtitleLabel.alignmentX = Component.CENTER_ALIGNMENT
        subtitleLabel.font = subtitleLabel.font.deriveFont(Font.PLAIN, 12f)

        hintLabel.alignmentX = Component.CENTER_ALIGNMENT
        hintLabel.font = hintLabel.font.deriveFont(Font.PLAIN, 11f)

        add(Box.createVerticalGlue())
        add(titleLabel)
        add(Box.createVerticalStrut(6))
        add(subtitleLabel)
        add(Box.createVerticalStrut(6))
        add(hintLabel)
        add(Box.createVerticalGlue())

        val clickListener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                val ignoreClickBecauseOfRecentDrop =
                    System.currentTimeMillis() - lastSuccessfulDropAtMillis < RECENT_DROP_CLICK_GUARD_MS
                if (enabledState && !ignoreClickBecauseOfRecentDrop) {
                    onSelectClick.value.invoke()
                }
            }
        }
        attachMouseListener(this, clickListener)

        registerDropTarget(this)
        registerDropTarget(titleLabel)
        registerDropTarget(subtitleLabel)
        registerDropTarget(hintLabel)
        applyVisualState()
    }

    fun updateContent(
        emptyLabel: String,
        readyLabel: String,
        selectedPath: String?,
        enabled: Boolean
    ) {
        enabledState = enabled
        isEnabled = enabled
        titleLabel.isEnabled = enabled
        subtitleLabel.isEnabled = enabled
        hintLabel.isEnabled = enabled
        hasSelection = selectedPath != null
        titleLabel.text = if (selectedPath == null) emptyLabel else readyLabel
        subtitleLabel.text = selectedPath?.let(::fileNameOnly) ?: "Trascina il file oppure fai clic"
        hintLabel.text = buildHintText(enabled, selectedPath != null)
        if (dropState == DropUiState.IDLE || dropState == DropUiState.ACCEPTED || !enabled) {
            dropState = if (selectedPath != null) DropUiState.ACCEPTED else DropUiState.IDLE
        }
        applyVisualState()
    }

    private fun buildHintText(enabled: Boolean, hasSelectedPath: Boolean): String {
        if (!enabled) {
            return "Publishing non disponibile su questa piattaforma"
        }
        if (runningAsAdministrator) {
            return "Se il drag non entra, usa il clic: Explorer blocca il drop verso app elevate"
        }
        return if (hasSelectedPath) {
            "File acquisito. Puoi sostituirlo con un nuovo drag o con il clic."
        } else {
            "Accetta drag da Esplora file e fallback manuale con clic"
        }
    }

    private fun registerDropTarget(component: JComponent) {
        component.dropTarget = DropTarget(
            component,
            DnDConstants.ACTION_COPY_OR_MOVE,
            object : DropTargetAdapter() {
                override fun dragEnter(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragOver(event: DropTargetDragEvent) {
                    handleDrag(event)
                }

                override fun dragExit(event: java.awt.dnd.DropTargetEvent) {
                    dropState = if (hasSelection) DropUiState.ACCEPTED else DropUiState.IDLE
                    applyVisualState()
                }

                override fun drop(event: DropTargetDropEvent) {
                    val droppedFile = findMatchingDroppedFile(event.transferable, extensions.value)
                    if (!enabledState || droppedFile == null) {
                        dropState = DropUiState.REJECTED
                        applyVisualState()
                        event.rejectDrop()
                        return
                    }

                    event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
                    lastSuccessfulDropAtMillis = System.currentTimeMillis()
                    onFileDropped.value.invoke(droppedFile.absolutePath)
                    hasSelection = true
                    dropState = DropUiState.ACCEPTED
                    applyVisualState()
                    event.dropComplete(true)
                }
            },
            true
        )
    }

    private fun handleDrag(event: DropTargetDragEvent) {
        if (enabledState) {
            dropState = DropUiState.HOVER
            applyVisualState()
            event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE)
        } else {
            dropState = DropUiState.REJECTED
            applyVisualState()
            event.rejectDrag()
        }
    }

    private fun applyVisualState() {
        val palette = when {
            !enabledState -> DropPalette(
                background = Color(233, 236, 241),
                border = Color(180, 185, 194),
                title = Color(114, 118, 126),
                text = Color(114, 118, 126)
            )

            dropState == DropUiState.HOVER -> DropPalette(
                background = Color(232, 240, 255),
                border = Color(102, 148, 255),
                title = Color(41, 78, 170),
                text = Color(52, 62, 89)
            )

            dropState == DropUiState.ACCEPTED -> DropPalette(
                background = Color(230, 246, 234),
                border = Color(63, 154, 95),
                title = Color(25, 113, 58),
                text = Color(41, 79, 55)
            )

            dropState == DropUiState.REJECTED -> DropPalette(
                background = Color(252, 236, 236),
                border = Color(204, 83, 83),
                title = Color(167, 39, 39),
                text = Color(110, 55, 55)
            )

            else -> DropPalette(
                background = Color(244, 246, 250),
                border = Color(151, 168, 196),
                title = Color(33, 37, 45),
                text = Color(83, 91, 108)
            )
        }

        background = palette.background
        border = BorderFactory.createCompoundBorder(
            LineBorder(palette.border, 1, true),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)
        )
        titleLabel.foreground = palette.title
        subtitleLabel.foreground = palette.text
        hintLabel.foreground = palette.text
        repaint()
    }
}

private fun attachMouseListener(component: Component, listener: java.awt.event.MouseListener) {
    component.addMouseListener(listener)
    if (component is java.awt.Container) {
        component.components.forEach { child -> attachMouseListener(child, listener) }
    }
}

private fun findMatchingDroppedFile(
    transferable: Transferable,
    extensions: List<String>
): File? {
    val droppedFiles = extractDroppedFiles(transferable)
    if (droppedFiles.size != 1) {
        return null
    }

    val file = droppedFiles.single()
    if (!file.exists() || file.isDirectory) {
        return null
    }
    if (extensions.isNotEmpty() && file.extension.lowercase() !in extensions) {
        return null
    }
    return file
}

private fun extractDroppedFiles(transferable: Transferable): List<File> {
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val javaFileList = runCatching {
            @Suppress("UNCHECKED_CAST")
            transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        }.getOrNull().orEmpty()
            .filterIsInstance<File>()
        if (javaFileList.isNotEmpty()) {
            return javaFileList
        }
    }

    val pathLikeFlavors = transferable.transferDataFlavors.filter(::isPathLikeFlavor)
    pathLikeFlavors.forEach { flavor ->
        val files = runCatching { extractFilesFromFlavor(transferable, flavor) }
            .getOrDefault(emptyList())
            .filter { it.exists() && !it.isDirectory }
        if (files.isNotEmpty()) {
            return files
        }
    }

    return emptyList()
}

private fun extractFilesFromFlavor(
    transferable: Transferable,
    flavor: DataFlavor
): List<File> {
    val transferData = transferable.getTransferData(flavor) ?: return emptyList()
    return when (transferData) {
        is File -> listOf(transferData)
        is URI -> listOfNotNull(runCatching { File(transferData) }.getOrNull())
        is URL -> listOfNotNull(runCatching { File(transferData.toURI()) }.getOrNull())
        is List<*> -> transferData.mapNotNull(::toDroppedFile)
        is String -> parseDroppedText(transferData)
        is Reader -> transferData.use { parseDroppedText(it.readText()) }
        is InputStream -> InputStreamReader(transferData, charsetFor(flavor)).use { parseDroppedText(it.readText()) }
        else -> parseDroppedText(transferData.toString())
    }
}

private fun toDroppedFile(value: Any?): File? {
    return when (value) {
        is File -> value
        is URI -> runCatching { File(value) }.getOrNull()
        is URL -> runCatching { File(value.toURI()) }.getOrNull()
        is String -> parseDroppedText(value).singleOrNull()
        else -> null
    }
}

private fun parseDroppedText(textPayload: String): List<File> {
    if (textPayload.isBlank()) return emptyList()

    return textPayload.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { entry ->
            when {
                entry.startsWith("file:/", ignoreCase = true) -> runCatching { File(URI(entry)) }.getOrNull()
                else -> File(entry.trim('"')).takeIf { it.exists() }
            }
        }
        .toList()
}

private fun isPathLikeFlavor(flavor: DataFlavor): Boolean {
    return flavor == DataFlavor.stringFlavor ||
        flavor.mimeType.startsWith("text/uri-list", ignoreCase = true) ||
        flavor.mimeType.startsWith("application/x-java-url", ignoreCase = true) ||
        flavor.representationClass == String::class.java ||
        flavor.representationClass == URI::class.java ||
        flavor.representationClass == URL::class.java ||
        flavor.isRepresentationClassReader ||
        flavor.isRepresentationClassInputStream
}

private fun charsetFor(flavor: DataFlavor): Charset {
    val charsetName = flavor.getParameter("charset").orEmpty()
    return runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
}

private fun fileNameOnly(path: String): String {
    return path.substringAfterLast('/').substringAfterLast('\\')
}

private data class DropPalette(
    val background: Color,
    val border: Color,
    val title: Color,
    val text: Color
)

private enum class DropUiState {
    IDLE,
    HOVER,
    ACCEPTED,
    REJECTED
}

private val runningAsAdministrator: Boolean by lazy {
    detectRunningAsAdministrator()
}

private fun detectRunningAsAdministrator(): Boolean {
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        return false
    }
    return runCatching {
        val process = ProcessBuilder("cmd.exe", "/c", "net", "session")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }.getOrDefault(false)
}

private const val RECENT_DROP_CLICK_GUARD_MS = 1200L
