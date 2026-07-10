import * as vscode from 'vscode';

type PanelAction =
  | 'selectDevice'
  | 'install'
  | 'launch'
  | 'fetchResults'
  | 'setupCheck'
  | 'clearOutput'
  | 'checkUpdate';

export class AccessScopeSidebarProvider implements vscode.WebviewViewProvider {
  public static readonly viewType = 'accessScope.sidebar';

  private view?: vscode.WebviewView;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly onAction: (action: PanelAction) => Promise<void>,
    private readonly readState: () => { device: string; targetPackage: string },
  ) {}

  resolveWebviewView(
    webviewView: vscode.WebviewView,
    _context: vscode.WebviewViewResolveContext,
    _token: vscode.CancellationToken,
  ): void {
    this.view = webviewView;
    webviewView.webview.options = {
      enableScripts: true,
      localResourceRoots: [this.context.extensionUri],
    };

    webviewView.webview.onDidReceiveMessage(async (message: { action?: PanelAction }) => {
      if (!message.action) {
        return;
      }
      await this.onAction(message.action);
      this.refresh();
    });

    webviewView.onDidChangeVisibility(() => this.refresh());
    this.refresh();
  }

  refresh(): void {
    if (!this.view) {
      return;
    }
    const state = this.readState();
    this.view.webview.html = this.renderHtml(state.device, state.targetPackage);
  }

  private renderHtml(device: string, targetPackage: string): string {
    const deviceLabel = device || 'Nessun device selezionato';
    const packageLabel = targetPackage || 'n/a (auto-detect da Gradle)';
    return `<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    :root {
      color: var(--vscode-foreground);
      font-family: var(--vscode-font-family);
      font-size: var(--vscode-font-size);
    }
    body { margin: 0; padding: 12px; }
    h2 { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
    .meta {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      margin-bottom: 12px;
      line-height: 1.4;
      word-break: break-word;
    }
    .grid { display: grid; gap: 8px; }
    button {
      width: 100%;
      text-align: left;
      padding: 8px 10px;
      border: 1px solid var(--vscode-button-border, transparent);
      border-radius: 4px;
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
      cursor: pointer;
      font: inherit;
    }
    button.primary {
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
    }
    button:hover { opacity: 0.92; }
    .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  </style>
</head>
<body>
  <h2>AccessScope</h2>
  <div class="meta">
    <div><b>Device:</b> ${escapeHtml(deviceLabel)}</div>
    <div><b>Target package:</b> ${escapeHtml(packageLabel)}</div>
  </div>
  <div class="grid">
    <button class="primary" data-action="selectDevice">Refresh / Select Device</button>
    <button class="primary" data-action="install">Install / Update</button>
    <button class="primary" data-action="launch">Launch</button>
    <button data-action="fetchResults">Fetch Results</button>
    <button data-action="setupCheck">Setup Check</button>
    <div class="row2">
      <button data-action="clearOutput">Clear log</button>
      <button data-action="checkUpdate">Plugin update</button>
    </div>
  </div>
  <script>
    const vscode = acquireVsCodeApi();
    for (const button of document.querySelectorAll('button[data-action]')) {
      button.addEventListener('click', () => {
        vscode.postMessage({ action: button.getAttribute('data-action') });
      });
    }
  </script>
</body>
</html>`;
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
