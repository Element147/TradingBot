import {
  alpha,
  createTheme,
  type PaletteMode,
  type Theme,
} from '@mui/material/styles';

const buildTheme = (mode: PaletteMode): Theme => {
  const isLight = mode === 'light';
  const primaryMain = isLight ? '#186660' : '#8edfd4';
  const secondaryMain = isLight ? '#ad6a25' : '#ffd086';
  const successMain = isLight ? '#147351' : '#79e0aa';
  const warningMain = isLight ? '#b7761f' : '#ffd07a';
  const errorMain = isLight ? '#b02c21' : '#ffaaa1';
  const infoMain = isLight ? '#2b67c7' : '#8dbcff';
  const canvasBackground = isLight ? '#f3efe6' : '#0d1318';
  const panelBackground = isLight ? '#fbfaf7' : '#151d23';
  const raisedBackground = isLight ? '#f7f4ee' : '#121920';
  const shellBackground = isLight ? '#ece5da' : '#0a1015';
  const textPrimary = isLight ? '#152229' : '#edf5f1';
  const textSecondary = isLight ? '#60707a' : '#9baab3';
  const divider = alpha(isLight ? '#16384b' : '#dbeae3', isLight ? 0.12 : 0.14);
  const surfaceRadius = 16;
  const controlRadius = 14;
  const numericFont =
    '"IBM Plex Mono", "Cascadia Mono", "Aptos Mono", "Consolas", monospace';
  const sansFont =
    '"IBM Plex Sans", "Aptos", "Segoe UI Variable Text", "Segoe UI", sans-serif';
  const focusRing = `0 0 0 3px ${alpha(primaryMain, isLight ? 0.18 : 0.26)}`;

  return createTheme({
    spacing: 4,
    palette: {
      mode,
      primary: {
        main: primaryMain,
        light: isLight ? '#2c8f87' : '#b4ebe3',
        dark: isLight ? '#0f514c' : '#5bc9bc',
        contrastText: isLight ? '#f7fffd' : '#06211f',
      },
      secondary: {
        main: secondaryMain,
        light: isLight ? '#cb8a43' : '#ffe0ab',
        dark: isLight ? '#825019' : '#d5a054',
        contrastText: isLight ? '#fff9f2' : '#2f1a03',
      },
      success: {
        main: successMain,
        light: isLight ? '#28a16f' : '#a7edc7',
        dark: isLight ? '#0f563b' : '#45bf84',
        contrastText: isLight ? '#f6fff9' : '#07281b',
      },
      warning: {
        main: warningMain,
        light: isLight ? '#daa04a' : '#ffe4ae',
        dark: isLight ? '#855012' : '#ddb25e',
        contrastText: isLight ? '#fffaf2' : '#342003',
      },
      error: {
        main: errorMain,
        light: isLight ? '#d9584c' : '#ffc2bb',
        dark: isLight ? '#7a1f19' : '#e7776c',
        contrastText: isLight ? '#fff7f6' : '#320806',
      },
      info: {
        main: infoMain,
        light: isLight ? '#5d92e5' : '#b1d3ff',
        dark: isLight ? '#1f4b92' : '#5c9ce4',
        contrastText: isLight ? '#f7faff' : '#08182e',
      },
      background: {
        default: canvasBackground,
        paper: panelBackground,
      },
      text: {
        primary: textPrimary,
        secondary: textSecondary,
        disabled: alpha(textPrimary, 0.42),
      },
      divider,
    },
    shape: {
      borderRadius: surfaceRadius,
    },
    typography: {
      fontFamily: sansFont,
      fontSize: 14,
      h1: {
        fontFamily: sansFont,
        fontSize: '2.55rem',
        fontWeight: 700,
        letterSpacing: '-0.05em',
        lineHeight: 1.04,
      },
      h2: {
        fontFamily: sansFont,
        fontSize: '2.1rem',
        fontWeight: 700,
        letterSpacing: '-0.04em',
        lineHeight: 1.08,
      },
      h3: {
        fontFamily: sansFont,
        fontSize: '1.72rem',
        fontWeight: 700,
        letterSpacing: '-0.035em',
        lineHeight: 1.1,
      },
      h4: {
        fontFamily: sansFont,
        fontSize: '1.45rem',
        fontWeight: 700,
        letterSpacing: '-0.03em',
        lineHeight: 1.15,
      },
      h5: {
        fontFamily: sansFont,
        fontSize: '1.22rem',
        fontWeight: 700,
        letterSpacing: '-0.02em',
        lineHeight: 1.18,
      },
      h6: {
        fontFamily: sansFont,
        fontSize: '1.02rem',
        fontWeight: 700,
        letterSpacing: '-0.01em',
        lineHeight: 1.18,
      },
      subtitle1: {
        fontSize: '0.98rem',
        fontWeight: 600,
        lineHeight: 1.4,
      },
      subtitle2: {
        fontSize: '0.84rem',
        fontWeight: 700,
        letterSpacing: '0.02em',
        lineHeight: 1.28,
      },
      body1: {
        lineHeight: 1.52,
      },
      body2: {
        lineHeight: 1.44,
      },
      caption: {
        lineHeight: 1.32,
      },
      overline: {
        fontWeight: 700,
        letterSpacing: '0.11em',
        lineHeight: 1.2,
      },
      button: {
        textTransform: 'none',
        fontWeight: 700,
        letterSpacing: '0.01em',
      },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          html: {
            backgroundColor: shellBackground,
          },
          body: {
            backgroundColor: canvasBackground,
            color: textPrimary,
          },
          '#root': {
            minHeight: '100vh',
          },
          '*': {
            scrollbarWidth: 'thin',
            scrollbarColor: `${alpha(primaryMain, 0.38)} transparent`,
            boxSizing: 'border-box',
          },
          'code, pre, kbd, samp': {
            fontFamily: numericFont,
            fontVariantNumeric: 'tabular-nums',
          },
          '.skip-link': {
            position: 'absolute',
            left: 12,
            top: -80,
            zIndex: 2000,
            padding: '10px 14px',
            borderRadius: surfaceRadius,
            backgroundColor: panelBackground,
            border: `1px solid ${divider}`,
            color: textPrimary,
            textDecoration: 'none',
            transition: 'top 140ms ease',
          },
          '.skip-link:focus': {
            top: 12,
          },
          '::selection': {
            backgroundColor: alpha(primaryMain, 0.18),
          },
          '*::-webkit-scrollbar': {
            width: 11,
            height: 11,
          },
          '*::-webkit-scrollbar-thumb': {
            backgroundColor: alpha(primaryMain, 0.3),
            borderRadius: 999,
            border: `3px solid ${alpha(shellBackground, 0.08)}`,
          },
          '*::-webkit-scrollbar-track': {
            backgroundColor: 'transparent',
          },
          ':focus-visible': {
            outline: 'none',
            boxShadow: focusRing,
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            backgroundColor: alpha(raisedBackground, isLight ? 0.84 : 0.9),
            borderBottom: `1px solid ${divider}`,
            boxShadow: 'none',
            backdropFilter: 'blur(14px)',
          },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: {
            backgroundColor: isLight ? '#f7f3eb' : '#10171d',
            borderRight: `1px solid ${divider}`,
            boxShadow: 'none',
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
          outlined: {
            borderColor: divider,
            backgroundColor: panelBackground,
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            backgroundColor: panelBackground,
            border: `1px solid ${divider}`,
            borderRadius: surfaceRadius,
            boxShadow: 'none',
          },
        },
      },
      MuiCardContent: {
        styleOverrides: {
          root: {
            padding: 18,
            '&:last-child': {
              paddingBottom: 18,
            },
          },
        },
      },
      MuiButton: {
        defaultProps: {
          disableElevation: true,
        },
        styleOverrides: {
          root: {
            minHeight: 38,
            borderRadius: controlRadius,
            paddingInline: 14,
          },
          contained: {
            boxShadow: 'none',
          },
          outlined: {
            borderColor: alpha(textPrimary, 0.14),
          },
        },
      },
      MuiIconButton: {
        styleOverrides: {
          root: {
            borderRadius: controlRadius,
            border: `1px solid ${alpha(textPrimary, isLight ? 0.08 : 0.14)}`,
            backgroundColor: alpha(panelBackground, 0.9),
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            minHeight: 28,
            borderRadius: controlRadius,
            borderColor: alpha(textPrimary, 0.08),
            backgroundColor: alpha(panelBackground, isLight ? 0.98 : 0.82),
            fontWeight: 700,
            height: 'auto',
            alignItems: 'flex-start',
          },
          label: {
            display: 'block',
            whiteSpace: 'normal',
            lineHeight: 1.2,
            paddingTop: 5,
            paddingBottom: 5,
          },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: {
            borderRadius: surfaceRadius,
            border: `1px solid ${divider}`,
            backgroundColor: alpha(panelBackground, 0.98),
          },
        },
      },
      MuiMenu: {
        styleOverrides: {
          paper: {
            marginTop: 10,
            borderRadius: surfaceRadius,
            border: `1px solid ${divider}`,
            backgroundColor: panelBackground,
            boxShadow: `0 24px 48px ${alpha('#000000', 0.12)}`,
          },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            borderRadius: controlRadius,
            marginInline: 8,
            marginBlock: 2,
          },
        },
      },
      MuiToolbar: {
        styleOverrides: {
          root: {
            minHeight: 72,
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderColor: divider,
            verticalAlign: 'top',
            paddingTop: 10,
            paddingBottom: 10,
          },
          head: {
            fontSize: '0.68rem',
            fontWeight: 700,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: textSecondary,
          },
        },
      },
      MuiTableContainer: {
        styleOverrides: {
          root: {
            borderRadius: surfaceRadius,
            border: `1px solid ${divider}`,
            backgroundColor: panelBackground,
          },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: {
            transition: 'background-color 120ms ease',
            '&:hover': {
              backgroundColor: alpha(primaryMain, isLight ? 0.05 : 0.08),
            },
            '&.Mui-selected': {
              backgroundColor: alpha(primaryMain, isLight ? 0.11 : 0.16),
            },
          },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: controlRadius,
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: controlRadius,
            backgroundColor: alpha(panelBackground, 0.94),
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: alpha(textPrimary, isLight ? 0.1 : 0.16),
            },
            '&:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: alpha(primaryMain, 0.32),
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderWidth: 1,
              borderColor: primaryMain,
            },
          },
        },
      },
      MuiToggleButtonGroup: {
        styleOverrides: {
          root: {
            padding: 4,
            borderRadius: surfaceRadius,
            backgroundColor: alpha(textPrimary, isLight ? 0.03 : 0.08),
            border: `1px solid ${divider}`,
          },
          grouped: {
            margin: 0,
            border: 0,
            borderRadius: controlRadius,
          },
        },
      },
      MuiToggleButton: {
        styleOverrides: {
          root: {
            borderRadius: controlRadius,
            color: textSecondary,
            '&.Mui-selected': {
              backgroundColor: alpha(primaryMain, isLight ? 0.13 : 0.16),
              color: textPrimary,
            },
          },
        },
      },
      MuiTabs: {
        styleOverrides: {
          indicator: {
            height: 3,
            borderRadius: surfaceRadius,
            backgroundColor: primaryMain,
          },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            alignItems: 'flex-start',
            minHeight: 52,
            fontWeight: 700,
            paddingInline: 12,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: surfaceRadius,
            border: `1px solid ${divider}`,
            backgroundColor: panelBackground,
          },
        },
      },
      MuiLinearProgress: {
        styleOverrides: {
          root: {
            height: 7,
            borderRadius: 999,
          },
        },
      },
      MuiSkeleton: {
        styleOverrides: {
          root: {
            borderRadius: surfaceRadius,
            backgroundColor: alpha(textPrimary, isLight ? 0.08 : 0.12),
            transform: 'none',
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            borderRadius: surfaceRadius,
            backgroundColor: isLight ? '#183037' : '#f3f7f5',
            color: isLight ? '#f4f9f7' : '#102228',
            boxShadow: 'none',
            padding: '8px 10px',
          },
        },
      },
      MuiDivider: {
        styleOverrides: {
          root: {
            borderColor: divider,
          },
        },
      },
      MuiAccordion: {
        styleOverrides: {
          root: {
            border: `1px solid ${divider}`,
            borderRadius: `${surfaceRadius}px !important`,
            backgroundColor: panelBackground,
            boxShadow: 'none',
            '&:before': {
              display: 'none',
            },
          },
        },
      },
      MuiAccordionSummary: {
        styleOverrides: {
          root: {
            minHeight: 58,
          },
          content: {
            marginBlock: 14,
          },
        },
      },
      MuiAccordionDetails: {
        styleOverrides: {
          root: {
            paddingTop: 0,
          },
        },
      },
    },
  });
};

export const lightTheme: Theme = buildTheme('light');
export const darkTheme: Theme = buildTheme('dark');
