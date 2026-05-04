"""Tests for DashboardWidget run pagination and aggregate stats box."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def _widget():
    from ai.pojo_lens_agents._tui_dashboard import DashboardWidget
    w = DashboardWidget()
    return w


def _patch_query(w, *, nav=None, prev=None, nxt=None, stats_box=None, extra=None):
    nav_w       = nav       or MagicMock(); nav_w.update = MagicMock()
    prev_w      = prev      or MagicMock(); prev_w.disabled = True
    nxt_w       = nxt       or MagicMock(); nxt_w.disabled  = True
    stats_box_w = stats_box or MagicMock(); stats_box_w.update = MagicMock()

    def _q(sel, *_args):
        if "#dash-run-nav"  in str(sel): return nav_w
        if "#btn-run-prev"  in str(sel): return prev_w
        if "#btn-run-next"  in str(sel): return nxt_w
        if "#dash-stats-box" in str(sel): return stats_box_w
        if extra:
            m = extra.get(str(sel))
            if m is not None:
                return m
        # return a silent mock for anything else
        return MagicMock()

    w.query_one = _q
    return nav_w, prev_w, nxt_w, stats_box_w


def _write_manifest(path: Path, *, run_id: str = "run-x",
                    finished: bool = False, cost: float = 0.0) -> None:
    events = [{"phase": "run-start", "ts": "2026-01-01T00:00:00Z"}]
    if finished:
        events.append({"phase": "run-finished", "ts": "2026-01-01T00:01:00Z"})
    path.mkdir(parents=True, exist_ok=True)
    (path / "manifest.json").write_text(
        json.dumps({
            "runId": run_id,
            "events": events,
            "usageTotals": {"totalCostUsd": cost},
        }),
        encoding="utf-8",
    )


# ── _manifest_run_state helper ────────────────────────────────────────────────

class TestManifestRunState(unittest.TestCase):

    def test_completed_when_run_finished(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_run_state
        data = {"events": [{"phase": "run-start"}, {"phase": "run-finished"}]}
        self.assertEqual(_manifest_run_state(data), "completed")

    def test_running_when_only_run_start(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_run_state
        data = {"events": [{"phase": "run-start"}]}
        self.assertEqual(_manifest_run_state(data), "running")

    def test_unknown_when_no_events(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_run_state
        data = {"events": []}
        self.assertEqual(_manifest_run_state(data), "unknown")


# ── _manifest_cost helper ─────────────────────────────────────────────────────

class TestManifestCost(unittest.TestCase):

    def test_reads_usage_totals(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_cost
        data = {"usageTotals": {"totalCostUsd": 1.5}}
        self.assertAlmostEqual(_manifest_cost(data), 1.5)

    def test_falls_back_to_highest_cost_tasks(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_cost
        data = {"runGovernance": {"highestCostTasks": [{"costUsd": 0.4}, {"costUsd": 0.6}]}}
        self.assertAlmostEqual(_manifest_cost(data), 1.0)

    def test_returns_zero_when_no_cost(self):
        from ai.pojo_lens_agents._tui_dashboard import _manifest_cost
        self.assertEqual(_manifest_cost({}), 0.0)


# ── _navigate_run ─────────────────────────────────────────────────────────────

class TestNavigateRun(unittest.TestCase):

    def test_navigate_to_older_run(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", run_id="r1", finished=True)
            _write_manifest(root / "run-2", run_id="r2", finished=True)
            manifests = sorted(
                root.glob("*/manifest.json"),
                key=lambda p: p.stat().st_mtime,
                reverse=True,
            )
            w = _widget()
            w._manifests  = manifests
            w._run_index  = 0
            _patch_query(w)
            w._update_display = MagicMock()
            w._navigate_run(+1)
            self.assertEqual(int(w._run_index), 1)

    def test_navigate_clamped_at_oldest(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", run_id="r1", finished=True)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            w._manifests = manifests
            w._run_index = 0
            _patch_query(w)
            w._update_display = MagicMock()
            w._navigate_run(+1)          # already at oldest (only 1 run)
            self.assertEqual(int(w._run_index), 0)

    def test_navigate_newer_decrements_index(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", run_id="r1", finished=True)
            _write_manifest(root / "run-2", run_id="r2", finished=True)
            manifests = sorted(
                root.glob("*/manifest.json"),
                key=lambda p: p.stat().st_mtime,
                reverse=True,
            )
            w = _widget()
            w._manifests = manifests
            w._run_index = 1
            _patch_query(w)
            w._update_display = MagicMock()
            w._navigate_run(-1)
            self.assertEqual(int(w._run_index), 0)

    def test_navigate_clamped_at_newest(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", run_id="r1", finished=True)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            w._manifests = manifests
            w._run_index = 0
            _patch_query(w)
            w._update_display = MagicMock()
            w._navigate_run(-1)
            self.assertEqual(int(w._run_index), 0)


# ── _update_nav_bar ───────────────────────────────────────────────────────────

class TestUpdateNavBar(unittest.TestCase):

    def _manifests(self, n: int):
        return [MagicMock() for _ in range(n)]

    def test_first_run_shows_latest_tag(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        w._update_nav_bar(0, self._manifests(3))
        call_arg = nav.update.call_args[0][0]
        self.assertIn("latest", call_arg)

    def test_non_first_run_no_latest_tag(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        w._update_nav_bar(1, self._manifests(3))
        call_arg = nav.update.call_args[0][0]
        self.assertNotIn("latest", call_arg)

    def test_prev_disabled_at_last(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        prev.disabled = False
        w._update_nav_bar(2, self._manifests(3))   # idx=2 = oldest of 3
        self.assertTrue(prev.disabled)

    def test_next_disabled_at_first(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        nxt.disabled = False
        w._update_nav_bar(0, self._manifests(3))   # idx=0 = newest
        self.assertTrue(nxt.disabled)

    def test_prev_enabled_when_not_last(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        prev.disabled = True
        w._update_nav_bar(0, self._manifests(3))   # still has older runs
        self.assertFalse(prev.disabled)

    def test_run_count_shown(self):
        w = _widget()
        nav, prev, nxt, _ = _patch_query(w)
        w._update_nav_bar(2, self._manifests(5))
        call_arg = nav.update.call_args[0][0]
        self.assertIn("5", call_arg)


# ── _update_stats_box ─────────────────────────────────────────────────────────

class TestUpdateStatsBox(unittest.TestCase):

    def test_stats_show_total_run_count(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for i in range(4):
                _write_manifest(root / f"run-{i}", run_id=f"r{i}", finished=True, cost=0.5)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            _, _, _, stats_w = _patch_query(w)
            w._update_stats_box(manifests)
            call_arg = stats_w.update.call_args[0][0]
            self.assertIn("4", call_arg)

    def test_stats_show_total_cost(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for i, c in enumerate([1.0, 2.0, 0.5]):
                _write_manifest(root / f"run-{i}", finished=True, cost=c)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            _, _, _, stats_w = _patch_query(w)
            w._update_stats_box(manifests)
            call_arg = stats_w.update.call_args[0][0]
            self.assertIn("3.5", call_arg)

    def test_stats_show_completed_count(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", finished=True)
            _write_manifest(root / "run-2", finished=True)
            _write_manifest(root / "run-3", finished=False)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            _, _, _, stats_w = _patch_query(w)
            w._update_stats_box(manifests)
            call_arg = stats_w.update.call_args[0][0]
            self.assertIn("✓2", call_arg)

    def test_stats_dash_when_no_cost(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _write_manifest(root / "run-1", finished=True, cost=0.0)
            manifests = list(root.glob("*/manifest.json"))
            w = _widget()
            _, _, _, stats_w = _patch_query(w)
            w._update_stats_box(manifests)
            call_arg = stats_w.update.call_args[0][0]
            self.assertIn("—", call_arg)


# ── compose has nav widgets ───────────────────────────────────────────────────

class TestDashboardCompose(unittest.TestCase):

    def _src(self):
        return (
            Path(__file__).resolve().parents[1]
            / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        ).read_text(encoding="utf-8")

    def test_stats_box_in_compose(self):
        self.assertIn("dash-stats-box", self._src())

    def test_nav_bar_in_compose(self):
        self.assertIn("dash-nav", self._src())

    def test_btn_run_prev_in_compose(self):
        self.assertIn("btn-run-prev", self._src())

    def test_btn_run_next_in_compose(self):
        self.assertIn("btn-run-next", self._src())

    def test_dash_run_nav_in_compose(self):
        self.assertIn("dash-run-nav", self._src())
