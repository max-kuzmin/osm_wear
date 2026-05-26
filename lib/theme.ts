/**
 * Theme color definitions for Wear OS Map application
 */

export type ColorScheme = "light" | "dark";

export interface ThemeColorPalette {
  primary: string;
  background: string;
  surface: string;
  foreground: string;
  muted: string;
  border: string;
  success: string;
  warning: string;
  error: string;
}

export type SchemeColorsType = Record<ColorScheme, ThemeColorPalette>;

export const SchemeColors: SchemeColorsType = {
  light: {
    primary: "#0a7ea4",
    background: "#ffffff",
    surface: "#f5f5f5",
    foreground: "#11181C",
    muted: "#687076",
    border: "#E5E7EB",
    success: "#22C55E",
    warning: "#F59E0B",
    error: "#EF4444",
  },
  dark: {
    primary: "#0a7ea4",
    background: "#151718",
    surface: "#1e2022",
    foreground: "#ECEDEE",
    muted: "#9BA1A6",
    border: "#334155",
    success: "#4ADE80",
    warning: "#FBBF24",
    error: "#F87171",
  },
};

export const Colors = SchemeColors;

export type ThemeColors = typeof SchemeColors;

export interface ColorTokens {
  primary: string;
  background: string;
  surface: string;
  foreground: string;
  muted: string;
  border: string;
  success: string;
  warning: string;
  error: string;
}

export const Fonts = {
  regular: "System",
  medium: "System",
  bold: "System",
};
