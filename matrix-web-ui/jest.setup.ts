import '@testing-library/jest-dom';

/**
 * RECON-W38.4 — jsdom does not implement `fetch`, and Node's global `fetch`
 * is not exposed in the jsdom test environment, so `jest.spyOn(global, 'fetch')`
 * failed with "Property `fetch` does not exist in the provided object" and
 * every test in `__tests__/lib/matrix-client.test.ts` failed before it could
 * assert anything.
 *
 * A stub is installed so that a test has something to spy on. Any test that
 * needs behaviour replaces it with `mockResolvedValue` / `mockRejectedValue`;
 * the stub throws if it is actually called without being replaced, so a test
 * cannot silently pass by hitting the network.
 */
if (typeof globalThis.fetch === 'undefined') {
  globalThis.fetch = (() => {
    throw new Error('fetch was called without being stubbed in this test');
  }) as unknown as typeof fetch;
}
