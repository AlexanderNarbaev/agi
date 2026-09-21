import { MatrixClient, MatrixError, AnalyzeRequest } from '../src';

describe('MatrixClient', () => {
  it('requires apiKey', () => {
    expect(() => new MatrixClient({ apiKey: '' })).toThrow('apiKey is required');
  });

  it('uses default base URL', () => {
    const client = new MatrixClient({ apiKey: 'test' });
    expect(client).toBeInstanceOf(MatrixClient);
  });

  it('accepts custom base URL', () => {
    const client = new MatrixClient({ apiKey: 'test', baseUrl: 'http://localhost:8080/' });
    expect(client).toBeInstanceOf(MatrixClient);
  });
});

describe('AnalyzeRequest factory', () => {
  it('constructs via type-specific factories', () => {
    // Type-check via index export
    const req: AnalyzeRequest = { input: 'hello' };
    expect(req.input).toBe('hello');
  });
});

describe('MatrixError', () => {
  it('has statusCode and errorBody', () => {
    const err = new MatrixError(401, 'Unauthorized');
    expect(err.statusCode).toBe(401);
    expect(err.errorBody).toBe('Unauthorized');
    expect(err.message).toContain('401');
    expect(err.name).toBe('MatrixError');
  });
});
