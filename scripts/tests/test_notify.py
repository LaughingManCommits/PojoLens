from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai.pojo_lens_agents.notify import (
    build_notification_payload,
    dispatch_notifications,
    notify_desktop,
    notify_slack,
    notify_webhook,
)

_COMPLETED_PAYLOAD = {
    "runId": "run-abc12345",
    "plan": "my-plan",
    "statusCounts": {"completed": 3, "failed": 0, "blocked": 0},
    "usageTotals": {"totalCostUsd": 0.0125},
}

_FAILED_PAYLOAD = {
    "runId": "run-fail1234",
    "plan": "my-plan",
    "statusCounts": {"completed": 1, "failed": 2, "blocked": 0},
    "usageTotals": {"totalCostUsd": 0.005},
}

_BLOCKED_PAYLOAD = {
    "runId": "run-blk00001",
    "statusCounts": {"completed": 2, "blocked": 1},
    "usageTotals": {},
}


class _FakeResponse:
    def __init__(self, captured: dict, body: bytes) -> None:
        self._captured = captured
        self._body = body

    def __enter__(self) -> "_FakeResponse":
        return self

    def __exit__(self, *_: object) -> None:
        pass

    def read(self) -> bytes:
        return b""


class TestBuildNotificationPayload(unittest.TestCase):
    def test_completed_status(self) -> None:
        result = build_notification_payload(_COMPLETED_PAYLOAD)
        self.assertEqual(result["status"], "completed")
        self.assertEqual(result["runId"], "run-abc12345")
        self.assertIn("3 completed", result["summary"])

    def test_failed_status(self) -> None:
        result = build_notification_payload(_FAILED_PAYLOAD)
        self.assertEqual(result["status"], "failed")
        self.assertIn("2 failed", result["summary"])
        self.assertIn("1 completed", result["summary"])

    def test_blocked_status(self) -> None:
        result = build_notification_payload(_BLOCKED_PAYLOAD)
        self.assertEqual(result["status"], "blocked")
        self.assertIn("1 blocked", result["summary"])

    def test_failed_takes_precedence_over_blocked(self) -> None:
        payload = {
            "runId": "x",
            "statusCounts": {"completed": 0, "failed": 1, "blocked": 1},
        }
        result = build_notification_payload(payload)
        self.assertEqual(result["status"], "failed")

    def test_includes_cost(self) -> None:
        result = build_notification_payload(_COMPLETED_PAYLOAD)
        self.assertAlmostEqual(result["totalCostUsd"], 0.0125)

    def test_missing_usage_omits_cost(self) -> None:
        payload = {"runId": "x", "statusCounts": {"completed": 1}}
        result = build_notification_payload(payload)
        self.assertNotIn("totalCostUsd", result)

    def test_task_counts_present(self) -> None:
        result = build_notification_payload(_FAILED_PAYLOAD)
        self.assertEqual(result["taskCounts"]["completed"], 1)
        self.assertEqual(result["taskCounts"]["failed"], 2)
        self.assertEqual(result["taskCounts"]["blocked"], 0)

    def test_empty_payload_defaults(self) -> None:
        result = build_notification_payload({})
        self.assertEqual(result["status"], "completed")
        self.assertEqual(result["runId"], "")
        self.assertIn("0 completed", result["summary"])


class TestNotifyDesktop(unittest.TestCase):
    def test_calls_injected_notify_fn(self) -> None:
        calls: list[dict] = []
        notify_desktop(
            {"status": "completed", "summary": "3 completed", "runId": "abc"},
            _notify_fn=lambda **kw: calls.append(kw),
        )
        self.assertEqual(len(calls), 1)
        self.assertIn("Completed", calls[0]["title"])
        self.assertIn("3 completed", calls[0]["message"])

    def test_graceful_when_plyer_missing(self) -> None:
        notify_desktop({"status": "completed", "summary": "ok"})


class TestNotifyWebhook(unittest.TestCase):
    def _make_fake_urlopen(self, captured: dict):  # type: ignore[return]
        def fake_urlopen(req, timeout=None):  # type: ignore[return]
            captured["url"] = req.full_url
            captured["body"] = json.loads(req.data)
            captured["method"] = req.method
            return _FakeResponse(captured, req.data)
        return fake_urlopen

    def test_posts_json(self) -> None:
        captured: dict = {}
        errors = notify_webhook(
            "https://example.com/hook",
            {"runId": "x", "status": "completed"},
            _urlopen_fn=self._make_fake_urlopen(captured),
        )
        self.assertEqual(errors, [])
        self.assertEqual(captured["url"], "https://example.com/hook")
        self.assertEqual(captured["body"]["runId"], "x")
        self.assertEqual(captured["method"], "POST")

    def test_returns_error_on_failure(self) -> None:
        def fail(req, timeout=None):  # type: ignore[return]
            raise ConnectionError("refused")

        errors = notify_webhook("https://bad.example.com", {}, _urlopen_fn=fail)
        self.assertEqual(len(errors), 1)
        self.assertIn("webhook", errors[0])

    def test_content_type_header(self) -> None:
        captured: dict = {}

        def fake_urlopen(req, timeout=None):  # type: ignore[return]
            captured["content_type"] = req.get_header("Content-type")
            return _FakeResponse(captured, req.data)

        notify_webhook("https://example.com", {}, _urlopen_fn=fake_urlopen)
        self.assertEqual(captured["content_type"], "application/json")


class TestNotifySlack(unittest.TestCase):
    def _make_fake_urlopen(self, captured: dict):  # type: ignore[return]
        def fake_urlopen(req, timeout=None):  # type: ignore[return]
            captured["body"] = json.loads(req.data)
            return _FakeResponse(captured, req.data)
        return fake_urlopen

    def test_posts_blocks(self) -> None:
        captured: dict = {}
        errors = notify_slack(
            "https://hooks.slack.com/test",
            {"status": "completed", "summary": "3 completed", "runId": "abc12345"},
            _urlopen_fn=self._make_fake_urlopen(captured),
        )
        self.assertEqual(errors, [])
        body = captured["body"]
        self.assertIn("blocks", body)
        header_block = next((b for b in body["blocks"] if b["type"] == "header"), None)
        self.assertIsNotNone(header_block)

    def test_failed_uses_red_color(self) -> None:
        captured: dict = {}
        notify_slack(
            "https://hooks.slack.com/test",
            {"status": "failed", "summary": "1 failed"},
            _urlopen_fn=self._make_fake_urlopen(captured),
        )
        attachments = captured["body"].get("attachments", [])
        self.assertTrue(any("#e01e5a" in str(a) for a in attachments))

    def test_completed_uses_green_color(self) -> None:
        captured: dict = {}
        notify_slack(
            "https://hooks.slack.com/test",
            {"status": "completed", "summary": "3 completed"},
            _urlopen_fn=self._make_fake_urlopen(captured),
        )
        attachments = captured["body"].get("attachments", [])
        self.assertTrue(any("#2eb67d" in str(a) for a in attachments))

    def test_includes_cost_field_when_present(self) -> None:
        captured: dict = {}
        notify_slack(
            "https://hooks.slack.com/test",
            {"status": "completed", "summary": "ok", "totalCostUsd": 0.025},
            _urlopen_fn=self._make_fake_urlopen(captured),
        )
        body_str = json.dumps(captured["body"])
        self.assertIn("Cost", body_str)

    def test_returns_error_on_failure(self) -> None:
        def fail(req, timeout=None):  # type: ignore[return]
            raise ConnectionError("nope")

        errors = notify_slack("https://bad.slack.com", {"status": "failed"}, _urlopen_fn=fail)
        self.assertEqual(len(errors), 1)
        self.assertIn("slack", errors[0])


class TestDispatchNotifications(unittest.TestCase):
    def test_desktop_dispatched_from_config(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"desktop": True},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0]["status"], "completed")

    def test_webhook_dispatched(self) -> None:
        calls: list[str] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"webhook_url": "https://example.com/hook"},
            _webhook_fn=lambda url, p: calls.append(url) or [],
        )
        self.assertEqual(len(calls), 1)
        self.assertIn("example.com", calls[0])

    def test_slack_dispatched(self) -> None:
        calls: list[str] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"slack_webhook_url": "https://hooks.slack.com/x"},
            _slack_fn=lambda url, p: calls.append(url) or [],
        )
        self.assertEqual(len(calls), 1)

    def test_force_desktop_overrides_config(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {},
            force_desktop=True,
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 1)

    def test_empty_config_no_dispatches(self) -> None:
        calls: list[object] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {},
            _desktop_fn=lambda p: calls.append(p),
            _webhook_fn=lambda url, p: calls.append(url) or [],
            _slack_fn=lambda url, p: calls.append(url) or [],
        )
        self.assertEqual(len(calls), 0)

    def test_notify_on_success_dispatches_completed(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"desktop": True, "notify_on": ["success"]},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 1)

    def test_notify_on_success_skips_failed(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _FAILED_PAYLOAD,
            {"desktop": True, "notify_on": ["success"]},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 0)

    def test_notify_on_failure_dispatches_failed(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _FAILED_PAYLOAD,
            {"desktop": True, "notify_on": ["failure"]},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 1)

    def test_notify_on_failure_skips_completed(self) -> None:
        calls: list[dict] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"desktop": True, "notify_on": ["failure"]},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 0)

    def test_notify_on_always_dispatches_both(self) -> None:
        for payload in (_COMPLETED_PAYLOAD, _FAILED_PAYLOAD):
            calls: list[dict] = []
            dispatch_notifications(
                payload,
                {"desktop": True, "notify_on": ["always"]},
                _desktop_fn=lambda p: calls.append(p),
            )
            self.assertEqual(len(calls), 1, f"Expected dispatch for {payload['runId']}")

    def test_errors_collected_from_webhook(self) -> None:
        errors = dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"webhook_url": "https://bad.example.com"},
            _webhook_fn=lambda url, p: ["webhook: error"],
        )
        self.assertEqual(len(errors), 1)
        self.assertIn("webhook", errors[0])

    def test_multiple_channels_dispatched(self) -> None:
        calls: list[str] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {
                "desktop": True,
                "webhook_url": "https://example.com/hook",
                "slack_webhook_url": "https://hooks.slack.com/x",
            },
            _desktop_fn=lambda p: calls.append("desktop"),
            _webhook_fn=lambda url, p: calls.append("webhook") or [],
            _slack_fn=lambda url, p: calls.append("slack") or [],
        )
        self.assertIn("desktop", calls)
        self.assertIn("webhook", calls)
        self.assertIn("slack", calls)

    def test_no_notify_skips_all_when_no_channels(self) -> None:
        calls: list[object] = []
        dispatch_notifications(
            _COMPLETED_PAYLOAD,
            {"notify_on": ["success"]},
            _desktop_fn=lambda p: calls.append(p),
        )
        self.assertEqual(len(calls), 0)


class TestLoadNotificationsConfig(unittest.TestCase):
    def setUp(self) -> None:
        import tempfile
        self._td = tempfile.TemporaryDirectory()
        self.tmp = Path(self._td.name)

    def tearDown(self) -> None:
        self._td.cleanup()

    def _write_toml(self, content: str) -> Path:
        p = self.tmp / "pojolens-agents.toml"
        p.write_text(content, encoding="utf-8")
        return p

    def test_no_file_returns_empty(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        result = load_notifications_config(root=self.tmp, env={})
        self.assertEqual(result, {})

    def test_missing_section_returns_empty(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        self._write_toml("[defaults]\nmax_parallel = 2\n")
        result = load_notifications_config(root=self.tmp, env={})
        self.assertEqual(result, {})

    def test_loads_all_fields(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        self._write_toml(
            '[notifications]\n'
            'desktop = true\n'
            'webhook_url = "https://example.com/hook"\n'
            'slack_webhook_url = "https://hooks.slack.com/x"\n'
            'notify_on = ["success", "failure"]\n'
        )
        result = load_notifications_config(root=self.tmp, env={})
        self.assertIs(result["desktop"], True)
        self.assertEqual(result["webhook_url"], "https://example.com/hook")
        self.assertEqual(result["slack_webhook_url"], "https://hooks.slack.com/x")
        self.assertEqual(result["notify_on"], ["success", "failure"])

    def test_unknown_key_raises(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        self._write_toml("[notifications]\nbad_key = true\n")
        with self.assertRaises(ValueError) as ctx:
            load_notifications_config(root=self.tmp, env={})
        self.assertIn("bad_key", str(ctx.exception))

    def test_invalid_notify_on_value_raises(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        self._write_toml('[notifications]\nnotify_on = ["never"]\n')
        with self.assertRaises(ValueError) as ctx:
            load_notifications_config(root=self.tmp, env={})
        self.assertIn("never", str(ctx.exception))

    def test_wrong_type_desktop_raises(self) -> None:
        from ai.pojo_lens_agents.config_loader import load_notifications_config
        self._write_toml('[notifications]\ndesktop = "yes"\n')
        with self.assertRaises(ValueError) as ctx:
            load_notifications_config(root=self.tmp, env={})
        self.assertIn("desktop", str(ctx.exception))


class TestNotifyCliFlags(unittest.TestCase):
    def _parse(self, cmd: list[str]):  # type: ignore[return]
        from unittest.mock import patch
        with patch("pojo_lens_agents.config_loader.load_config", return_value={}):
            from ai.pojo_lens_agents.cli_parser import parse_args
            return parse_args(cmd)

    def test_run_notify_flag(self) -> None:
        args = self._parse(["run", "plan.json", "--notify"])
        self.assertTrue(args.notify)
        self.assertFalse(args.no_notify)

    def test_run_no_notify_flag(self) -> None:
        args = self._parse(["run", "plan.json", "--no-notify"])
        self.assertFalse(args.notify)
        self.assertTrue(args.no_notify)

    def test_resume_notify_flag(self) -> None:
        args = self._parse(["resume", "run-dir/", "--notify"])
        self.assertTrue(args.notify)

    def test_retry_notify_flag(self) -> None:
        args = self._parse(["retry", "run-dir/", "--notify"])
        self.assertTrue(args.notify)

    def test_run_no_flag_defaults_false(self) -> None:
        args = self._parse(["run", "plan.json"])
        self.assertFalse(args.notify)
        self.assertFalse(args.no_notify)


if __name__ == "__main__":
    unittest.main()
