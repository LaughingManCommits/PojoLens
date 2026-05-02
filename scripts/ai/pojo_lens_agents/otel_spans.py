from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from typing import Any, Mapping


DEFAULT_OTEL_SERVICE_NAME = "pojolens-agents"
OTEL_EXPORTER_OTLP_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT"
OTEL_SPAN_NAME_BY_KIND = {
    "run": "orchestrator.run",
    "batch": "orchestrator.batch",
    "task": "orchestrator.task",
    "validation": "orchestrator.validation",
    "approval": "orchestrator.approval",
}
OTEL_STATUS_OK = "ok"
OTEL_STATUS_ERROR = "error"
OTEL_STATUS_UNSET = "unset"

_OK_STATUSES = {"completed", "passed", "allowed", "applied", "recorded", "approved", "planned", "scheduled"}
_ERROR_STATUSES = {"failed", "blocked", "aborted", "rejected", "refused", "denied"}


def resolve_otel_endpoint(
    override: str | None = None,
    *,
    env: Mapping[str, str] | None = None,
) -> str | None:
    candidate = str(override or "").strip()
    if candidate:
        return candidate
    environment = env if env is not None else os.environ
    resolved = str(environment.get(OTEL_EXPORTER_OTLP_ENDPOINT_ENV, "")).strip()
    return resolved or None


def map_otel_status(status: str | None) -> str:
    normalized = str(status or "").strip().lower()
    if normalized in _OK_STATUSES:
        return OTEL_STATUS_OK
    if normalized in _ERROR_STATUSES:
        return OTEL_STATUS_ERROR
    return OTEL_STATUS_UNSET


def _attribute_value(value: Any) -> bool | int | float | str | list[bool] | list[int] | list[float] | list[str] | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, int) and not isinstance(value, bool):
        return value
    if isinstance(value, float):
        return value
    if isinstance(value, str):
        return value
    if isinstance(value, (list, tuple)):
        items = list(value)
        if not items:
            return []
        if all(isinstance(item, bool) for item in items):
            return [bool(item) for item in items]
        if all(isinstance(item, int) and not isinstance(item, bool) for item in items):
            return [int(item) for item in items]
        if all(isinstance(item, (int, float)) and not isinstance(item, bool) for item in items):
            return [float(item) for item in items]
        return [str(item) for item in items]
    if isinstance(value, dict):
        return json.dumps(value, sort_keys=True)
    return str(value)


def _iso_to_unix_nano(timestamp: str | None) -> int:
    text = str(timestamp or "").strip()
    if not text:
        return int(datetime.now(timezone.utc).timestamp() * 1_000_000_000)
    parsed = datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp() * 1_000_000_000)


def _ordered_spans(spans: list[dict[str, Any]]) -> list[dict[str, Any]]:
    pending = list(spans)
    ordered: list[dict[str, Any]] = []
    emitted_ids: set[str] = set()
    while pending:
        progressed = False
        for index, span in enumerate(pending):
            parent_span_ids = [str(item).strip() for item in span.get("parentSpanIds", []) or [] if str(item).strip()]
            actual_parent_id = parent_span_ids[0] if parent_span_ids else None
            if actual_parent_id and actual_parent_id not in emitted_ids:
                continue
            ordered.append(span)
            emitted_ids.add(str(span.get("id", "")).strip())
            pending.pop(index)
            progressed = True
            break
        if not progressed:
            raise RuntimeError("Unable to topologically order custom spans for OTEL emission")
    return ordered


def build_otel_span_plan(trace_payload: dict[str, Any]) -> dict[str, Any]:
    custom_spans = [span for span in trace_payload.get("spans", []) or [] if isinstance(span, dict)]
    ordered_custom_spans = _ordered_spans(custom_spans)
    otel_spans: list[dict[str, Any]] = []
    root_span_id = None
    for span in ordered_custom_spans:
        custom_id = str(span.get("id", "")).strip()
        kind = str(span.get("kind", "")).strip() or "span"
        parent_span_ids = [
            str(item).strip()
            for item in span.get("parentSpanIds", []) or []
            if str(item).strip()
        ]
        actual_parent_id = parent_span_ids[0] if parent_span_ids else None
        link_span_ids = parent_span_ids[1:] if len(parent_span_ids) > 1 else []
        raw_attributes = span.get("attributes") if isinstance(span.get("attributes"), dict) else {}
        attributes: dict[str, Any] = {
            "customSpanId": custom_id,
            "customSpanKind": kind,
            "customSpanStatus": str(span.get("status", "")).strip(),
            "runId": str(trace_payload.get("runId", "")).strip(),
            "traceFormat": str(trace_payload.get("traceFormat", "")).strip(),
        }
        if actual_parent_id:
            attributes["customParentSpanId"] = actual_parent_id
        if link_span_ids:
            attributes["linkedParentSpanIds"] = link_span_ids
        for key, value in raw_attributes.items():
            normalized = _attribute_value(value)
            if normalized is not None:
                attributes[str(key)] = normalized
        otel_spans.append(
            {
                "customId": custom_id,
                "name": OTEL_SPAN_NAME_BY_KIND.get(kind, f"orchestrator.{kind}"),
                "kind": kind,
                "status": map_otel_status(span.get("status")),
                "statusDescription": str(span.get("status", "")).strip(),
                "startTimeUnixNano": _iso_to_unix_nano(span.get("startTs")),
                "endTimeUnixNano": _iso_to_unix_nano(span.get("endTs") or span.get("startTs")),
                "actualParentSpanId": actual_parent_id,
                "linkSpanIds": link_span_ids,
                "attributes": attributes,
            }
        )
        if kind == "run" and root_span_id is None:
            root_span_id = custom_id
    return {
        "runId": str(trace_payload.get("runId", "")).strip(),
        "traceFormat": str(trace_payload.get("traceFormat", "")).strip(),
        "rootSpanId": root_span_id,
        "spanCount": len(otel_spans),
        "spans": otel_spans,
    }


def _default_otel_deps() -> dict[str, Any]:
    try:
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import SimpleSpanProcessor
        from opentelemetry.trace import Link, Status, StatusCode, set_span_in_context
    except ImportError as exc:
        raise RuntimeError(
            "OpenTelemetry emission requires the optional '[otel]' dependencies. "
            "Install them with `py -3 -m pip install -e .[otel]`."
        ) from exc
    return {
        "resource_factory": Resource.create,
        "exporter_factory": lambda endpoint: OTLPSpanExporter(endpoint=endpoint),
        "tracer_provider_factory": lambda resource: TracerProvider(resource=resource),
        "span_processor_factory": SimpleSpanProcessor,
        "link_factory": Link,
        "status_factory": Status,
        "status_code_ok": StatusCode.OK,
        "status_code_error": StatusCode.ERROR,
        "status_code_unset": StatusCode.UNSET,
        "set_span_in_context": set_span_in_context,
        "tracer_name": "pojo_lens_agents.otel_spans",
        "service_name": DEFAULT_OTEL_SERVICE_NAME,
    }


def _status_code_for(status: str, deps: dict[str, Any]) -> Any:
    if status == OTEL_STATUS_OK:
        return deps["status_code_ok"]
    if status == OTEL_STATUS_ERROR:
        return deps["status_code_error"]
    return deps["status_code_unset"]


def emit_otel_trace_from_custom_payload(
    trace_payload: dict[str, Any],
    *,
    endpoint: str,
    deps: dict[str, Any] | None = None,
) -> dict[str, Any]:
    otel_deps = dict(_default_otel_deps() if deps is None else deps)
    span_plan = build_otel_span_plan(trace_payload)
    resource = otel_deps["resource_factory"](
        {
            "service.name": otel_deps.get("service_name", DEFAULT_OTEL_SERVICE_NAME),
            "service.namespace": "pojo-lens",
            "pojolens.run.id": span_plan["runId"],
            "pojolens.trace.format": span_plan["traceFormat"],
        }
    )
    exporter = otel_deps["exporter_factory"](endpoint)
    provider = otel_deps["tracer_provider_factory"](resource)
    processor = otel_deps["span_processor_factory"](exporter)
    provider.add_span_processor(processor)
    tracer = provider.get_tracer(otel_deps.get("tracer_name", "pojo_lens_agents.otel_spans"))

    created_spans: dict[str, Any] = {}
    for span in span_plan["spans"]:
        parent_context = None
        parent_id = span.get("actualParentSpanId")
        if parent_id:
            parent_span = created_spans.get(parent_id)
            if parent_span is not None:
                parent_context = otel_deps["set_span_in_context"](parent_span)
        links = []
        for link_id in span.get("linkSpanIds", []) or []:
            linked_span = created_spans.get(link_id)
            if linked_span is not None:
                links.append(otel_deps["link_factory"](linked_span.get_span_context()))
        created = tracer.start_span(
            span["name"],
            context=parent_context,
            start_time=span["startTimeUnixNano"],
            attributes=span["attributes"],
            links=links,
        )
        created.set_status(
            otel_deps["status_factory"](
                _status_code_for(span["status"], otel_deps),
                span.get("statusDescription"),
            )
        )
        created.end(end_time=span["endTimeUnixNano"])
        created_spans[span["customId"]] = created

    force_flush = getattr(provider, "force_flush", None)
    if callable(force_flush):
        force_flush()
    shutdown = getattr(provider, "shutdown", None)
    if callable(shutdown):
        shutdown()

    root_span = created_spans.get(span_plan.get("rootSpanId") or "")
    trace_id = None
    if root_span is not None:
        trace_id = f"{root_span.get_span_context().trace_id:032x}"
    return {
        "enabled": True,
        "endpoint": endpoint,
        "emitted": True,
        "serviceName": otel_deps.get("service_name", DEFAULT_OTEL_SERVICE_NAME),
        "traceId": trace_id,
        "rootSpanId": span_plan.get("rootSpanId"),
        "spanCount": span_plan["spanCount"],
    }
