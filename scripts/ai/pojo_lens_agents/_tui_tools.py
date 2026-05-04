from __future__ import annotations

import sys
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, Input, RichLog, Static
except ImportError as exc:  # pragma: no cover
    TEXTUAL_IMPORT_ERROR = exc
    App = object  # type: ignore[assignment,misc]
    Screen = object  # type: ignore[assignment,misc]
    ComposeResult = Any  # type: ignore[assignment]
    Text = None  # type: ignore[assignment]

try:
    from pojo_lens_agents.wizard import _EFFORT_MODEL_MAP
except ImportError:  # pragma: no cover
    _EFFORT_MODEL_MAP = {}  # type: ignore[assignment]


# ── MemoryToolsScreen ──────────────────────────────────────────────────────────

class MemoryToolsScreen(Screen):  # type: ignore[type-arg,misc]
    """AI memory maintenance: refresh, check, query."""

    BINDINGS = [
        Binding("escape", "go_back", "Back", show=True),
        Binding("r", "refresh",    "Refresh", show=True),
        Binding("c", "check",      "Check",   show=True),
        Binding("q", "query_mem",  "Query",   show=True),
    ]

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ MEMORY TOOLS ]  AI Memory Maintenance", id="mem-title")
            yield Static("[R] Refresh  [C] Check  [Q] Query  [Esc] Back", id="mem-hint")
            yield Static(
                "Hot context: ai/core/agent-invariants.md · ai/core/repo-purpose.md\n"
                "             ai/state/current-state.md · ai/state/handoff.md\n"
                "After tracked memory changes: run refresh → check.",
                id="mem-desc",
            )
        with Horizontal(id="query-bar"):
            yield Static("QUERY: ", id="query-label")
            yield Input(
                placeholder="search keywords for query-ai-memory.ps1 ...",
                id="query-input",
            )
        yield RichLog(id="mem-log", markup=True, auto_scroll=True, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("REFRESH", id="btn-refresh", variant="primary")
            yield Button("CHECK",   id="btn-check")
            yield Button("QUERY",   id="btn-query")
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  MEMORY TOOLS"
        log = self.query_one("#mem-log", RichLog)
        log.write("[dim #2a5a3a]Memory tools ready.[/]")
        log.write("Hot context files:")
        log.write("  [#00e5ff]•[/] ai/core/agent-invariants.md")
        log.write("  [#00e5ff]•[/] ai/core/repo-purpose.md")
        log.write("  [#00e5ff]•[/] ai/state/current-state.md")
        log.write("  [#00e5ff]•[/] ai/state/handoff.md")
        log.write("")
        log.write("[dim]Use [R] to refresh derived memory, [C] to check consistency.[/]")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-refresh":
            self.action_refresh()
        elif event.button.id == "btn-check":
            self.action_check()
        elif event.button.id == "btn-query":
            self.action_query_mem()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#mem-log", RichLog).write(text))

    def action_refresh(self) -> None:
        self.run_worker(self._run_refresh, thread=True, name="mem-refresh")

    def action_check(self) -> None:
        self.run_worker(self._run_check, thread=True, name="mem-check")

    def action_query_mem(self) -> None:
        query = self.query_one("#query-input", Input).value.strip()
        if not query:
            self.query_one("#query-input", Input).focus()
            self._log("[#ffaa00]Enter a search query above then press [Q] or the QUERY button.[/]")
            return
        self.run_worker(
            lambda: self._run_query(query), thread=True, name="mem-query"
        )

    def _run_query(self, query: str) -> None:
        self._log(f"[#00e5ff][ SIGNAL ] querying memory: {query!r}...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/query-ai-memory.ps1",
                 "-Query", query, "-Limit", "10"],
                capture_output=True,
                text=True,
                timeout=60,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ query complete[/]")
            else:
                self._log(f"[#ff2244]✗ query exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]query error: {exc}[/]")

    def _run_refresh(self) -> None:
        self._log("[#00e5ff][ SIGNAL ] launching memory refresh...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/refresh-ai-memory.ps1"],
                capture_output=True,
                text=True,
                timeout=120,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ refresh complete[/]")
            else:
                self._log(f"[#ff2244]✗ refresh exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]refresh error: {exc}[/]")

    def _run_check(self) -> None:
        self._log("[#00e5ff][ SIGNAL ] running memory check...[/]")
        try:
            import subprocess
            result = subprocess.run(
                ["powershell", "-File", "scripts/ai/refresh-ai-memory.ps1", "-Check"],
                capture_output=True,
                text=True,
                timeout=120,
            )
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ check passed[/]")
            else:
                self._log(f"[#ff2244]✗ check failed (exit {result.returncode})[/]")
        except Exception as exc:
            self._log(f"[#ff2244]check error: {exc}[/]")


# ── SettingsScreen ─────────────────────────────────────────────────────────────

class SettingsScreen(Screen):  # type: ignore[type-arg,misc]
    """Display current configuration defaults."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SETTINGS ]  Current configuration defaults", id="set-title")
            yield Static(
                "Edit ai/orchestrator/README.md or pojolens config for persistent changes.",
                id="set-hint",
            )
        yield RichLog(id="set-log", markup=True, auto_scroll=False, wrap=True, highlight=False)
        with Horizontal(id="action-bar"):
            yield Button("REFRESH", id="btn-refresh")
            yield Button("BACK",    id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SETTINGS"
        self.run_worker(self._load_config, thread=True, name="load-config")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-refresh":
            self.query_one("#set-log", RichLog).clear()
            self.run_worker(self._load_config, thread=True, name="load-config-2")
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    def _log(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#set-log", RichLog).write(text))

    def _load_config(self) -> None:
        handlers      = getattr(self.app, "_handlers", {})
        parse_args_fn = getattr(self.app, "_parse_args_fn", None)

        self._log("[bold #00e5ff]═══ Operator Defaults ═══[/]")

        for key, attr in [
            ("Runtime Root",  "_runtime_root"),
            ("Agents File",   "_agents"),
            ("Claude Binary", "_claude_bin"),
            ("Tasks Dir",     "_tasks_dir"),
        ]:
            val = str(getattr(self.app, attr, "-"))
            self._log(f"  [#00e5ff]{key}:[/] {val}")

        self._log("")
        self._log("[bold #00e5ff]═══ Effort → Model Mapping ═══[/]")
        try:
            for effort, (model, effort_val) in _EFFORT_MODEL_MAP.items():
                self._log(f"  [#00e5ff]{effort:<8}[/] → {model}")
        except Exception:
            self._log("  [dim](mapping unavailable)[/]")

        self._log("")
        self._log("[bold #00e5ff]═══ Rate Limits ═══[/]")
        tpm = str(getattr(self.app, "_tpm_limit", None) or "-")
        rpm = str(getattr(self.app, "_rpm_limit", None) or "-")
        self._log(f"  [#00e5ff]TPM limit:[/] {tpm}  [dim](tokens per minute; - = unlimited)[/]")
        self._log(f"  [#00e5ff]RPM limit:[/] {rpm}  [dim](requests per minute; - = unlimited)[/]")
        if parse_args_fn is not None and "config" in handlers:
            try:
                import io as _cio
                _a = parse_args_fn(["config", "show", "--json"])
                _buf = _cio.StringIO()
                _old = sys.stdout
                sys.stdout = _buf  # type: ignore[assignment]
                try:
                    _cfg = handlers["config"](_a) or {}
                except Exception:
                    _cfg = {}
                finally:
                    sys.stdout = _old
                _defaults = _cfg.get("defaults") or {}
                if isinstance(_defaults, dict):
                    _tpm_cfg = _defaults.get("tpm_limit") or _defaults.get("tpmLimit")
                    _rpm_cfg = _defaults.get("rpm_limit") or _defaults.get("rpmLimit")
                    if _tpm_cfg:
                        self._log(f"  [dim #00e5ff]Config TPM:[/] {_tpm_cfg}")
                    if _rpm_cfg:
                        self._log(f"  [dim #00e5ff]Config RPM:[/] {_rpm_cfg}")
            except Exception:
                pass

        self._log("")
        self._log("[bold #00e5ff]═══ Notification Defaults ═══[/]")
        if parse_args_fn is not None and "config" in handlers:
            try:
                import io as _nio
                _a2 = parse_args_fn(["config", "show", "--json"])
                _buf2 = _nio.StringIO()
                _old2 = sys.stdout
                sys.stdout = _buf2  # type: ignore[assignment]
                try:
                    _cfg2 = handlers["config"](_a2) or {}
                except Exception:
                    _cfg2 = {}
                finally:
                    sys.stdout = _old2
                _def2 = (_cfg2.get("defaults") or {}) if isinstance(_cfg2, dict) else {}
                _notify_on  = _def2.get("notify_on")  or _def2.get("notifyOn")  or "-"
                _webhook    = _def2.get("webhook")     or _def2.get("notifyWebhook") or "-"
                _slack_ch   = _def2.get("slack_channel") or _def2.get("slackChannel") or "-"
                self._log(f"  [#00e5ff]notify_on:[/] {_notify_on}  [dim](never/failure/success/always)[/]")
                self._log(f"  [#00e5ff]webhook  :[/] {_webhook}")
                self._log(f"  [#00e5ff]slack    :[/] {_slack_ch}")
            except Exception:
                self._log("  [dim](notification config unavailable)[/]")
        else:
            self._log("  [dim](config handler not available)[/]")

        self._log("")
        self._log("[bold #00e5ff]═══ Config File ═══[/]")
        if parse_args_fn is not None and "config" in handlers:
            try:
                import io as _io
                args = parse_args_fn(["config", "show", "--json"])
                buf  = _io.StringIO()
                old_stdout = sys.stdout
                sys.stdout = buf  # type: ignore[assignment]
                try:
                    payload = handlers["config"](args)
                except Exception:
                    payload = {}
                finally:
                    sys.stdout = old_stdout
                    captured = buf.getvalue().strip()
                if captured:
                    for line in captured.splitlines():
                        self._log(f"  {line}")
                elif payload:
                    for k, v in sorted(payload.items()):
                        if not k.startswith("_"):
                            self._log(f"  [#00e5ff]{k}:[/] {v}")
            except Exception as exc:
                self._log(f"  [dim](config load error: {exc})[/]")
        else:
            self._log("  [dim](config handler not available)[/]")

        self._log("")
        self._log("[bold #00e5ff]═══ Providers ═══[/]")
        try:
            from pojo_lens_agents.provider_registry import get_registry
            from pojo_lens_agents.provider_plugin import RateLimitMeta, ModelPricing
            from pojo_lens_agents.config_loader import load_default_provider_id
            _cfg_default = load_default_provider_id()
            if _cfg_default:
                self._log(f"  [#00e5ff]configured default:[/] [bold #00ff41]{_cfg_default}[/]")
            else:
                self._log("  [#00e5ff]configured default:[/] [dim](none — set [providers] default in pojolens-agents.toml)[/]")
            _reg = get_registry()
            _ids = _reg.list_ids()
            if _ids:
                for _pid in _ids:
                    try:
                        _prov = _reg.get(_pid)
                        _rl: RateLimitMeta = _prov.rate_limit_meta()
                        _mp: ModelPricing  = _prov.model_pricing()
                        _tpm = f"{_rl.tpm_limit:,}" if _rl.tpm_limit is not None else "—"
                        _rpm = f"{_rl.rpm_limit:,}" if _rl.rpm_limit is not None else "—"
                        _in  = f"${_mp.input_per_1k_usd:.4f}" if _mp.input_per_1k_usd else "—"
                        _out = f"${_mp.output_per_1k_usd:.4f}" if _mp.output_per_1k_usd else "—"
                        _cls = type(_prov).__qualname__
                        _tag = "  [bold #ffaa00][config default][/]" if _pid == _cfg_default else ""
                        self._log(f"  [bold #00ff41]{_pid}[/]  [dim]({_cls})[/]{_tag}")
                        self._log(f"    [#00e5ff]pricing :[/] in {_in}/1k  out {_out}/1k")
                        self._log(f"    [#00e5ff]limits  :[/] TPM {_tpm}  RPM {_rpm}")
                    except Exception as _e:
                        self._log(f"  [#ffaa00]{_pid}[/]  [dim](meta error: {_e})[/]")
            else:
                self._log("  [dim](no providers registered)[/]")
        except ImportError:
            self._log("  [dim](provider registry unavailable)[/]")
        except Exception as exc:
            self._log(f"  [dim](providers error: {exc})[/]")

        self._log("")
        self._log("[dim #2a5a3a][ trace ] settings loaded[/]")
