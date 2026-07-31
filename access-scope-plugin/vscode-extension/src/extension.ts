import * as vscode from 'vscode';
import { CliRunner, detectTargetPackage } from './cliRunner';
import { ResultsWebview } from './resultsWebview';
import { AccessScopeSidebarProvider } from './sidebarPanel';
import { DeviceInfo, ScanResultResponse, SetupCheckResult } from './types';

const EXTENSION_VERSION = '1.0.8';
const GITHUB_REPO = 'xdrake96p/AccessScope';
const RELEASES_PATH = 'access-scope-plugin/releases';

let outputChannel: vscode.OutputChannel;
let cli: CliRunner;
let resultsWebview: ResultsWebview;
let sidebarProvider: AccessScopeSidebarProvider;
let extensionContext: vscode.ExtensionContext;

export function activate(context: vscode.ExtensionContext): void {
  extensionContext = context;
  outputChannel = vscode.window.createOutputChannel('AccessScope');
  cli = new CliRunner(outputChannel);
  resultsWebview = new ResultsWebview();

  sidebarProvider = new AccessScopeSidebarProvider(
    context,
    handleSidebarAction,
    readPanelState,
  );

  context.subscriptions.push(
    outputChannel,
    vscode.window.registerWebviewViewProvider(
      AccessScopeSidebarProvider.viewType,
      sidebarProvider,
      { webviewOptions: { retainContextWhenHidden: true } },
    ),
    vscode.commands.registerCommand('accessScope.openPanel', openPanel),
    vscode.commands.registerCommand('accessScope.selectDevice', selectDevice),
    vscode.commands.registerCommand('accessScope.install', install),
    vscode.commands.registerCommand('accessScope.launch', launch),
    vscode.commands.registerCommand('accessScope.installAndLaunch', installAndLaunch),
    vscode.commands.registerCommand('accessScope.fetchResults', () => fetchResults(context)),
    vscode.commands.registerCommand('accessScope.checkSetup', checkSetup),
    vscode.commands.registerCommand('accessScope.clearOutput', clearOutput),
    vscode.commands.registerCommand('accessScope.checkExtensionUpdate', checkExtensionUpdate),
  );
}

export function deactivate(): void {}

async function handleSidebarAction(
  action:
    | 'selectDevice'
    | 'install'
    | 'launch'
    | 'fetchResults'
    | 'setupCheck'
    | 'clearOutput'
    | 'checkUpdate',
): Promise<void> {
  try {
    switch (action) {
      case 'selectDevice':
        await selectDevice();
        break;
      case 'install':
        await install();
        break;
      case 'launch':
        await launch();
        break;
      case 'fetchResults':
        await fetchResults(extensionContext);
        break;
      case 'setupCheck':
        await checkSetup();
        break;
      case 'clearOutput':
        clearOutput();
        break;
      case 'checkUpdate':
        await checkExtensionUpdate();
        break;
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    outputChannel.appendLine(`ERROR: ${message}`);
    outputChannel.show(true);
    vscode.window.showErrorMessage(message);
  } finally {
    refreshSidebar();
  }
}

function openPanel(): void {
  void vscode.commands.executeCommand('workbench.view.extension.accessScope');
}

function readPanelState(): { device: string; targetPackage: string } {
  const config = vscode.workspace.getConfiguration('accessScope');
  const serial = config.get<string>('defaultDevice', '');
  const configuredPackage = config.get<string>('targetPackage', '');
  return {
    device: serial,
    targetPackage: configuredPackage || detectTargetPackage() || '',
  };
}

function refreshSidebar(): void {
  sidebarProvider?.refresh();
}

async function selectDevice(): Promise<void> {
  const devices = await listDevices();
  const online = devices.filter((device) => device.state === 'device');
  if (online.length === 0) {
    vscode.window.showErrorMessage('No Android devices connected.');
    return;
  }
  const pick = await vscode.window.showQuickPick(
    online.map((device) => ({
      label: device.model,
      description: device.serial,
      detail: device.isEmulator ? 'Emulator' : 'Physical device',
      device,
    })),
    { placeHolder: 'Select Android device for AccessScope' },
  );
  if (!pick) {
    return;
  }
  await vscode.workspace
    .getConfiguration('accessScope')
    .update('defaultDevice', pick.device.serial, vscode.ConfigurationTarget.Workspace);
  outputChannel.appendLine(`Device selected: ${pick.device.model} (${pick.device.serial})`);
  vscode.window.showInformationMessage(`AccessScope device set to ${pick.device.model} (${pick.device.serial})`);
  refreshSidebar();
}

async function install(): Promise<void> {
  await ensureDeviceSelected();
  const raw = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'AccessScope: installing / updating...',
      cancellable: false,
    },
    async () => cli.run(['install']),
  );
  outputChannel.appendLine(raw);
  vscode.window.showInformationMessage('AccessScope install/update completed.');
}

async function launch(): Promise<void> {
  await ensureDeviceSelected();
  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'AccessScope: launching...',
      cancellable: false,
    },
    async () => {
      await cli.run(['install']);
      await cli.run(['launch', '--skip-install']);
    },
  );
  vscode.window.showInformationMessage('AccessScope launched on device. Start a scan in the app when ready.');
}

async function installAndLaunch(): Promise<void> {
  await launch();
}

async function fetchResults(context: vscode.ExtensionContext): Promise<void> {
  await ensureDeviceSelected();
  const config = vscode.workspace.getConfiguration('accessScope');
  const configuredPackage = config.get<string>('targetPackage', '');
  const targetPackage = configuredPackage || detectTargetPackage() || '';
  const args = ['fetch-results'];
  if (targetPackage) {
    args.push('--package', targetPackage);
    outputChannel.appendLine(`Target package: ${targetPackage}`);
  }
  const raw = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'AccessScope: fetching results...',
      cancellable: false,
    },
    async () => cli.run(args),
  );
  const parsed = JSON.parse(raw) as ScanResultResponse & { error?: string; message?: string };
  // The CLI returns {"error":"not_found"} with exit 0 when no session exists yet:
  // treating it as a result would crash on result.violations below.
  if (parsed.error || !Array.isArray(parsed.violations)) {
    vscode.window.showInformationMessage(
      'AccessScope: no scan results on the device yet. Run a scan from the app first.',
    );
    return;
  }
  const result = parsed as ScanResultResponse;
  await context.workspaceState.update('accessScope.lastResult', result);
  resultsWebview.show(result);
  vscode.window.showInformationMessage(
    `AccessScope: score ${result.score}, ${result.violations.length} violations`,
  );
}

async function checkSetup(): Promise<void> {
  await ensureDeviceSelected();
  const raw = await cli.run(['setup-check']);
  outputChannel.appendLine(raw);
  const result = JSON.parse(raw) as SetupCheckResult;
  if (result.versionWarning) {
    vscode.window.showWarningMessage(result.versionWarning);
  }
  if (result.ready) {
    vscode.window.showInformationMessage('AccessScope setup is complete on the selected device.');
    return;
  }
  const action = await vscode.window.showWarningMessage(
    result.hint ?? 'AccessScope setup is incomplete on the selected device.',
    'Open Output',
  );
  if (action === 'Open Output') {
    outputChannel.show(true);
  }
}

function clearOutput(): void {
  outputChannel.clear();
}

async function checkExtensionUpdate(): Promise<void> {
  try {
    const latest = await fetchLatestExtensionVersion();
    outputChannel.appendLine(`Extension version: ${EXTENSION_VERSION}`);
    outputChannel.appendLine(`Latest GitHub release folder: ${latest}`);
    if (compareVersions(latest, EXTENSION_VERSION) <= 0) {
      vscode.window.showInformationMessage(`AccessScope extension v${EXTENSION_VERSION} is up to date.`);
      return;
    }
    const downloadUrl =
      `https://github.com/${GITHUB_REPO}/raw/develop/${RELEASES_PATH}/${latest}/AccessScope-${latest}.vsix`;
    const action = await vscode.window.showInformationMessage(
      `AccessScope extension v${latest} is available (current: v${EXTENSION_VERSION}).`,
      'Download VSIX',
      'Open Releases',
    );
    if (action === 'Download VSIX') {
      await vscode.env.openExternal(vscode.Uri.parse(downloadUrl));
    } else if (action === 'Open Releases') {
      await vscode.env.openExternal(
        vscode.Uri.parse(`https://github.com/${GITHUB_REPO}/tree/develop/${RELEASES_PATH}`),
      );
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    vscode.window.showErrorMessage(`Extension update check failed: ${message}`);
  }
}

async function fetchLatestExtensionVersion(): Promise<string> {
  const response = await fetch(
    `https://api.github.com/repos/${GITHUB_REPO}/contents/${RELEASES_PATH}?ref=develop`,
    {
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'accessscope-vscode-extension',
      },
    },
  );
  if (!response.ok) {
    throw new Error(`GitHub API HTTP ${response.status}`);
  }
  const entries = (await response.json()) as Array<{ name: string; type: string }>;
  return entries
    .filter((entry) => entry.type === 'dir' && /^\d+\.\d+\.\d+(\.\d+)?$/.test(entry.name))
    .map((entry) => entry.name)
    .sort(compareVersions)
    .at(-1) ?? EXTENSION_VERSION;
}

function compareVersions(left: string, right: string): number {
  const leftParts = left.split('.').map((part) => Number.parseInt(part, 10) || 0);
  const rightParts = right.split('.').map((part) => Number.parseInt(part, 10) || 0);
  const max = Math.max(leftParts.length, rightParts.length);
  for (let index = 0; index < max; index += 1) {
    const diff = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (diff !== 0) {
      return diff;
    }
  }
  return 0;
}

async function listDevices(): Promise<DeviceInfo[]> {
  const raw = await cli.run(['devices', 'list']);
  return JSON.parse(raw) as DeviceInfo[];
}

async function ensureDeviceSelected(): Promise<void> {
  const device = vscode.workspace.getConfiguration('accessScope').get<string>('defaultDevice', '');
  if (device) {
    return;
  }
  await selectDevice();
  const updated = vscode.workspace.getConfiguration('accessScope').get<string>('defaultDevice', '');
  if (!updated) {
    throw new Error('No device selected.');
  }
}
