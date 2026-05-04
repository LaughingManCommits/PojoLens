"""Tests for SavedPlansScreen pagination and stats."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def _screen() -> "SavedPlansScreen":
    from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
    s = SavedPlansScreen()
    return s


def _make_preview(name: str, goal: str = "", task_count: int = 3, path: str = "") -> MagicMock:
    p = MagicMock()
    p.name = name
    p.goal = goal
    p.task_count = task_count
    p.path = path or f"/fake/{name}.json"
    return p


def _mock_widgets(s, *, stats=None, notice=None, page_label=None,
                  btn_prev=None, btn_next=None, table=None):
    stats_w      = stats      or MagicMock(); stats_w.update = MagicMock()
    notice_w     = notice     or MagicMock(); notice_w.update = MagicMock()
    page_label_w = page_label or MagicMock(); page_label_w.update = MagicMock()
    btn_prev_w   = btn_prev   or MagicMock(); btn_prev_w.disabled  = True
    btn_next_w   = btn_next   or MagicMock(); btn_next_w.disabled  = True
    table_w      = table      or MagicMock()
    table_w.row_count = 0
    table_w.cursor_row = 0

    def _q(sel, *_args):
        if "#plans-stats"  in str(sel): return stats_w
        if "#empty-notice" in str(sel): return notice_w
        if "#page-label"   in str(sel): return page_label_w
        if "#btn-prev"     in str(sel): return btn_prev_w
        if "#btn-next"     in str(sel): return btn_next_w
        if "#plans-table"  in str(sel): return table_w
        raise ValueError(f"unexpected: {sel}")

    s.query_one = _q
    return stats_w, notice_w, page_label_w, btn_prev_w, btn_next_w, table_w


# ── PAGE_SIZE constant ────────────────────────────────────────────────────────

class TestPageSize(unittest.TestCase):

    def test_page_size_is_at_least_10(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        self.assertGreaterEqual(SavedPlansScreen.PAGE_SIZE, 10)

    def test_page_size_is_int(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        self.assertIsInstance(SavedPlansScreen.PAGE_SIZE, int)


# ── _total_pages ──────────────────────────────────────────────────────────────

class TestTotalPages(unittest.TestCase):

    def test_zero_previews_gives_one_page(self):
        s = _screen()
        s._visible_previews = []
        self.assertEqual(s._total_pages(), 1)

    def test_exactly_page_size_is_one_page(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        s = _screen()
        s._visible_previews = [_make_preview(f"p{i}") for i in range(SavedPlansScreen.PAGE_SIZE)]
        self.assertEqual(s._total_pages(), 1)

    def test_page_size_plus_one_is_two_pages(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        s = _screen()
        s._visible_previews = [_make_preview(f"p{i}") for i in range(SavedPlansScreen.PAGE_SIZE + 1)]
        self.assertEqual(s._total_pages(), 2)

    def test_fractional_last_page_rounds_up(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        s = _screen()
        s._visible_previews = [_make_preview(f"p{i}") for i in range(SavedPlansScreen.PAGE_SIZE * 2 + 3)]
        self.assertEqual(s._total_pages(), 3)


# ── action_prev_page / action_next_page ───────────────────────────────────────

class TestPageNavigation(unittest.TestCase):

    def _setup_two_pages(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        s = _screen()
        s._visible_previews = [_make_preview(f"p{i}") for i in range(SavedPlansScreen.PAGE_SIZE + 1)]
        s._page = 0
        s._go_to_page = MagicMock(side_effect=lambda p: setattr(s, "_page", p))
        return s

    def test_next_page_increments(self):
        s = self._setup_two_pages()
        s.action_next_page()
        self.assertEqual(s._page, 1)

    def test_prev_page_decrements(self):
        s = self._setup_two_pages()
        s._page = 1
        s.action_prev_page()
        self.assertEqual(s._page, 0)

    def test_prev_page_noop_at_zero(self):
        s = self._setup_two_pages()
        s._page = 0
        s.action_prev_page()
        self.assertEqual(s._page, 0)

    def test_next_page_noop_at_last(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        s = _screen()
        s._visible_previews = [_make_preview(f"p{i}") for i in range(SavedPlansScreen.PAGE_SIZE)]
        s._page = 0
        s._go_to_page = MagicMock()
        s.action_next_page()
        s._go_to_page.assert_not_called()


# ── _update_stats ─────────────────────────────────────────────────────────────

class TestUpdateStats(unittest.TestCase):

    def test_stats_shows_plan_count(self):
        s = _screen()
        s._previews         = [_make_preview(f"p{i}") for i in range(5)]
        s._visible_previews = s._previews
        s._run_count        = 3
        s._total_cost       = 1.2345
        stats_w, *_ = _mock_widgets(s)
        s._update_stats()
        call_arg = stats_w.update.call_args[0][0]
        self.assertIn("5", call_arg)

    def test_stats_shows_run_count(self):
        s = _screen()
        s._previews         = [_make_preview("p")]
        s._visible_previews = s._previews
        s._run_count        = 7
        s._total_cost       = 0.0
        stats_w, *_ = _mock_widgets(s)
        s._update_stats()
        call_arg = stats_w.update.call_args[0][0]
        self.assertIn("7", call_arg)

    def test_stats_shows_cost(self):
        s = _screen()
        s._previews         = [_make_preview("p")]
        s._visible_previews = s._previews
        s._run_count        = 2
        s._total_cost       = 3.14
        stats_w, *_ = _mock_widgets(s)
        s._update_stats()
        call_arg = stats_w.update.call_args[0][0]
        self.assertIn("3.14", call_arg)

    def test_stats_shows_dash_when_no_cost(self):
        s = _screen()
        s._previews         = [_make_preview("p")]
        s._visible_previews = s._previews
        s._run_count        = 0
        s._total_cost       = 0.0
        stats_w, *_ = _mock_widgets(s)
        s._update_stats()
        call_arg = stats_w.update.call_args[0][0]
        self.assertIn("—", call_arg)

    def test_stats_shows_filter_count_when_active(self):
        s = _screen()
        all_p = [_make_preview(f"p{i}") for i in range(10)]
        s._previews         = all_p
        s._visible_previews = all_p[:3]
        s._filter           = "foo"
        s._run_count        = 0
        s._total_cost       = 0.0
        stats_w, *_ = _mock_widgets(s)
        s._update_stats()
        call_arg = stats_w.update.call_args[0][0]
        self.assertIn("3", call_arg)


# ── _collect_run_stats ────────────────────────────────────────────────────────

class TestCollectRunStats(unittest.TestCase):

    def test_empty_runs_dir(self, tmp_path=None):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            from ai.pojo_lens_agents._tui_plans import _collect_run_stats
            count, cost = _collect_run_stats(root)
            self.assertEqual(count, 0)
            self.assertEqual(cost, 0.0)

    def test_counts_manifests(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runs = root / "runs"
            for rid in ["run-1", "run-2", "run-3"]:
                (runs / rid).mkdir(parents=True)
                (runs / rid / "manifest.json").write_text(
                    json.dumps({"usageTotals": {"totalCostUsd": 0.5}}), encoding="utf-8"
                )
            from ai.pojo_lens_agents._tui_plans import _collect_run_stats
            count, cost = _collect_run_stats(root)
            self.assertEqual(count, 3)

    def test_sums_cost_from_usage_totals(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runs = root / "runs"
            for i, c in enumerate([1.0, 2.5, 0.3]):
                (runs / f"run-{i}").mkdir(parents=True)
                (runs / f"run-{i}" / "manifest.json").write_text(
                    json.dumps({"usageTotals": {"totalCostUsd": c}}), encoding="utf-8"
                )
            from ai.pojo_lens_agents._tui_plans import _collect_run_stats
            count, cost = _collect_run_stats(root)
            self.assertAlmostEqual(cost, 3.8, places=5)

    def test_falls_back_to_highest_cost_tasks(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runs = root / "runs" / "run-1"
            runs.mkdir(parents=True)
            (runs / "manifest.json").write_text(
                json.dumps({
                    "runGovernance": {
                        "highestCostTasks": [
                            {"costUsd": 0.4},
                            {"costUsd": 0.6},
                        ]
                    }
                }), encoding="utf-8"
            )
            from ai.pojo_lens_agents._tui_plans import _collect_run_stats
            count, cost = _collect_run_stats(root)
            self.assertAlmostEqual(cost, 1.0, places=5)

    def test_ignores_malformed_manifests(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runs = root / "runs" / "run-bad"
            runs.mkdir(parents=True)
            (runs / "manifest.json").write_text("NOT JSON", encoding="utf-8")
            from ai.pojo_lens_agents._tui_plans import _collect_run_stats
            count, cost = _collect_run_stats(root)
            self.assertEqual(count, 1)
            self.assertEqual(cost, 0.0)


# ── pagination bindings in source ────────────────────────────────────────────

class TestPaginationSource(unittest.TestCase):

    def _src(self) -> str:
        return (
            Path(__file__).resolve().parents[1]
            / "ai" / "pojo_lens_agents" / "_tui_plans.py"
        ).read_text(encoding="utf-8")

    def test_left_right_bindings_present(self):
        from ai.pojo_lens_agents._tui_plans import SavedPlansScreen
        keys = {b.key for b in SavedPlansScreen.BINDINGS}
        self.assertIn("left",  keys)
        self.assertIn("right", keys)

    def test_pagination_bar_in_compose(self):
        self.assertIn("pagination-bar", self._src())

    def test_btn_prev_in_compose(self):
        self.assertIn("btn-prev", self._src())

    def test_btn_next_in_compose(self):
        self.assertIn("btn-next", self._src())

    def test_page_label_in_compose(self):
        self.assertIn("page-label", self._src())

    def test_plans_stats_in_compose(self):
        self.assertIn("plans-stats", self._src())
