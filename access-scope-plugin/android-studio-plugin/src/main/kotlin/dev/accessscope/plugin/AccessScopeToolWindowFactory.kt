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
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class AccessScopeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AccessScopePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class AccessScopePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val cli = ApplicationManager.getApplication().getService(CliExecutor::class.java)
    private val updateChecker = ApplicationManager.getApplication().getService(PluginUpdateChecker::class.java)
    private val deviceCombo = JComboBox<String>()
    private val outputArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val targetPackageField = JLabel()

    init {
        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        val refreshButton = JButton("Refresh Devices")
        val installButton = JButton("Install / Update")
        val launchButton = JButton("Launch")
        val fetchButton = JButton("Fetch Results")
        val setupButton = JButton("Setup Check")
        val clearLogButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = "Cancella log"
            isFocusable = false
        }
        val updatePluginButton = JButton(AllIcons.Actions.Download).apply {
            toolTipText = "Verifica aggiornamenti plugin"
            isFocusable = false
        }

        targetPackageField.text = "Target package: ${GradlePackageDetector.detectTargetPackage(project) ?: "n/a"}"

        refreshButton.addActionListener { refreshDevices() }
        installButton.addActionListener { runForSelectedDevice { serial -> cli.install(project, serial) } }
        launchButton.addActionListener { runForSelectedDevice { serial -> cli.launch(project, serial) } }
        fetchButton.addActionListener {
            runForSelectedDevice { serial ->
                val pkg = GradlePackageDetector.detectTargetPackage(project)
                cli.fetchResults(project, serial, pkg)
            }
        }
        setupButton.addActionListener { runForSelectedDevice { serial -> cli.setupCheck(project, serial) } }
        clearLogButton.addActionListener { clearOutput() }
        updatePluginButton.addActionListener { checkPluginUpdate() }

        top.add(refreshButton)
        top.add(deviceCombo)
        top.add(installButton)
        top.add(launchButton)
        top.add(fetchButton)
        top.add(setupButton)
        top.add(clearLogButton)
        top.add(updatePluginButton)

        add(top, BorderLayout.NORTH)
        add(JBScrollPane(outputArea), BorderLayout.CENTER)
        add(targetPackageField, BorderLayout.SOUTH)
        preferredSize = Dimension(480, 360)
        refreshDevices()
    }

    private fun refreshDevices() {
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
            }
        }
    }

    private fun runForSelectedDevice(action: (String) -> String) {
        val selection = deviceCombo.selectedItem as? String ?: run {
            Messages.showErrorDialog(project, "Select a device first.", "AccessScope")
            return
        }
        val serial = selection.substringAfterLast('(').removeSuffix(")")
        runOnBackground {
            try {
                val result = action(serial)
                SwingUtilities.invokeLater {
                    appendOutput(result)
                }
            } catch (error: Exception) {
                showError(error)
            }
        }
    }

    private fun appendOutput(text: String) {
        outputArea.text = "${outputArea.text}\n$text".trim()
    }

    private fun clearOutput() {
        outputArea.text = ""
    }

    private fun checkPluginUpdate() {
        runOnBackground {
            try {
                val result = updateChecker.checkAndInstall { message ->
                    SwingUtilities.invokeLater { appendOutput(message) }
                }
                SwingUtilities.invokeLater {
                    when (result) {
                        is PluginUpdateResult.UpToDate -> {
                            appendOutput("Plugin già aggiornato (v${result.version}).")
                            Messages.showInfoMessage(
                                project,
                                "AccessScope plugin v${result.version} è già la versione più recente.",
                                "AccessScope",
                            )
                        }
                        is PluginUpdateResult.Installed -> {
                            appendOutput("Plugin aggiornato a v${result.version}. Riavvio Android Studio...")
                            Messages.showInfoMessage(
                                project,
                                "Aggiornamento a v${result.version} installato. Android Studio si riavvierà.",
                                "AccessScope",
                            )
                            ApplicationManager.getApplication().restart()
                        }
                    }
                }
            } catch (error: Exception) {
                showError(error)
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
