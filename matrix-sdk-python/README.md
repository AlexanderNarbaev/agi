# matrix-ai (MATRIX Python SDK)

`pip install matrix-ai`

## Usage

```python
from matrix_ai import MatrixClient

with MatrixClient() as client:
    resp = client.analyze(input="What is 2+2?")
    print(f"Reply: {resp.reply}")
    print(f"Confidence: {resp.confidence}")
    print(f"Explain ID: {resp.explain_id}")

    # Get XAI breakdown
    explain = client.explain(resp.explain_id)
    for step in explain.steps:
        print(f"  [{step.stage}] {step.duration_ms}ms")
```

## Async

```python
import asyncio
from matrix_ai import AsyncMatrixClient

async def main():
    async with AsyncMatrixClient() as client:
        resp = await client.analyze(input="hello")
        print(resp.reply)

asyncio.run(main())
```

## Development

```bash
pip install -r requirements.txt
pytest
```

## CONSTITUTION

- Article I: No LLM is invoked (pure transport)
- Article VIII: Apache-2.0
