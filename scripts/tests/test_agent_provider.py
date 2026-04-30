from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AI_SCRIPT_DIR = ROOT / "scripts" / "ai"
if str(AI_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SCRIPT_DIR))

from pojo_lens_agents import provider


class ProviderAdapterTest(unittest.TestCase):
    def test_run_subprocess_captures_stdout_and_progress(self):
        frames: list[int] = []

        completed = provider.run_subprocess(
            [sys.executable, "-c", "print('ok')"],
            cwd=ROOT,
            timeout_sec=30,
            timeout_error="timed out",
            on_progress_frame=frames.append,
            progress_interval_sec=0.01,
        )

        self.assertEqual(0, completed.returncode)
        self.assertEqual("ok", completed.stdout.strip())
        self.assertEqual(0, frames[0])

    def test_run_process_timeout_uses_error_factory(self):
        with self.assertRaisesRegex(RuntimeError, "wrapped timeout"):
            provider.run_process(
                [sys.executable, "-c", "import time; time.sleep(5)"],
                cwd=ROOT,
                timeout_sec=0,
                shell=False,
                timeout_error="timeout",
                progress_interval_sec=0.01,
                error_factory=lambda message: RuntimeError(f"wrapped {message}"),
            )

    def test_extract_json_payload_accepts_wrapped_provider_json(self):
        self.assertEqual(
            {"result": {"status": "completed"}},
            provider.extract_json_payload('noise\n{"result":{"status":"completed"}}'),
        )

    def test_extract_usage_preserves_provider_usage_contract(self):
        usage = provider.extract_usage(
            {
                "usage": {
                    "input_tokens": 10,
                    "output_tokens": 4,
                    "cache_read_input_tokens": 2,
                    "cache_creation_input_tokens": 1,
                    "service_tier": "standard",
                },
                "duration_ms": 100,
                "duration_api_ms": 80,
                "num_turns": 1,
                "stop_reason": "end_turn",
                "is_error": False,
                "total_cost_usd": 0.02,
                "modelUsage": {"claude": {"calls": 1}},
            }
        )

        self.assertEqual(10, usage["inputTokens"])
        self.assertEqual(4, usage["outputTokens"])
        self.assertEqual(0.02, usage["totalCostUsd"])
        self.assertEqual({"claude": {"calls": 1}}, usage["modelUsage"])


if __name__ == "__main__":
    unittest.main()
