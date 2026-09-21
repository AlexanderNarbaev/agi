"""
matrix-ai: Python SDK for MATRIX Hybrid Neuro-Symbolic AI API.

Basic usage:

    from matrix_ai import MatrixClient

    with MatrixClient() as client:
        resp = client.analyze(input="What is 2+2?")
        print(resp.reply)

Async usage:

    import asyncio
    from matrix_ai import AsyncMatrixClient

    async def main():
        async with AsyncMatrixClient() as client:
            resp = await client.analyze(input="hello")
            print(resp.reply)

    asyncio.run(main())

CONSTITUTION compliance: No LLM is invoked. Pure transport.
"""
from matrix_ai.client import MatrixClient, AsyncMatrixClient
from matrix_ai.exceptions import MatrixException
from matrix_ai.models import (
    AnalyzeRequest, AnalyzeResponse, ExplainResponse, ExplainStep,
    ModulatorState, ConfidenceBreakdown, FederateRequest, FederateResponse,
    AuditEntry, FederateNode, Plan,
)

__version__ = "0.1.0-T08"
__all__ = [
    "MatrixClient", "AsyncMatrixClient", "MatrixException",
    "AnalyzeRequest", "AnalyzeResponse", "ExplainResponse", "ExplainStep",
    "ModulatorState", "ConfidenceBreakdown", "FederateRequest", "FederateResponse",
    "AuditEntry", "FederateNode", "Plan",
]
