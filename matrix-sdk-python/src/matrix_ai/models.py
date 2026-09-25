"""MATRIX Python SDK — Pydantic models matching the OpenAPI 3.0 spec."""
from __future__ import annotations
from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field


class Plan(str, Enum):
    FREE = "FREE"
    PRO = "PRO"
    ENTERPRISE = "ENTERPRISE"

    @property
    def max_requests_per_hour(self) -> int:
        return {"FREE": 100, "PRO": 1000, "ENTERPRISE": 2**31 - 1}[self.value]


class ContentType(str, Enum):
    TEXT = "text"
    AUDIO = "audio"
    IMAGE = "image"


class AnalyzeRequest(BaseModel):
    input: str = Field(..., min_length=1, max_length=1_048_576)
    content_type: ContentType = ContentType.TEXT
    context: Optional[str] = Field(None, max_length=65_536)
    model: str = "default"

    @classmethod
    def text(cls, text: str) -> "AnalyzeRequest":
        return cls(input=text, content_type=ContentType.TEXT)

    @classmethod
    def audio(cls, base64_audio: str) -> "AnalyzeRequest":
        return cls(input=base64_audio, content_type=ContentType.AUDIO)

    @classmethod
    def image(cls, base64_image: str) -> "AnalyzeRequest":
        return cls(input=base64_image, content_type=ContentType.IMAGE)


class AnalyzeResponse(BaseModel):
    reply: str
    confidence: float = Field(..., ge=0, le=1)
    duration_ms: int
    accepted: bool
    explain_id: str
    modulators_fired: Optional[List[str]] = None


class ExplainStep(BaseModel):
    stage: str
    action: str
    duration_ms: int


class ModulatorState(BaseModel):
    ethical_filter: float
    safety_monitor: float
    consistency_checker: float
    lie_detector: float


class ConfidenceBreakdown(BaseModel):
    bir_confidence: float
    hdc_confidence: float
    mcts_confidence: float
    aggregate: float


class ExplainResponse(BaseModel):
    explain_id: str
    steps: List[ExplainStep]
    modulator_snapshot: ModulatorState
    hdc_memory_hits: List[str]
    confidence_breakdown: ConfidenceBreakdown


class FederateRequest(BaseModel):
    region: str
    shard_capacity: Optional[int] = 100


class FederateResponse(BaseModel):
    node_id: str
    region: str
    shard_capacity: int
    joined_at: str
    total_nodes: int


class FederateNode(BaseModel):
    nodeId: str
    region: str
    shardCapacity: int
    joinedAt: str


class AuditEntry(BaseModel):
    timestamp: str
    user_id: str
    action: str
    target: str
    status_code: int
