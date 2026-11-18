import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider, createTheme, StyledEngineProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { getDesignTokens } from './theme'
import App from './App'
import './index.css'
import useThemeStore from './store/themeStore'
import { LanguageProvider } from './i18n/LanguageProvider'

const MainApp = () => {
  const mode = useThemeStore((state) => state.mode);
  
  // Tạo theme động dựa vào chế độ sáng/tối hiện tại
  const theme = React.useMemo(() => createTheme(getDesignTokens(mode)), [mode]);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <LanguageProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </LanguageProvider>
      </ThemeProvider>
    </StyledEngineProvider>
  );
};

ReactDOM.createRoot(document.getElementById('root')).render(
  // <React.StrictMode>
    <MainApp />
  // </React.StrictMode>,
)
