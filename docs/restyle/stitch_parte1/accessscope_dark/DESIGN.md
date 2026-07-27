---
name: AccessScope Dark
colors:
  surface: '#0d1518'
  surface-dim: '#0d1518'
  surface-bright: '#323a3e'
  surface-container-lowest: '#070f12'
  surface-container-low: '#151d20'
  surface-container: '#192124'
  surface-container-high: '#232b2e'
  surface-container-highest: '#2e3639'
  on-surface: '#dbe4e8'
  on-surface-variant: '#bac9cc'
  inverse-surface: '#dbe4e8'
  inverse-on-surface: '#2a3235'
  outline: '#849396'
  outline-variant: '#3b494c'
  surface-tint: '#00daf3'
  primary: '#c3f5ff'
  on-primary: '#00363d'
  primary-container: '#00e5ff'
  on-primary-container: '#00626e'
  inverse-primary: '#006875'
  secondary: '#d1beef'
  on-secondary: '#372950'
  secondary-container: '#50426b'
  on-secondary-container: '#c2b0e0'
  tertiary: '#dcf0f8'
  on-tertiary: '#213339'
  tertiary-container: '#c0d4dc'
  on-tertiary-container: '#4a5c63'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#9cf0ff'
  primary-fixed-dim: '#00daf3'
  on-primary-fixed: '#001f24'
  on-primary-fixed-variant: '#004f58'
  secondary-fixed: '#ebdcff'
  secondary-fixed-dim: '#d1beef'
  on-secondary-fixed: '#21143a'
  on-secondary-fixed-variant: '#4e4068'
  tertiary-fixed: '#d2e6ee'
  tertiary-fixed-dim: '#b6cad1'
  on-tertiary-fixed: '#0b1e24'
  on-tertiary-fixed-variant: '#374a50'
  background: '#0d1518'
  on-background: '#dbe4e8'
  surface-variant: '#2e3639'
typography:
  headline-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.05em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 40px
  xl: 64px
  gutter: 24px
  margin: 32px
---

## Brand & Style

The design system is an immersive, high-utility framework designed for specialized technology and data environments. The brand personality is precise, technical, and forward-looking, prioritizing clarity in low-light environments. 

The aesthetic leans into a refined **Glassmorphism** and **Corporate Modern** hybrid. It utilizes deep layering and translucent surfaces to create a sense of infinite digital space. The goal is to evoke an emotional response of focus, security, and professional mastery. Every element is structured to feel like a high-end instrumentation panel—efficient, responsive, and authoritative.

## Colors

This design system uses a palette optimized for deep-space readability. The foundation is a charcoal-navy background (`#0f171a`), providing a stable, non-fatiguing base for long-term use. 

- **Primary Cyan (#00e5ff):** Reserved for high-priority actions and active states. It provides a sharp, neon-like contrast against the dark base while maintaining WCAG AAA legibility for essential interface elements.
- **Secondary Lavender (#c3b1e1):** Used for supporting information, tags, and accent icons to provide a softer visual counterpoint to the vibrant cyan.
- **Surface Tiers:** Depth is achieved through incremental lightness. Surfaces elevate from the background (`#0f171a`) to the primary container (`#162024`) and finally to interactive variants (`#1c282e`).

## Typography

Typography focuses on technical precision and maximum legibility. **Geist** is used for all primary UI text and headlines due to its clean, developer-friendly proportions. For data, metadata, and status labels, **JetBrains Mono** is employed to provide a distinct, monospaced rhythm that suggests computational accuracy.

All text is rendered in high-contrast whites (`#f0f4f5`) or muted greys for secondary content. Letter spacing is slightly tightened for large headlines and expanded for small labels to ensure clarity at all scales.

## Layout & Spacing

The design system utilizes a **Fluid Grid** model based on an 8px square baseline. This ensures vertical rhythm and consistent alignment across complex dashboards.

- **Desktop:** 12-column grid with 24px gutters and 32px outer margins.
- **Tablet:** 8-column grid with 16px gutters and 24px outer margins.
- **Mobile:** 4-column grid with 16px gutters and 16px outer margins.

Spacing is primarily applied via the `base` unit. Larger gaps between sections should use `lg` or `xl` to maintain the minimalist, airy feel required for complex data visualization.

## Elevation & Depth

In dark mode, depth is expressed through **Tonal Layering** and **Backdrop Blurs** rather than traditional heavy shadows.

- **Level 0 (Background):** The lowest layer, using `#0f171a`.
- **Level 1 (Cards/Containers):** Elevated using `#162024` with a subtle 1px border of `#ffffff1a` (10% white) to define edges.
- **Level 2 (Modals/Popovers):** Uses a translucent background with a `20px` backdrop blur. This creates a frosted-glass effect that maintains context of the layer beneath.
- **Inner Glows:** Interactive elements may feature a very subtle inner glow using the primary cyan at 5% opacity to simulate light emitting from within the component.

## Shapes

The design system employs **Soft** roundedness (`0.25rem` standard). This subtle rounding maintains a professional and technical architectural feel while preventing the interface from appearing overly aggressive or "sharp." Larger containers like cards use `0.5rem` (rounded-lg) to provide a clear container hierarchy.

## Components

### Buttons
- **Primary:** Solid Cyan (`#00e5ff`) with black text. No shadow; high-contrast focus rings.
- **Secondary:** Ghost style with a 1px Cyan border and Lavender text for high contrast.
- **Tertiary:** Text-only with an underline on hover.

### Inputs
- **Fields:** Dark surface (`#1c282e`) with a 1px border. The border glows Primary Cyan when focused.
- **Labels:** Use `label-sm` (JetBrains Mono) placed above the field for a technical look.

### Cards
- **Structure:** Background `#162024`, 1px border `#ffffff10`. 
- **Header:** Separated by a subtle hairline stroke.

### Lists & Data Tables
- **Rows:** Alternate background colors are not used; instead, use 1px hairlines to separate rows. Hover states use a subtle primary-tinted overlay at 4% opacity.

### Chips/Badges
- **Status:** Small, monospaced text. Success states use Cyan; Info states use Lavender; Warning states use a desaturated Amber.