import unittest

from pojo_lens_agents import otel_spans


class FakeSpanContext:
    def __init__(self, trace_id: int) -> None:
        self.trace_id = trace_id


class FakeSpan:
    def __init__(self, name, *, trace_id, parent=None, context=None, start_time=None, attributes=None, links=None):
        self.name = name
        self.trace_id = trace_id
        self.parent = parent
        self.context = context
        self.start_time = start_time
        self.attributes = dict(attributes or {})
        self.links = list(links or [])
        self.ended_at = None
        self.status = None

    def set_status(self, status) -> None:
        self.status = status

    def end(self, *, end_time=None) -> None:
        self.ended_at = end_time

    def get_span_context(self):
        return FakeSpanContext(self.trace_id)


class FakeTracer:
    def __init__(self) -> None:
        self.started = []
        self._next_trace_id = 0x100

    def start_span(self, name, *, context=None, start_time=None, attributes=None, links=None):
        parent = context.get("span") if isinstance(context, dict) else None
        trace_id = parent.trace_id if parent is not None else self._next_trace_id
        if parent is None:
            self._next_trace_id += 1
        span = FakeSpan(
            name,
            trace_id=trace_id,
            parent=parent,
            context=context,
            start_time=start_time,
            attributes=attributes,
            links=links,
        )
        self.started.append(span)
        return span


class FakeProvider:
    def __init__(self, resource) -> None:
        self.resource = resource
        self.processor = None
        self.tracer = FakeTracer()
        self.flushed = False
        self.shut_down = False

    def add_span_processor(self, processor) -> None:
        self.processor = processor

    def get_tracer(self, _name):
        return self.tracer

    def force_flush(self) -> None:
        self.flushed = True

    def shutdown(self) -> None:
        self.shut_down = True


class OtelSpansTest(unittest.TestCase):
    def test_resolve_otel_endpoint_prefers_override(self):
        resolved = otel_spans.resolve_otel_endpoint(
            "http://override:4318/v1/traces",
            env={otel_spans.OTEL_EXPORTER_OTLP_ENDPOINT_ENV: "http://env:4318/v1/traces"},
        )

        self.assertEqual("http://override:4318/v1/traces", resolved)

    def test_build_otel_span_plan_maps_names_status_links_and_usage_attributes(self):
        plan = otel_spans.build_otel_span_plan(
            {
                "traceFormat": "pojo-lens-orchestrator-trace/v1",
                "runId": "run-123",
                "spans": [
                    {
                        "id": "run:run-123",
                        "kind": "run",
                        "status": "completed",
                        "startTs": "2026-05-02T10:00:00+00:00",
                        "endTs": "2026-05-02T10:00:05+00:00",
                        "parentSpanIds": [],
                        "attributes": {"goal": "Observe a run."},
                    },
                    {
                        "id": "batch:run-123:1",
                        "kind": "batch",
                        "status": "completed",
                        "startTs": "2026-05-02T10:00:01+00:00",
                        "endTs": "2026-05-02T10:00:04+00:00",
                        "parentSpanIds": ["run:run-123"],
                        "attributes": {"batchIndex": 1},
                    },
                    {
                        "id": "task:run-123:inspect-a",
                        "kind": "task",
                        "status": "failed",
                        "startTs": "2026-05-02T10:00:02+00:00",
                        "endTs": "2026-05-02T10:00:03+00:00",
                        "parentSpanIds": ["batch:run-123:1", "run:run-123"],
                        "attributes": {
                            "model": "claude-haiku-4-5",
                            "modelProfile": "simple",
                            "effort": "low",
                            "outputProfile": "lean",
                            "usage.totalCostUsd": 0.12,
                            "usage.inputTokens": 11,
                            "usage.outputTokens": 7,
                            "usage.cacheReadTokens": 3,
                        },
                    },
                ],
            }
        )

        self.assertEqual("run:run-123", plan["rootSpanId"])
        task_span = next(span for span in plan["spans"] if span["customId"] == "task:run-123:inspect-a")
        self.assertEqual("orchestrator.task", task_span["name"])
        self.assertEqual(otel_spans.OTEL_STATUS_ERROR, task_span["status"])
        self.assertEqual("batch:run-123:1", task_span["actualParentSpanId"])
        self.assertEqual(["run:run-123"], task_span["linkSpanIds"])
        self.assertEqual("claude-haiku-4-5", task_span["attributes"]["model"])
        self.assertEqual("lean", task_span["attributes"]["outputProfile"])
        self.assertEqual(0.12, task_span["attributes"]["usage.totalCostUsd"])
        self.assertEqual(3, task_span["attributes"]["usage.cacheReadTokens"])

    def test_emit_otel_trace_from_custom_payload_uses_parent_and_link_structure(self):
        captured = {}

        def exporter_factory(endpoint):
            captured["endpoint"] = endpoint
            return {"endpoint": endpoint}

        def tracer_provider_factory(resource):
            provider = FakeProvider(resource)
            captured["provider"] = provider
            return provider

        result = otel_spans.emit_otel_trace_from_custom_payload(
            {
                "traceFormat": "pojo-lens-orchestrator-trace/v1",
                "runId": "run-456",
                "spans": [
                    {
                        "id": "run:run-456",
                        "kind": "run",
                        "status": "completed",
                        "startTs": "2026-05-02T10:00:00+00:00",
                        "endTs": "2026-05-02T10:00:05+00:00",
                        "parentSpanIds": [],
                        "attributes": {"goal": "Emit OTEL."},
                    },
                    {
                        "id": "batch:run-456:1",
                        "kind": "batch",
                        "status": "completed",
                        "startTs": "2026-05-02T10:00:01+00:00",
                        "endTs": "2026-05-02T10:00:04+00:00",
                        "parentSpanIds": ["run:run-456"],
                        "attributes": {"batchIndex": 1},
                    },
                    {
                        "id": "task:run-456:inspect-a",
                        "kind": "task",
                        "status": "blocked",
                        "startTs": "2026-05-02T10:00:02+00:00",
                        "endTs": "2026-05-02T10:00:03+00:00",
                        "parentSpanIds": ["batch:run-456:1", "run:run-456"],
                        "attributes": {"model": "claude-sonnet-4-6"},
                    },
                ],
            },
            endpoint="http://collector:4318/v1/traces",
            deps={
                "resource_factory": lambda attrs: attrs,
                "exporter_factory": exporter_factory,
                "tracer_provider_factory": tracer_provider_factory,
                "span_processor_factory": lambda exporter: {"exporter": exporter},
                "link_factory": lambda context: {"trace_id": context.trace_id},
                "status_factory": lambda code, description: {"code": code, "description": description},
                "status_code_ok": "OK",
                "status_code_error": "ERROR",
                "status_code_unset": "UNSET",
                "set_span_in_context": lambda span: {"span": span},
                "tracer_name": "test.otel",
                "service_name": "test-service",
            },
        )

        provider = captured["provider"]
        self.assertEqual("http://collector:4318/v1/traces", captured["endpoint"])
        self.assertEqual(3, len(provider.tracer.started))
        task_span = provider.tracer.started[2]
        self.assertEqual("orchestrator.task", task_span.name)
        self.assertEqual("claude-sonnet-4-6", task_span.attributes["model"])
        self.assertEqual("ERROR", task_span.status["code"])
        self.assertEqual("batch:run-456:1", task_span.attributes["customParentSpanId"])
        self.assertEqual([{"trace_id": 0x100}], task_span.links)
        self.assertTrue(provider.flushed)
        self.assertTrue(provider.shut_down)
        self.assertEqual("test-service", result["serviceName"])
        self.assertEqual(3, result["spanCount"])
        self.assertEqual(f"{0x100:032x}", result["traceId"])


if __name__ == "__main__":
    unittest.main()
