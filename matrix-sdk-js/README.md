# @matrix/sdk (MATRIX JavaScript SDK)

`npm install @matrix/sdk`

## Usage

```typescript
import { MatrixClient } from '@matrix/sdk';

const client = new MatrixClient({
  apiKey: process.env.MATRIX_API_KEY!,
});

const resp = await client.analyze({ input: 'What is 2+2?' });
console.log(`Reply: ${resp.reply}`);
console.log(`Confidence: ${resp.confidence}`);

// Stream explain updates
client.streamExplain(resp.explain_id, {
  onUpdate: (e) => console.log(`step: ${e.steps.at(-1)?.stage}`),
  onComplete: () => console.log('done'),
});
```

## CONSTITUTION

- Article I: No LLM is invoked
- Article VIII: Apache-2.0
