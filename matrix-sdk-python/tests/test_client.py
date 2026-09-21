"""Tests for the MATRIX Python SDK — uses pytest + responses for mocking."""
import pytest
import responses
from matrix_ai import MatrixClient, MatrixException
from matrix_ai.models import AnalyzeRequest, AnalyzeResponse, ExplainResponse


@responses.activate
def test_analyze():
    responses.add(
        responses.POST,
        "https://api.matrix.ai/v1/analyze",
        json={
            "reply": "Paris is the capital of France.",
            "confidence": 0.95,
            "duration_ms": 23,
            "accepted": True,
            "explain_id": "expl_abc",
        },
        status=200,
    )

    with MatrixClient(api_key="test-key") as client:
        resp = client.analyze(input="What is the capital of France?")
        assert resp.reply == "Paris is the capital of France."
        assert resp.confidence == 0.95
        assert resp.accepted


@responses.activate
def test_explain():
    responses.add(
        responses.GET,
        "https://api.matrix.ai/v1/explain/expl_abc",
        json={
            "explain_id": "expl_abc",
            "steps": [
                {"stage": "INPUT_NORMALIZED", "action": "norm", "duration_ms": 1},
            ],
            "modulator_snapshot": {
                "ethical_filter": 0.95,
                "safety_monitor": 0.92,
                "consistency_checker": 0.88,
                "lie_detector": 0.91,
            },
            "hdc_memory_hits": ["kb-doc-42"],
            "confidence_breakdown": {
                "bir_confidence": 0.85,
                "hdc_confidence": 0.78,
                "mcts_confidence": 0.72,
                "aggregate": 0.95,
            },
        },
        status=200,
    )

    with MatrixClient(api_key="test-key") as client:
        resp = client.explain("expl_abc")
        assert resp.explain_id == "expl_abc"
        assert resp.modulator_snapshot.ethical_filter == 0.95


@responses.activate
def test_error_handling():
    responses.add(
        responses.POST,
        "https://api.matrix.ai/v1/analyze",
        json={"error": "Rate limit exceeded"},
        status=429,
    )

    with MatrixClient(api_key="test-key") as client:
        with pytest.raises(MatrixException) as exc_info:
            client.analyze(input="hi")
        assert exc_info.value.status_code == 429


def test_requires_api_key():
    with pytest.raises(ValueError):
        MatrixClient(api_key="")


def test_analyze_request_text_factory():
    req = AnalyzeRequest.text("hello")
    assert req.input == "hello"
    assert req.content_type.value == "text"


def test_analyze_request_audio_factory():
    req = AnalyzeRequest.audio("base64data")
    assert req.content_type.value == "audio"


def test_analyze_request_image_factory():
    req = AnalyzeRequest.image("base64img")
    assert req.content_type.value == "image"


def test_plan_rates():
    from matrix_ai.models import Plan
    assert Plan.FREE.max_requests_per_hour == 100
    assert Plan.PRO.max_requests_per_hour == 1000
    assert Plan.ENTERPRISE.max_requests_per_hour == 2**31 - 1
