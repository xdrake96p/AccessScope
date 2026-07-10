import * as vscode from 'vscode';
import { CliRunner } from './cliRunner';
import { ResultsWebview } from './resultsWebview';
import { DeviceInfo, ScanResultResponse, SetupCheckResult } from './types';

let outputChannel: vscode.OutputChannel;
let cli: CliRunner;
let resultsWebview: ResultsWebview;

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel('AccessScope');
  cli = new CliRunner(outputChannel);
  resultsWebview = new ResultsWebview();

  context.subscriptions.push(
    outputChannel,
    vscode.commands.registerCommand('accessScope.selectDevice', selectDevice),
    vscode.commands.registerCommand('accessScope.installAndLaunch', installAndLaunch),
    vscode.commands.registerCommand('accessScope.fetchResults', () => fetchResults(context)),
    vscode.commands.registerCommand('accessScope.checkSetup', checkSetup),
  );
}

export function deactivate(): void {}

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
  vscode.window.showInformationMessage(`AccessScope device set to ${pick.device.model} (${pick.device.serial})`);
}

async function installAndLaunch(): Promise<void> {
  await ensureDeviceSelected();
  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'AccessScope: installing and launching...',
      cancellable: false,
    },
    async () => {
      await cli.run(['install']);
      await cli.run(['launch', '--skip-install']);
    },
  );
  vscode.window.showInformationMessage('AccessScope launched on device. Start a scan in the app when ready.');
}

async function fetchResults(context: vscode.ExtensionContext): Promise<void> {
  await ensureDeviceSelected();
  const config = vscode.workspace.getConfiguration('accessScope');
  const targetPackage = config.get<string>('targetPackage', '');
  const args = ['fetch-results'];
  if (targetPackage) {
    args.push('--package', targetPackage);
  }
  const raw = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'AccessScope: fetching results...',
      cancellable: false,
    },
    async () => cli.run(args),
  );
  const result = JSON.parse(raw) as ScanResultResponse;
  await context.workspaceState.update('accessScope.lastResult', result);
  resultsWebview.show(result);
  vscode.window.showInformationMessage(
    `AccessScope: score ${result.score}, ${result.violations.length} violations`,
  );
}

async function checkSetup(): Promise<void> {
  await ensureDeviceSelected();
  const raw = await cli.run(['setup-check']);
  const result = JSON.parse(raw) as SetupCheckResult;
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
