package dev.accessscope.plugin

import com.google.gson.JsonObject
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

class AccessScopeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AccessScopePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Modello tabellare per le violazioni di `fetch-results`.
 *
 * Prima venivano mostrate come JSON grezzo in una JTextArea — illeggibile su sessioni con
 * decine di violazioni e senza alcuna struttura per severità/schermata.
 */
private class ViolationsTableModel : AbstractTableModel() {
    private val columns = listOf("Severity", "Type", "Screen", "View ID", "Details")
    private var rows: List<JsonObject> = emptyList()

    fun setViolations(violations: List<JsonObject>) {
        rows = violations
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val violation = rows[rowIndex]
        return when (columnIndex) {
            0 -> violation.get("severity")?.asString ?: "MODERATE"
            1 -> violation.get("type")?.asString.orEmpty()
            2 -> violation.get("screenTitle")?.asString.orEmpty()
            3 -> violation.get("viewId")?.asString.orEmpty()
            else -> violation.get("details")?.asString.orEmpty()
        }
    }
}

private class AccessScopePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val cli = ApplicationManager.getApplication().getService(CliExecutor::class.java)
    private val updateChecker = ApplicationManager.getApplication().getService(PluginUpdateChecker::class.java)
    private val deviceCombo = JComboBox<String>()
    private val violationsModel = ViolationsTableModel()
    private val violationsTable = JBTable(violationsModel)
    private val logArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val busyIcon = AsyncProcessIcon("AccessScope").apply {
        isVisible = false
        suspend()
    }
    private val statusLabel = JLabel(" ")
    private val targetPackageField = JLabel()
    private val actionButtons = mutableListOf<JButton>()

    init {
        val topRows = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val actionsRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        val utilityRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        val refreshButton = JButton("Refresh Devices")
        val installButton = JButton("Install / Update")
        val launchButton = JButton("Launch")
        val fetchButton = JButton("Fetch Results")
        val setupButton = JButton("Setup Check")
        val clearLogButton = JButton("Clear log", AllIcons.Actions.GC).apply {
            toolTipText = "Clear log"
        }
        val updatePluginButton = JButton("Plugin update", AllIcons.Actions.Download).apply {
            toolTipText = "Check for and install plugin updates"
        }

        targetPackageField.text = "Target package: ${GradlePackageDetector.detectTargetPackage(project) ?: "n/a"}"

        refreshButton.addActionListener { refreshDevices() }
        installButton.addActionListener {
            runForSelectedDevice("Install") { serial -> cli.install(project, serial) }
        }
        launchButton.addActionListener {
            runForSelectedDevice("Launch") { serial -> cli.launch(project, serial) }
        }
        fetchButton.addActionListener {
            runForSelectedDevice("Fetch Results") { serial ->
                val pkg = GradlePackageDetector.detectTargetPackage(project)
                cli.fetchResults(project, serial, pkg)
            }
        }
        setupButton.addActionListener {
            runForSelectedDevice("Setup Check") { serial -> cli.setupCheck(project, serial) }
        }
        clearLogButton.addActionListener { clearOutput() }
        updatePluginButton.addActionListener { checkPluginUpdate() }

        actionButtons += listOf(refreshButton, installButton, launchButton, fetchButton, setupButton, updatePluginButton)

        actionsRow.add(refreshButton)
        actionsRow.add(deviceCombo)
        actionsRow.add(installButton)
        actionsRow.add(launchButton)
        actionsRow.add(fetchButton)
        actionsRow.add(setupButton)

        utilityRow.add(clearLogButton)
        utilityRow.add(updatePluginButton)

        statusRow.add(busyIcon)
        statusRow.add(statusLabel)

        topRows.add(actionsRow)
        topRows.add(utilityRow)
        topRows.add(statusRow)

        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JBScrollPane(violationsTable),
            JBScrollPane(logArea),
        ).apply {
            resizeWeight = 0.65
            dividerSize = 4
        }

        add(topRows, BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
        add(targetPackageField, BorderLayout.SOUTH)
        preferredSize = Dimension(560, 420)
        refreshDevices()
    }

    /** Feedback di progresso su ogni azione: prima executeOnPooledThread nudo, zero segnale visivo. */
    private fun setBusy(actionName: String?, busy: Boolean) {
        SwingUtilities.invokeLater {
            busyIcon.isVisible = busy
            if (busy) busyIcon.resume() else busyIcon.suspend()
            actionButtons.forEach { it.isEnabled = !busy }
            statusLabel.text = if (busy) "$actionName…" else " "
        }
    }

    private fun refreshDevices() {
        setBusy("Refreshing devices", true)
        runOnBackground {
            try {
                val devices = cli.listDevices(project).filter { it.state == "device" }
                val labels = devices.map { "${it.model} (${it.serial})" }
                SwingUtilities.invokeLater {
                    deviceCombo.model = DefaultComboBoxModel(labels.toTypedArray())
                    appendOutput("Found ${devices.size} device(s)")
                }
            } catch (error: Exception) {
                showError(error)
            } finally {
                setBusy(null, false)
            }
        }
    }

    private fun runForSelectedDevice(actionName: String, action: (String) -> String) {
        val selection = deviceCombo.selectedItem as? String ?: run {
            Messages.showErrorDialog(project, "Select a device first.", "AccessScope")
            return
        }
        val serial = selection.substringAfterLast('(').removeSuffix(")")
        setBusy(actionName, true)
        runOnBackground {
            try {
                val result = action(serial)
                SwingUtilities.invokeLater { handleResult(actionName, result) }
            } catch (error: Exception) {
                showError(error)
            } finally {
                setBusy(null, false)
            }
        }
    }

    private fun handleResult(actionName: String, raw: String) {
        val parsed = runCatching { parseJsonObject(raw) }.getOrNull()
        val violations = parsed?.getAsJsonArray("violations")
        when {
            violations != null -> {
                violationsModel.setViolations(violations.map { it.asJsonObject })
                val summary = parsed.getAsJsonObject("summary")
                val summaryText = summary?.let {
                    " (critical ${it.get("critical")?.asInt ?: 0}, serious ${it.get("serious")?.asInt ?: 0}, " +
                        "moderate ${it.get("moderate")?.asInt ?: 0}, minor ${it.get("minor")?.asInt ?: 0})"
                }.orEmpty()
                appendOutput(
                    "$actionName: score ${parsed.get("score")?.asInt ?: 0}, " +
                        "${violations.size()} violation(s)$summaryText",
                )
                parsed.get("pdfPath")?.asString?.let { appendOutput("PDF report: $it") }
            }
            parsed?.get("error")?.asString != null -> {
                appendOutput("$actionName: ${parsed.get("message")?.asString ?: parsed.get("error")?.asString}")
            }
            else -> {
                parsed?.get("versionWarning")?.asString?.let { appendOutput("WARNING: $it") }
                val message = parsed?.get("message")?.asString ?: parsed?.get("hint")?.asString ?: raw
                appendOutput("$actionName: $message")
            }
        }
    }

    private fun appendOutput(text: String) {
        logArea.text = "${logArea.text}\n$text".trim()
    }

    private fun clearOutput() {
        logArea.text = ""
        violationsModel.setViolations(emptyList())
    }

    private fun checkPluginUpdate() {
        setBusy("Plugin update", true)
        runOnBackground {
            try {
                val result = updateChecker.checkAndInstall { message ->
                    SwingUtilities.invokeLater { appendOutput(message) }
                }
                SwingUtilities.invokeLater {
                    when (result) {
                        is PluginUpdateResult.UpToDate -> {
                            appendOutput("Plugin already up to date (v${result.version}).")
                            Messages.showInfoMessage(
                                project,
                                "AccessScope plugin v${result.version} is already the latest version.",
                                "AccessScope",
                            )
                        }
                        is PluginUpdateResult.Installed -> {
                            appendOutput("Plugin updated to v${result.version}. Restarting Android Studio...")
                            Messages.showInfoMessage(
                                project,
                                "Update to v${result.version} installed. Android Studio will restart.",
                                "AccessScope",
                            )
                            ApplicationManager.getApplication().restart()
                        }
                    }
                }
            } catch (error: Exception) {
                showError(error)
            } finally {
                setBusy(null, false)
            }
        }
    }

    private fun showError(error: Exception) {
        val message = error.message?.removePrefix("ERROR:")?.trim().orEmpty()
            .ifBlank { "Unknown error" }
        SwingUtilities.invokeLater {
            appendOutput("ERROR: $message")
            Messages.showErrorDialog(project, message, "AccessScope")
        }
    }
}

class InstallLaunchAction : com.intellij.openapi.actionSystem.AnAction() {
    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
        val project = e.project ?: return
        val cli = ApplicationManager.getApplication().getService(CliExecutor::class.java)
        runOnBackground {
            try {
                val devices = cli.listDevices(project).filter { it.state == "device" }
                if (devices.isEmpty()) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(project, "No connected Android devices.", "AccessScope")
                    }
                    return@runOnBackground
                }
                val serial = devices.first().serial
                cli.install(project, serial)
                cli.launch(project, serial)
                SwingUtilities.invokeLater {
                    Messages.showInfoMessage(project, "AccessScope launched on $serial", "AccessScope")
                }
            } catch (error: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, error.message ?: "Unknown error", "AccessScope")
                }
            }
        }
    }
}
