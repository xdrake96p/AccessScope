import { execFile } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';

export class CliRunner {
  constructor(private readonly output: vscode.OutputChannel) {}

  async run(args: string[]): Promise<string> {
    const config = vscode.workspace.getConfiguration('accessScope');
    const javaPath = this.resolveJavaPath(config.get<string>('javaPath', ''));
    const jarPath = this.resolveJarPath(config.get<string>('cliPath', ''));
    const device = config.get<string>('defaultDevice', '');
    const fullArgs = ['-jar', jarPath, ...args];
    if (device && !args.some((arg, index) => arg === '--device' && args[index + 1])) {
      fullArgs.push('--device', device);
    }

    this.output.appendLine(`$ ${javaPath} ${fullArgs.join(' ')}`);

    return new Promise((resolve, reject) => {
      execFile(
        javaPath,
        fullArgs,
        { maxBuffer: 10 * 1024 * 1024, env: this.buildEnv() },
        (error, stdout, stderr) => {
          const combined = [stderr, stdout].filter((chunk) => chunk.trim()).join('\n');
          if (stderr.trim()) {
            this.output.appendLine(stderr.trim());
          }
          if (stdout.trim()) {
            this.output.appendLine(stdout.trim());
          }
          if (error) {
            reject(new Error(this.formatCliError(combined || error.message)));
            return;
          }
          resolve(stdout.trim());
        },
      );
    });
  }

  private buildEnv(): NodeJS.ProcessEnv {
    const env = { ...process.env };
    const sdkRoot = this.resolveSdkRoot();
    if (sdkRoot) {
      env.ACCESS_SCOPE_SDK_ROOT = sdkRoot;
      const platformTools = path.join(sdkRoot, 'platform-tools');
      if (fs.existsSync(platformTools)) {
        env.PATH = `${platformTools}${path.delimiter}${env.PATH ?? ''}`;
      }
    }
    return env;
  }

  private resolveSdkRoot(): string | undefined {
    for (const key of ['ACCESS_SCOPE_SDK_ROOT', 'ANDROID_SDK_ROOT', 'ANDROID_HOME']) {
      const value = process.env[key];
      if (value && fs.existsSync(value)) {
        return value;
      }
    }

    const folders = vscode.workspace.workspaceFolders ?? [];
    for (const folder of folders) {
      const fromLocal = this.readSdkDirFromLocalProperties(path.join(folder.uri.fsPath, 'local.properties'));
      if (fromLocal) {
        return fromLocal;
      }
    }

    const home = os.homedir();
    for (const candidate of [
      path.join(home, 'Library', 'Android', 'sdk'),
      path.join(home, 'Android', 'Sdk'),
    ]) {
      if (fs.existsSync(candidate)) {
        return candidate;
      }
    }
    return undefined;
  }

  private readSdkDirFromLocalProperties(filePath: string): string | undefined {
    if (!fs.existsSync(filePath)) {
      return undefined;
    }
    const match = fs.readFileSync(filePath, 'utf8').match(/^sdk\.dir=(.+)$/m);
    if (!match) {
      return undefined;
    }
    const sdk = match[1].trim().replace(/\\\\/g, '\\');
    return fs.existsSync(sdk) ? sdk : undefined;
  }

  private formatCliError(raw: string): string {
    const withoutPrefix = raw.replace(/^ERROR:\s*/i, '').trim();
    if (
      withoutPrefix.includes('INSTALL_FAILED_UPDATE_INCOMPATIBLE') ||
      withoutPrefix.includes('signatures do not match')
    ) {
      return 'Firma APK incompatibile sul device. Riesegui Install: il CLI disinstalla e reinstalla automaticamente.';
    }
    if (withoutPrefix.includes('adb not found')) {
      return 'adb non trovato. Verifica Android SDK platform-tools o imposta ANDROID_SDK_ROOT.';
    }
    return withoutPrefix.replace(/^ERROR:\s*/i, '').trim();
  }

  private resolveJarPath(configured: string): string {
    if (configured && fs.existsSync(configured)) {
      return configured;
    }
    const bundled = path.join(__dirname, '..', 'bin', 'AccessScope-cli.jar');
    if (fs.existsSync(bundled)) {
      return bundled;
    }
    const legacyBundled = path.join(__dirname, '..', 'bin', 'access-scope-cli.jar');
    if (fs.existsSync(legacyBundled)) {
      return legacyBundled;
    }
    const devJar = path.join(__dirname, '..', '..', 'cli', 'build', 'libs', 'cli-1.0.0-all.jar');
    if (fs.existsSync(devJar)) {
      return devJar;
    }
    throw new Error('AccessScope CLI JAR not found. Reinstall the extension VSIX.');
  }

  private resolveJavaPath(configured: string): string {
    if (configured && fs.existsSync(configured)) {
      return configured;
    }
    const studioJava = '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java';
    if (process.platform === 'darwin' && fs.existsSync(studioJava)) {
      return studioJava;
    }
    return 'java';
  }
}

export function detectTargetPackage(): string | undefined {
  const folders = vscode.workspace.workspaceFolders ?? [];
  for (const folder of folders) {
    for (const fileName of ['app/build.gradle', 'app/build.gradle.kts']) {
      const gradleFile = path.join(folder.uri.fsPath, fileName);
      if (!fs.existsSync(gradleFile)) {
        continue;
      }
      const text = fs.readFileSync(gradleFile, 'utf8');
      const baseMatch =
        text.match(/applicationId\s*[= ]\s*"([^"]+)"/) ??
        text.match(/applicationId\s*=\s*"([^"]+)"/);
      if (!baseMatch) {
        continue;
      }
      const suffixMatches = [
        ...text.matchAll(/applicationIdSuffix\s*[= ]\s*'([^']+)'/g),
        ...text.matchAll(/applicationIdSuffix\s*=\s*"([^"]+)"/g),
      ];
      const suffix = suffixMatches.at(-1)?.[1] ?? '';
      return `${baseMatch[1]}${suffix}`;
    }
  }
  return undefined;
}
