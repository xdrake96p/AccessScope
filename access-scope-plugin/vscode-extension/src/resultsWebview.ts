import * as vscode from 'vscode';
import { ScanResultResponse } from './types';

export class ResultsWebview {
  private panel: vscode.WebviewPanel | undefined;

  show(result: ScanResultResponse): void {
    if (this.panel) {
      this.panel.reveal(vscode.ViewColumn.Beside);
    } else {
      this.panel = vscode.window.createWebviewPanel(
        'accessScopeResults',
        'AccessScope Results',
        vscode.ViewColumn.Beside,
        { enableScripts: false, retainContextWhenHidden: true },
      );
      this.panel.onDidDispose(() => {
        this.panel = undefined;
      });
    }
    this.panel.webview.html = this.renderHtml(result);
  }

  private renderHtml(result: ScanResultResponse): string {
    const grouped = new Map<string, typeof result.violations>();
    for (const violation of result.violations) {
      const key = violation.screenTitle || 'Unknown screen';
      const list = grouped.get(key) ?? [];
      list.push(violation);
      grouped.set(key, list);
    }

    const screenSections = Array.from(grouped.entries())
      .map(([screen, violations]) => {
        const rows = violations
          .map((v) => {
            const remediation = v.remediation
              ? `<div class="remediation">${escapeHtml(v.remediation)}</div>`
              : '';
            const measured =
              v.measuredValue || v.requiredValue
                ? `<div class="measured">measured ${escapeHtml(v.measuredValue ?? '?')} · required ${escapeHtml(
                    v.requiredValue ?? '?',
                  )}</div>`
                : '';
            return `
              <tr>
                <td>${escapeHtml(v.severity ?? 'MODERATE')}</td>
                <td>${escapeHtml(v.type)}</td>
                <td>${escapeHtml(v.details)}${measured}${remediation}</td>
                <td><code>${escapeHtml(v.viewId ?? '')}</code></td>
              </tr>`;
          })
          .join('');
        return `
          <section>
            <h3>${escapeHtml(screen)} (${violations.length})</h3>
            <table>
              <caption class="sr-only">Accessibility violations found on screen ${escapeHtml(screen)}</caption>
              <thead>
                <tr>
                  <th scope="col">Severity</th>
                  <th scope="col">Type</th>
                  <th scope="col">Details</th>
                  <th scope="col">View ID</th>
                </tr>
              </thead>
              <tbody>${rows}</tbody>
            </table>
          </section>`;
      })
      .join('');

    const talkBackFindings = result.screenReaderFindings ?? [];
    const talkBackSection =
      talkBackFindings.length > 0
        ? `
          <section>
            <h2>TalkBack notes (${talkBackFindings.length})</h2>
            <table>
              <caption class="sr-only">Screen reader simulation notes</caption>
              <thead>
                <tr>
                  <th scope="col">Screen</th>
                  <th scope="col">Element</th>
                  <th scope="col">Issue</th>
                </tr>
              </thead>
              <tbody>
                ${talkBackFindings
                  .map(
                    (f) => `
                  <tr>
                    <td>${escapeHtml(f.screenTitle)}</td>
                    <td>${escapeHtml(f.nodeClassName)}</td>
                    <td>${escapeHtml(f.issue)}</td>
                  </tr>`,
                  )
                  .join('')}
              </tbody>
            </table>
          </section>`
        : '';

    const visitedScreens = result.visitedScreens ?? [];
    const visitedSection =
      visitedScreens.length > 0
        ? `
          <div class="meta">
            Screens visited: ${escapeHtml(
              [...visitedScreens]
                .sort((a, b) => a.visitIndex - b.visitIndex)
                .map((s) => s.title)
                .join(' → '),
            )}
          </div>`
        : '';

    const pdfSection = result.pdfPath
      ? `<div class="meta">PDF report: <code>${escapeHtml(result.pdfPath)}</code></div>`
      : '';

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <title>AccessScope Scan Results</title>
  <style>
    :root {
      color-scheme: light dark;
    }
    body {
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, sans-serif);
      font-size: var(--vscode-font-size, 13px);
      padding: 16px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
    }
    h1 { margin-bottom: 4px; }
    h2 { margin-top: 28px; }
    .meta { color: var(--vscode-descriptionForeground); margin-bottom: 16px; }
    .summary { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
    .pill {
      background: var(--vscode-badge-background);
      color: var(--vscode-badge-foreground);
      border-radius: 999px;
      padding: 6px 12px;
      font-size: 13px;
    }
    table { width: 100%; border-collapse: collapse; margin-bottom: 24px; font-size: 13px; }
    th, td {
      border-bottom: 1px solid var(--vscode-panel-border, #808080);
      text-align: left;
      padding: 8px;
      vertical-align: top;
    }
    th { background: var(--vscode-sideBar-background); }
    tr:hover td { background: var(--vscode-list-hoverBackground); }
    code { font-size: 12px; }
    .remediation, .measured {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      margin-top: 4px;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
    }
  </style>
</head>
<body>
  <h1>AccessScope Scan Results</h1>
  <div class="meta">
    Session <code>${escapeHtml(result.sessionId)}</code> ·
    Completed ${escapeHtml(result.completedAt)} ·
    Packages ${escapeHtml(result.targetPackages.join(', '))}
  </div>
  ${pdfSection}
  ${visitedSection}
  <div class="summary">
    <div class="pill">Score: ${result.score}</div>
    <div class="pill">Critical: ${result.summary.critical}</div>
    <div class="pill">Serious: ${result.summary.serious}</div>
    <div class="pill">Moderate: ${result.summary.moderate}</div>
    <div class="pill">Minor: ${result.summary.minor}</div>
    <div class="pill">Total: ${result.violations.length}</div>
  </div>
  ${screenSections || '<p>No violations found.</p>'}
  ${talkBackSection}
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
