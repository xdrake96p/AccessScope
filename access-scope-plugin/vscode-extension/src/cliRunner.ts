import { execFile } from 'child_process';
import * as fs from 'fs';
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
      execFile(javaPath, fullArgs, { maxBuffer: 10 * 1024 * 1024 }, (error, stdout, stderr) => {
        if (stderr.trim()) {
          this.output.appendLine(stderr.trim());
        }
        if (stdout.trim()) {
          this.output.appendLine(stdout.trim());
        }
        if (error) {
          reject(new Error(stderr.trim() || error.message));
          return;
        }
        resolve(stdout.trim());
      });
    });
  }

  private resolveJarPath(configured: string): string {
    if (configured && fs.existsSync(configured)) {
      return configured;
    }
    const bundled = path.join(__dirname, '..', 'bin', 'access-scope-cli.jar');
    if (fs.existsSync(bundled)) {
      return bundled;
    }
    const devJar = path.join(
      __dirname,
      '..',
      '..',
      'cli',
      'build',
      'libs',
      'cli-1.0.0-all.jar',
    );
    if (fs.existsSync(devJar)) {
      return devJar;
    }
    throw new Error('access-scope-cli JAR not found. Build it with ./gradlew :cli:fatJar');
  }

  private resolveJavaPath(configured: string): string {
    if (configured && fs.existsSync(configured)) {
      return configured;
    }
    const studioJava =
      '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java';
    if (process.platform === 'darwin' && fs.existsSync(studioJava)) {
      return studioJava;
    }
    return 'java';
  }
}
