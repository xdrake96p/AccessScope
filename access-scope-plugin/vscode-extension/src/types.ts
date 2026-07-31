export interface DeviceInfo {
  serial: string;
  model: string;
  state: string;
  isEmulator: boolean;
}

export interface ScanResultResponse {
  sessionId: string;
  score: number;
  targetPackages: string[];
  violations: Violation[];
  summary: {
    critical: number;
    serious: number;
    moderate: number;
    minor: number;
  };
  completedAt: string;
  pdfPath?: string;
  screenReaderFindings?: ScreenReaderFinding[];
  visitedScreens?: VisitedScreen[];
}

export interface Violation {
  type: string;
  severity?: string;
  screenTitle: string;
  packageName: string;
  details: string;
  viewId?: string;
  bounds?: string;
  remediation?: string;
  measuredValue?: string;
  requiredValue?: string;
}

export interface ScreenReaderFinding {
  packageName: string;
  screenTitle: string;
  nodeClassName: string;
  announcedText?: string;
  issue: string;
  viewId?: string;
  screenFingerprint?: string;
}

export interface VisitedScreen {
  fingerprint: string;
  title: string;
  visitIndex: number;
  protectionReason: 'NONE' | 'FLAG_SECURE' | 'PIN_OR_PASSWORD' | 'SCREENSHOT_BLOCKED';
}

export interface SetupCheckResult {
  accessibilityEnabled: boolean;
  overlayEnabled: boolean;
  appInstalled: boolean;
  ready: boolean;
  hint?: string;
  versionWarning?: string;
}
