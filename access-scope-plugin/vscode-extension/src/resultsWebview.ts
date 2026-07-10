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
          .map(
            (v) => `
              <tr>
                <td>${escapeHtml(v.severity ?? 'MODERATE')}</td>
                <td>${escapeHtml(v.type)}</td>
                <td>${escapeHtml(v.details)}</td>
                <td><code>${escapeHtml(v.viewId ?? '')}</code></td>
              </tr>`,
          )
          .join('');
        return `
          <section>
            <h3>${escapeHtml(screen)} (${violations.length})</h3>
            <table>
              <thead>
                <tr><th>Severity</th><th>Type</th><th>Details</th><th>View ID</th></tr>
              </thead>
              <tbody>${rows}</tbody>
            </table>
          </section>`;
      })
      .join('');

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 16px; color: #111; }
    h1 { margin-bottom: 4px; }
    .meta { color: #555; margin-bottom: 16px; }
    .summary { display: flex; gap: 12px; margin-bottom: 20px; }
    .pill { background: #f2f2f2; border-radius: 999px; padding: 6px 12px; font-size: 13px; }
    table { width: 100%; border-collapse: collapse; margin-bottom: 24px; font-size: 13px; }
    th, td { border-bottom: 1px solid #ddd; text-align: left; padding: 8px; vertical-align: top; }
    th { background: #fafafa; }
    code { font-size: 12px; }
  </style>
</head>
<body>
  <h1>AccessScope Scan Results</h1>
  <div class="meta">
    Session <code>${escapeHtml(result.sessionId)}</code> ·
    Completed ${escapeHtml(result.completedAt)} ·
    Packages ${escapeHtml(result.targetPackages.join(', '))}
  </div>
  <div class="summary">
    <div class="pill">Score: ${result.score}</div>
    <div class="pill">Critical: ${result.summary.critical}</div>
    <div class="pill">Serious: ${result.summary.serious}</div>
    <div class="pill">Moderate: ${result.summary.moderate}</div>
    <div class="pill">Minor: ${result.summary.minor}</div>
    <div class="pill">Total: ${result.violations.length}</div>
  </div>
  ${screenSections || '<p>No violations found.</p>'}
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
