"""Tests for DashboardWidget stop/pause/delete controls."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def _make_widget(run_path: Path | None = None, state: str = "idle"):
    from ai.pojo_lens_agents._tui_dashboard import DashboardWidget
    w = DashboardWidget()
    w._run_dir = run_path
    w._run_state = state
    return w


def _make_button_mock(disabled: bool = True):
    btn = MagicMock()
    btn.disabled = disabled
    btn.label = ""
    return btn


def _patch_query(w, *, stop_btn=None, pause_btn=None, delete_btn=None):
    stop_btn   = stop_btn   or _make_button_mock()
    pause_btn  = pause_btn  or _make_button_mock()
    delete_btn = delete_btn or _make_button_mock()

    def _q(selector, *_args):
        if "btn-dash-stop"   in str(selector): return stop_btn
        if "btn-dash-pause"  in str(selector): return pause_btn
        if "btn-dash-delete" in str(selector): return delete_btn
        raise ValueError(f"unexpected: {selector}")

    w.query_one = _q
    return stop_btn, pause_btn, delete_btn


# ── _set_action_buttons ───────────────────────────────────────────────────────

class TestSetActionButtons(unittest.TestCase):

    def test_running_enables_stop(self):
        w = _make_widget(state="running")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("running", Path("/fake/run"))
        self.assertFalse(stop.disabled)

    def test_running_enables_pause(self):
        w = _make_widget(state="running")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("running", Path("/fake/run"))
        self.assertFalse(pause.disabled)

    def test_running_disables_delete(self):
        w = _make_widget(state="running")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("running", Path("/fake/run"))
        self.assertTrue(delete.disabled)

    def test_completed_disables_stop(self):
        w = _make_widget(state="completed")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("completed", Path("/fake/run"))
        self.assertTrue(stop.disabled)

    def test_completed_enables_delete(self):
        w = _make_widget(state="completed")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("completed", Path("/fake/run"))
        self.assertFalse(delete.disabled)

    def test_idle_no_run_path_disables_all(self):
        w = _make_widget(state="idle")
        stop, pause, delete = _patch_query(w)
        w._set_action_buttons("idle", None)
        self.assertTrue(stop.disabled)
        self.assertTrue(pause.disabled)
        self.assertTrue(delete.disabled)

    def test_pause_label_when_pause_flag_absent(self, tmp_path=None):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            w = _make_widget(state="running")
            stop, pause, delete = _patch_query(w)
            w._set_action_buttons("running", run_path)
            self.assertEqual(str(pause.label), "PAUSE")

    def test_pause_label_when_pause_flag_present(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            (run_path / "pause.flag").write_text("pause")
            w = _make_widget(state="running")
            stop, pause, delete = _patch_query(w)
            w._set_action_buttons("running", run_path)
            self.assertEqual(str(pause.label), "RESUME")


# ── _do_stop ─────────────────────────────────────────────────────────────────

class TestDoStop(unittest.TestCase):

    def test_stop_writes_flag_file(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            w = _make_widget(run_path=run_path, state="running")
            stop, _, _ = _patch_query(w)
            w._do_stop()
            self.assertTrue((run_path / "stop.flag").exists())

    def test_stop_disables_stop_button(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            w = _make_widget(run_path=run_path, state="running")
            stop, _, _ = _patch_query(w)
            stop.disabled = False
            w._do_stop()
            self.assertTrue(stop.disabled)

    def test_stop_no_run_dir_is_noop(self):
        w = _make_widget(run_path=None, state="idle")
        w._do_stop()  # must not raise


# ── _do_pause_toggle ─────────────────────────────────────────────────────────

class TestDoPauseToggle(unittest.TestCase):

    def test_pause_writes_flag_when_absent(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            w = _make_widget(run_path=run_path, state="running")
            _patch_query(w)
            w._do_pause_toggle()
            self.assertTrue((run_path / "pause.flag").exists())

    def test_resume_removes_flag_when_present(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td)
            (run_path / "pause.flag").write_text("pause")
            w = _make_widget(run_path=run_path, state="running")
            _patch_query(w)
            w._do_pause_toggle()
            self.assertFalse((run_path / "pause.flag").exists())

    def test_pause_no_run_dir_is_noop(self):
        w = _make_widget(run_path=None, state="idle")
        w._do_pause_toggle()  # must not raise


# ── _do_delete ───────────────────────────────────────────────────────────────

class TestDoDelete(unittest.TestCase):

    def test_delete_removes_run_directory(self):
        import tempfile, os
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td) / "run-abc"
            run_path.mkdir()
            (run_path / "manifest.json").write_text("{}")
            w = _make_widget(run_path=run_path, state="completed")

            # patch _show_idle so it doesn't crash without DOM
            w._show_idle = MagicMock()
            _patch_query(w)
            w._do_delete()
            self.assertFalse(run_path.exists())

    def test_delete_clears_run_dir(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td) / "run-xyz"
            run_path.mkdir()
            w = _make_widget(run_path=run_path, state="completed")
            w._show_idle = MagicMock()
            _patch_query(w)
            w._do_delete()
            self.assertIsNone(w._run_dir)

    def test_delete_skipped_when_running(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            run_path = Path(td) / "run-running"
            run_path.mkdir()
            w = _make_widget(run_path=run_path, state="running")
            w._show_idle = MagicMock()
            _patch_query(w)
            w._do_delete()
            self.assertTrue(run_path.exists())

    def test_delete_no_run_dir_is_noop(self):
        w = _make_widget(run_path=None, state="completed")
        w._do_delete()  # must not raise


# ── orchestrator stop/pause flags ────────────────────────────────────────────

class TestOrchestratorStopPauseFlags(unittest.TestCase):

    def test_execute_task_checks_stop_flag_in_source(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "orchestrator_app.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("stop.flag", content)
        self.assertIn("pause.flag", content)

    def test_stop_flag_raises_orchestrator_error(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "orchestrator_app.py"
        content = src.read_text(encoding="utf-8")
        stop_idx  = content.index("stop.flag")
        error_idx = content.index("OrchestratorError", stop_idx)
        self.assertLess(error_idx - stop_idx, 300)

    def test_pause_flag_uses_asyncio_sleep(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "orchestrator_app.py"
        content = src.read_text(encoding="utf-8")
        pause_idx = content.index("pause.flag")
        sleep_idx = content.index("asyncio.sleep", pause_idx)
        self.assertLess(sleep_idx - pause_idx, 400)

    def test_dashboard_compose_has_stop_button(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("btn-dash-stop", content)

    def test_dashboard_compose_has_pause_button(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("btn-dash-pause", content)

    def test_dashboard_compose_has_delete_button(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("btn-dash-delete", content)

    def test_dashboard_do_stop_writes_stop_flag(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("stop.flag", content)

    def test_dashboard_do_delete_uses_shutil_rmtree(self):
        src = Path(__file__).resolve().parents[1] / "ai" / "pojo_lens_agents" / "_tui_dashboard.py"
        content = src.read_text(encoding="utf-8")
        self.assertIn("shutil.rmtree", content)
