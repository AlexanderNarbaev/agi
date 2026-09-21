"""MATRIX Python SDK — sync + async clients for matrix-api-gateway."""
from __future__ import annotations

import os
from typing import Callable, List, Optional
from urllib.parse import quote

import requests

from matrix_ai.exceptions import MatrixException
from matrix_ai.models import (
    AnalyzeRequest, AnalyzeResponse, ExplainResponse,
    FederateRequest, FederateResponse, FederateNode, AuditEntry,
)


DEFAULT_BASE_URL = "https://api.matrix.ai"


class MatrixClient:
    """Synchronous MATRIX API client. Use as a context manager."""

    def __init__(self, api_key: Optional[str] = None, base_url: str = DEFAULT_BASE_URL,
                 timeout: float = 30.0):
        self.api_key = api_key or os.environ.get("MATRIX_API_KEY")
        if not self.api_key:
            raise ValueError("api_key required (or set MATRIX_API_KEY env var)")
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._session = requests.Session()
        self._session.headers.update({
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        })

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def close(self):
        self._session.close()

    def analyze(self, input: Optional[str] = None, content_type: str = "text",
                context: Optional[str] = None, model: str = "default",
                request: Optional[AnalyzeRequest] = None) -> AnalyzeResponse:
        if request is None:
            request = AnalyzeRequest(input=input or "", content_type=content_type,
                                    context=context, model=model)
        resp = self._session.post(f"{self.base_url}/v1/analyze",
                                   json=request.model_dump(exclude_none=True),
                                   timeout=self.timeout)
        self._raise_for_status(resp)
        return AnalyzeResponse.model_validate(resp.json())

    def explain(self, explain_id: str) -> ExplainResponse:
        encoded = quote(explain_id, safe="")
        resp = self._session.get(f"{self.base_url}/v1/explain/{encoded}",
                                  timeout=self.timeout)
        self._raise_for_status(resp)
        return ExplainResponse.model_validate(resp.json())

    def federate(self, region: str, shard_capacity: int = 100) -> FederateResponse:
        resp = self._session.post(f"{self.base_url}/v1/federate",
                                   json={"region": region, "shard_capacity": shard_capacity},
                                   timeout=self.timeout)
        self._raise_for_status(resp)
        return FederateResponse.model_validate(resp.json())

    def list_federation(self) -> List[FederateNode]:
        resp = self._session.get(f"{self.base_url}/v1/federate", timeout=self.timeout)
        self._raise_for_status(resp)
        return [FederateNode.model_validate(n) for n in resp.json()]

    def audit_logs(self, limit: int = 100) -> List[AuditEntry]:
        resp = self._session.get(f"{self.base_url}/v1/audit/logs?limit={limit}",
                                   timeout=self.timeout)
        self._raise_for_status(resp)
        return [AuditEntry.model_validate(e) for e in resp.json()]

    def stream_explain(self, explain_id: str,
                       on_update: Callable[[ExplainResponse], None],
                       on_complete: Optional[Callable[[], None]] = None,
                       poll_interval_ms: int = 250) -> None:
        """Subscribe to explain trace updates (T-08.5: real SSE)."""
        import time
        last_confidence = -1.0
        while True:
            try:
                resp = self.explain(explain_id)
                on_update(resp)
                if resp.confidence_breakdown.aggregate >= last_confidence and \
                   resp.confidence_breakdown.aggregate > 0:
                    if on_complete:
                        on_complete()
                    return
                last_confidence = resp.confidence_breakdown.aggregate
                time.sleep(poll_interval_ms / 1000)
            except KeyboardInterrupt:
                return

    def _raise_for_status(self, resp: requests.Response) -> None:
        if resp.status_code // 100 != 2:
            raise MatrixException(resp.status_code, resp.text)


class AsyncMatrixClient:
    """Async MATRIX API client (T-08.5: full async with httpx)."""

    def __init__(self, api_key: Optional[str] = None, base_url: str = DEFAULT_BASE_URL,
                 timeout: float = 30.0):
        self.api_key = api_key or os.environ.get("MATRIX_API_KEY")
        if not self.api_key:
            raise ValueError("api_key required (or set MATRIX_API_KEY env var)")
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def analyze(self, input: str = "", content_type: str = "text") -> AnalyzeResponse:
        """Async version of analyze. T-08.5: uses httpx.AsyncClient."""
        raise NotImplementedError("Async client ships in T-08.5")
