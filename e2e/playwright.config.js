// Pruebas de punta a punta de Rabbit contra la app levantada (compose o local).
//   BASE=https://localhost:8443/Rabbit ADMIN_USER=... ADMIN_PASSWORD=... DEMO_PASSWORD=... ITEM=... npx playwright test
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0, // sin reintentos: un test inestable tiene que verse (eje B)
  reporter: [['list'], ['junit', { outputFile: process.env.JUNIT || '../target/e2e/junit.xml' }], ['html', { outputFolder: '../target/e2e/html', open: 'never' }]],
  outputDir: '../target/e2e/resultados',
  use: {
    baseURL: (process.env.BASE || 'https://localhost:8443/Rabbit') + '/',
    ignoreHTTPSErrors: true, // certificado autofirmado de WildFly
    locale: 'es-AR',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
