import { MatrixClient, MatrixApiError } from '@/lib/matrix-client';

describe('MatrixClient', () => {
  describe('constructor', () => {
    it('uses default base URL', () => {
      const client = new MatrixClient({ apiKey: 'test' });
      expect(client).toBeInstanceOf(MatrixClient);
    });

    it('accepts custom base URL', () => {
      const client = new MatrixClient({ baseUrl: 'http://localhost:8080', apiKey: 'test' });
      expect(client).toBeInstanceOf(MatrixClient);
    });
  });

  describe('MatrixApiError', () => {
    it('has correct properties', () => {
      const err = new MatrixApiError(403, 'Forbidden');
      expect(err.status).toBe(403);
      expect(err.message).toBe('Forbidden');
      expect(err.name).toBe('MatrixApiError');
    });
  });

  describe('analyze', () => {
    it('sends POST /v1/analyze', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          reply: 'Paris is the capital of France.',
          confidence: 0.95,
          duration_ms: 23,
          accepted: true,
          explain_id: 'expl_abc123',
        }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'test-key' });
      const result = await client.analyze({ input: 'What is the capital of France?' });

      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.matrix.ai/v1/analyze',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            Authorization: 'Bearer test-key',
            'Content-Type': 'application/json',
          }),
        }),
      );
      expect(result.reply).toBe('Paris is the capital of France.');
      expect(result.confidence).toBe(0.95);

      fetchSpy.mockRestore();
    });

    it('throws MatrixApiError on 401', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: async () => ({ error: 'Invalid bearer token' }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'bad-key' });

      await expect(client.analyze({ input: 'hello' })).rejects.toThrow(MatrixApiError);
      await expect(client.analyze({ input: 'hello' })).rejects.toMatchObject({
        status: 401,
        message: 'Invalid bearer token',
      });

      fetchSpy.mockRestore();
    });

    it('throws MatrixApiError on 429', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: false,
        status: 429,
        statusText: 'Too Many Requests',
        json: async () => ({ error: 'Rate limit exceeded' }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });

      await expect(client.analyze({ input: 'hello' })).rejects.toMatchObject({
        status: 429,
        message: 'Rate limit exceeded',
      });

      fetchSpy.mockRestore();
    });
  });

  describe('explain', () => {
    it('sends GET /v1/explain/{id}', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          explain_id: 'expl_abc',
          steps: [{ stage: 'INPUT_NORMALIZED', action: 'norm', duration_ms: 1 }],
          modulator_snapshot: {
            ethical_filter: 0.95,
            safety_monitor: 0.92,
            consistency_checker: 0.88,
            lie_detector: 0.91,
          },
          hdc_memory_hits: ['kb-doc-42'],
          confidence_breakdown: {
            bir_confidence: 0.85,
            hdc_confidence: 0.78,
            mcts_confidence: 0.72,
            aggregate: 0.95,
          },
        }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });
      const result = await client.explain('expl_abc');

      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.matrix.ai/v1/explain/expl_abc',
        expect.objectContaining({ method: 'GET' }),
      );
      expect(result.explain_id).toBe('expl_abc');
      expect(result.modulator_snapshot.ethical_filter).toBe(0.95);

      fetchSpy.mockRestore();
    });

    it('encodes special characters in explain_id', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        json: async () => ({ error: 'Unknown' }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });
      await expect(client.explain('expl_with/slash')).rejects.toThrow();

      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.matrix.ai/v1/explain/expl_with%2Fslash',
        expect.anything(),
      );

      fetchSpy.mockRestore();
    });
  });

  describe('joinFederation', () => {
    it('sends POST /v1/federate', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: true,
        status: 201,
        json: async () => ({
          node_id: 'node_abc',
          region: 'us-east',
          shard_capacity: 100,
          joined_at: '2026-09-21T17:00:00Z',
          total_nodes: 1,
        }),
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });
      const result = await client.joinFederation({ region: 'us-east', shard_capacity: 100 });

      expect(result.node_id).toBe('node_abc');
      expect(result.region).toBe('us-east');

      fetchSpy.mockRestore();
    });
  });

  describe('auditLogs', () => {
    it('fetches audit logs with limit', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => [
          {
            timestamp: '2026-09-21T17:00:00Z',
            user_id: 'user-1',
            action: 'POST /v1/analyze',
            target: 'alice@example.com',
            status_code: 200,
          },
        ],
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });
      const logs = await client.auditLogs(50);

      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.matrix.ai/v1/audit/logs?limit=50',
        expect.anything(),
      );
      expect(logs).toHaveLength(1);

      fetchSpy.mockRestore();
    });

    it('uses default limit of 100', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => [],
      } as Response);

      const client = new MatrixClient({ apiKey: 'test' });
      await client.auditLogs();

      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.matrix.ai/v1/audit/logs?limit=100',
        expect.anything(),
      );

      fetchSpy.mockRestore();
    });
  });

  describe('network errors', () => {
    it('wraps network errors in MatrixApiError', async () => {
      const fetchSpy = jest.spyOn(global, 'fetch').mockRejectedValue(
        new Error('Connection refused'),
      );

      const client = new MatrixClient({ apiKey: 'test' });

      await expect(client.analyze({ input: 'hello' })).rejects.toMatchObject({
        status: 0,
        message: expect.stringContaining('Network error'),
      });

      fetchSpy.mockRestore();
    });
  });
});
