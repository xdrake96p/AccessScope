package dev.accessscope.cli

import com.google.gson.Gson

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }
    try {
        ensureAdbAvailable()
        when (args[0]) {
            "devices" -> handleDevices(args.drop(1))
            "install" -> handleInstall(args.drop(1))
            "launch" -> handleLaunch(args.drop(1))
            "status" -> handleStatus(args.drop(1))
            "fetch-results" -> handleFetchResults(args.drop(1))
            "setup-check" -> handleSetupCheck(args.drop(1))
            else -> {
                System.err.println("Unknown command: ${args[0]}")
                printUsage()
                kotlin.system.exitProcess(1)
            }
        }
    } catch (error: Exception) {
        System.err.println("ERROR: ${error.message}")
        kotlin.system.exitProcess(1)
    }
}

private fun handleDevices(args: List<String>) {
    if (args.firstOrNull() != "list") {
        println(DeviceManager.listDevicesJson())
        return
    }
    println(DeviceManager.listDevicesJson())
}

private fun handleInstall(args: List<String>) {
    val options = parseOptions(args)
    val device = requireDevice(options)
    val force = options.flags.contains("force")
    val installer = ApkInstaller(device)
    val result = installer.installLatest(force)
    println(Gson().toJson(result))
}

private fun handleLaunch(args: List<String>) {
    val options = parseOptions(args)
    val device = requireDevice(options)
    if (!options.flags.contains("skip-install")) {
        runCatching { ApkInstaller(device).installLatest() }
            .onFailure { installError ->
                if (!ApkInstaller(device).isInstalled()) {
                    throw installError
                }
            }
    }
    AppLauncher.launch(device)
    println("""{"launched":true,"package":"${AppConstants.PACKAGE_NAME}"}""")
}

private fun handleStatus(args: List<String>) {
    val options = parseOptions(args)
    val device = requireDevice(options)
    println(ResultFetcher(device).fetchStatus())
}

private fun handleFetchResults(args: List<String>) {
    val options = parseOptions(args)
    val device = requireDevice(options)
    val packageName = options.values["package"]
    val wait = options.flags.contains("wait")
    val timeout = options.values["timeout"]?.removeSuffix("m")?.toLongOrNull() ?: 30L
    println(ResultFetcher(device).fetchResults(packageName, wait, timeout))
}

private fun handleSetupCheck(args: List<String>) {
    val options = parseOptions(args)
    val device = requireDevice(options)
    println(SetupCheck.run(device))
}

private data class ParsedOptions(
    val values: Map<String, String>,
    val flags: Set<String>,
)

private fun parseOptions(args: List<String>): ParsedOptions {
    val values = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var index = 0
    while (index < args.size) {
        val arg = args[index]
        when {
            arg == "--device" && index + 1 < args.size -> {
                values["device"] = args[index + 1]
                index += 2
            }
            arg == "--package" && index + 1 < args.size -> {
                values["package"] = args[index + 1]
                index += 2
            }
            arg == "--timeout" && index + 1 < args.size -> {
                values["timeout"] = args[index + 1]
                index += 2
            }
            arg == "--force" -> {
                flags.add("force")
                index++
            }
            arg == "--wait" -> {
                flags.add("wait")
                index++
            }
            arg == "--skip-install" -> {
                flags.add("skip-install")
                index++
            }
            else -> index++
        }
    }
    return ParsedOptions(values, flags)
}

private fun requireDevice(options: ParsedOptions): String {
    val fromFlag = options.values["device"]
    if (!fromFlag.isNullOrBlank()) return fromFlag
    val env = System.getenv("ACCESS_SCOPE_DEVICE")
    if (!env.isNullOrBlank()) return env
    val devices = DeviceManager.listDevices().filter { it.state == "device" }
    if (devices.size == 1) return devices.first().serial
    error("Multiple devices connected. Pass --device SERIAL or set ACCESS_SCOPE_DEVICE.")
}

private fun printUsage() {
    println(
        """
        access-scope-cli commands:
          devices list
          install --device SERIAL [--force]
          launch --device SERIAL [--skip-install]
          status --device SERIAL
          fetch-results --device SERIAL [--package PKG] [--wait] [--timeout 30m]
          setup-check --device SERIAL
        """.trimIndent(),
    )
}
