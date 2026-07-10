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
}

export interface Violation {
  type: string;
  severity?: string;
  screenTitle: string;
  packageName: string;
  details: string;
  viewId?: string;
  bounds?: string;
}

export interface SetupCheckResult {
  accessibilityEnabled: boolean;
  overlayEnabled: boolean;
  appInstalled: boolean;
  ready: boolean;
  hint?: string;
}
