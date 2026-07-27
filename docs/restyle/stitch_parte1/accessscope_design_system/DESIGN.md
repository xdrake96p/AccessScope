---
name: AccessScope Design System
colors:
  surface: '#fcf9f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fcf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f2'
  surface-container: '#f0edec'
  surface-container-high: '#ebe7e7'
  surface-container-highest: '#e5e2e1'
  on-surface: '#1c1b1b'
  on-surface-variant: '#3b494c'
  inverse-surface: '#313030'
  inverse-on-surface: '#f3f0ef'
  outline: '#6b7a7d'
  outline-variant: '#bac9cc'
  surface-tint: '#006875'
  primary: '#006875'
  on-primary: '#ffffff'
  primary-container: '#00e5ff'
  on-primary-container: '#00626e'
  inverse-primary: '#00daf3'
  secondary: '#5b00df'
  on-secondary: '#ffffff'
  secondary-container: '#7531ff'
  on-secondary-container: '#eadfff'
  tertiary: '#765a00'
  on-tertiary: '#ffffff'
  tertiary-container: '#fec931'
  on-tertiary-container: '#6f5500'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#9cf0ff'
  primary-fixed-dim: '#00daf3'
  on-primary-fixed: '#001f24'
  on-primary-fixed-variant: '#004f58'
  secondary-fixed: '#e8ddff'
  secondary-fixed-dim: '#cfbdff'
  on-secondary-fixed: '#22005d'
  on-secondary-fixed-variant: '#5300cd'
  tertiary-fixed: '#ffdf96'
  tertiary-fixed-dim: '#f3bf26'
  on-tertiary-fixed: '#251a00'
  on-tertiary-fixed-variant: '#594400'
  background: '#fcf9f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.08em
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 40px
  xl: 64px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 32px
---

## Brand & Style
The design system is rooted in a **Futuristic Minimalist** aesthetic, engineered to feel like a high-tech diagnostic tool while remaining accessible and human-centric. It prioritizes clarity, precision, and technical sophistication.

The visual narrative uses a "Scanner & HUD" metaphor—clean, surgical layouts with vibrant, neon-inflected accents that highlight critical data points. Large areas of whitespace (or "darkspace" in dark mode) ensure that the high-contrast elements remain the focal point, reducing cognitive load for users performing complex accessibility audits.

**Key Visual Pillars:**
- **Precision Engineering:** Sharp, purposeful alignment and consistent thinning of strokes.
- **Illuminated Data:** Use of glow effects and vibrant semantic colors to indicate status.
- **Layered Intelligence:** A clear sense of depth using tonal elevation and subtle translucency.

## Colors
The palette is built on a "Neon on Neutral" foundation. The primary **Electric Teal** serves as the primary action color and the signature "scanning" identifier.

- **Primary:** Electric Teal (#00E5FF) used for key actions, progress indicators, and active states.
- **Dark Mode Background:** A custom "Deep Space" Charcoal (#0A0E14) provides higher contrast for neon accents than pure black.
- **High Contrast:** All semantic colors are calibrated to meet WCAG AA standards against both light and dark backgrounds. 
- **Surface Tints:** In dark mode, surfaces use a 5-8% opacity overlay of the primary color to create a sense of unified atmospheric lighting.

## Typography
The system uses a tri-font strategy to balance character with utility:
1. **Hanken Grotesk (Headlines):** A sharp, modern sans-serif that provides a tech-forward "startup" feel.
2. **Inter (Body):** Chosen for its exceptional legibility at small sizes and extensive accessibility support.
3. **JetBrains Mono (Labels/Technical):** Used for status chips, IDs, and data points to reinforce the "scanner/code" aesthetic.

**Hierarchy Rules:**
- Use **Display-lg** sparingly for dashboard overviews (e.g., total accessibility score).
- **Labels** should always be uppercase when used in status chips or navigation tabs to enhance scannability.

## Layout & Spacing
This design system utilizes a **8px linear scale** for consistent vertical rhythm and a **fluid grid** for horizontal adaptability.

- **Mobile Layout:** 4-column fluid grid with 20px side margins and 16px gutters. 
- **Safe Zones:** Generous padding (24px+) around interactive targets to ensure the app exceeds touch-target size requirements (minimum 48x48px).
- **Density:** High whitespace in Light mode for "breathability"; slightly tighter spacing in Dark mode to emphasize the HUD (Heads-Up Display) effect.

## Elevation & Depth
Elevation is communicated through **Tonal Layering** rather than heavy shadows, maintaining a clean, futuristic look.

- **Level 0 (Base):** Background color.
- **Level 1 (Cards):** Subtly lighter/darker than background with a 1px border. 
- **Level 2 (Modals/Drawers):** Features a "Glassmorphic" blur (20px backdrop filter) with a 10% opacity white border to simulate light catching the edge of a lens.
- **Shadows:** Only used for the Primary Action Button and active Cards. Shadows are ultra-diffused (30px blur), low opacity (12%), and tinted with the Primary Electric Teal to create a "glow" rather than a drop shadow.

## Shapes
The system utilizes **Rounded** geometry (8px / 0.5rem base) to soften the technical edge and make the app feel approachable.

- **Standard Elements:** 8px radius (Inputs, Small Cards).
- **Large Containers:** 16px radius (Main Content Cards, Bottom Sheets).
- **Interactive Pill:** Full-radius (Pill) used for Status Chips and the primary "Scan" button to differentiate them as distinct, touchable objects.

## Components

### Bottom Navigation Bar
- **Style:** Floating bar with a 24px bottom margin or a solid-docked bar with a subtle top border.
- **Interaction:** Active states use the Primary Teal with a small glow dot beneath the icon.

### Status Chips
- **Style:** Pill-shaped, JetBrains Mono font.
- **Variations:** 
  - *Success:* Subtle green tint background with high-contrast green text.
  - *Critical:* Solid red background with white text for maximum urgency.

### Cards & Accordions
- **Design:** No heavy borders. Use a subtle 1px stroke (#E0E0E0 in light / #2C2C2C in dark).
- **Accordions:** Use a chevron-right that rotates 90 degrees downward. Expanded state uses a soft primary-color tint background to group related content.

### Pie Charts (Accessibility Distribution)
- **Visuals:** "Donut" style with a thick 12px stroke. Center of the donut displays the aggregate percentage in **Hanken Grotesk Bold**.

### Input Fields
- **Design:** Underline style with a subtle background fill. The underline transforms into a 2px Primary Teal line on focus, accompanied by a floating label.

### Sidebar (Drawer)
- **Style:** Full-height blur effect. Navigation items use 24px icons and a clear active-state indicator (a vertical teal bar on the left edge).