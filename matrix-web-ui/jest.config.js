const nextJest = require('next/jest')

/**
 * RECON-W38.4 — the jest configuration was never runnable:
 *
 *   1. `setupFilesAfterEach` is not a Jest option (it was silently ignored);
 *      the correct name is `setupFilesAfterEnv`, so `jest.setup.ts` - which
 *      loads @testing-library/jest-dom - was never loaded and every
 *      `toBeInTheDocument()` assertion would have failed.
 *   2. The transform named `ts-jest`, which was not a declared dependency, so
 *      jest aborted with "Module ts-jest in the transform option was not
 *      found".
 *   3. `testEnvironment: 'jsdom'` needs `jest-environment-jsdom`, which
 *      Jest 28+ no longer ships by default; it was not a declared dependency
 *      either, so jest aborted before running a single test.
 *
 * `next/jest` is the configuration Next.js itself documents: it transforms
 * TypeScript and TSX through the same SWC pipeline the application uses, and
 * it needs no separate transform dependency. `jest-environment-jsdom` remains
 * a declared devDependency because the jsdom environment is requested
 * explicitly.
 *
 * @type {import('jest').Config}
 */
const createJestConfig = nextJest({ dir: './' })

module.exports = createJestConfig({
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  testMatch: ['<rootDir>/__tests__/**/*.test.{ts,tsx}'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/$1',
  },
  collectCoverageFrom: [
    'app/**/*.{ts,tsx}',
    'components/**/*.{ts,tsx}',
    '!**/*.d.ts',
    '!**/node_modules/**',
  ],
})
