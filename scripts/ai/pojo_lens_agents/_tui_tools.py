from __future__ import annotations

import sys
from typing import Any

TEXTUAL_IMPORT_ERROR: Exception | None = None

try:
    from textual.app import ComposeResult
    from textual.binding import Binding
    from textual.containers import Container, Horizontal, VerticalScroll
    from textual.screen import Screen
    from textual.widgets import Button, Footer, Header, Input, RichLog, Select, Static
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
            yield Static("", id="mem-status")
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
            yield Button("CHECK",   id="btn-check",   variant="success")
            yield Button("QUERY",   id="btn-query",   variant="success")
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

    def _set_status(self, text: str) -> None:
        self.app.call_from_thread(lambda: self.query_one("#mem-status", Static).update(text))

    def _set_buttons(self, enabled: bool) -> None:
        def _update() -> None:
            for btn_id in ("#btn-refresh", "#btn-check", "#btn-query"):
                try:
                    self.query_one(btn_id, Button).disabled = not enabled
                except Exception:
                    pass
        self.app.call_from_thread(_update)

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

    @staticmethod
    def _memory_cmd(script_stem: str, extra_args: list[str] | None = None) -> list[str]:
        """Return platform-appropriate command to run a memory script."""
        import sys as _sys
        from pathlib import Path as _Path
        _base = _Path(__file__).resolve().parents[3]
        _extra = extra_args or []
        if _sys.platform == "win32":
            _ps1 = _base / "scripts" / "ai" / f"{script_stem}.ps1"
            return ["powershell", "-File", str(_ps1), *_extra]
        _py = _base / "scripts" / "ai" / f"{script_stem}.py"
        return [_sys.executable, str(_py), *_extra]

    def _run_query(self, query: str) -> None:
        self._set_status("[#ffaa00][ RUNNING ] querying...[/]")
        self._set_buttons(False)
        self._log(f"[#00e5ff][ SIGNAL ] querying memory: {query!r}...[/]")
        ok = False
        try:
            import subprocess
            import sys as _sys
            _extra = (
                ["-Query", query, "-Limit", "10"]
                if _sys.platform == "win32"
                else ["--query", query, "--limit", "10"]
            )
            cmd = self._memory_cmd("query-ai-memory", _extra)
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ query complete[/]")
                ok = True
            else:
                self._log(f"[#ff2244]✗ query exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]query error: {exc}[/]")
        self._set_status("[#00ff41][ DONE ] query complete[/]" if ok else "[#ff2244][ ERROR ] query failed[/]")
        self._set_buttons(True)

    def _run_refresh(self) -> None:
        self._set_status("[#ffaa00][ RUNNING ] refreshing memory...[/]")
        self._set_buttons(False)
        self._log("[#00e5ff][ SIGNAL ] launching memory refresh...[/]")
        ok = False
        try:
            import subprocess
            cmd = self._memory_cmd("refresh-ai-memory")
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ refresh complete[/]")
                ok = True
            else:
                self._log(f"[#ff2244]✗ refresh exited {result.returncode}[/]")
        except Exception as exc:
            self._log(f"[#ff2244]refresh error: {exc}[/]")
        self._set_status("[#00ff41][ DONE ] refresh complete[/]" if ok else "[#ff2244][ ERROR ] refresh failed[/]")
        self._set_buttons(True)

    def _run_check(self) -> None:
        self._set_status("[#ffaa00][ RUNNING ] checking memory...[/]")
        self._set_buttons(False)
        self._log("[#00e5ff][ SIGNAL ] running memory check...[/]")
        ok = False
        try:
            import subprocess
            import sys as _sys
            _extra = ["-Check"] if _sys.platform == "win32" else ["--check"]
            cmd = self._memory_cmd("refresh-ai-memory", _extra)
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            output = (result.stdout or "") + (result.stderr or "")
            for line in output.splitlines():
                self._log(line)
            if result.returncode == 0:
                self._log("[bold #00ff41]✓ check passed[/]")
                ok = True
            else:
                self._log(f"[#ff2244]✗ check failed (exit {result.returncode})[/]")
        except Exception as exc:
            self._log(f"[#ff2244]check error: {exc}[/]")
        self._set_status("[#00ff41][ DONE ] check passed[/]" if ok else "[#ff2244][ ERROR ] check failed[/]")
        self._set_buttons(True)


# ── SettingsScreen ─────────────────────────────────────────────────────────────

_BOOL_OPTIONS: list[tuple[str, str]] = [("(unset)", ""), ("true", "true"), ("false", "false")]
_NOTIFY_ON_OPTIONS: list[tuple[str, str]] = [
    ("(unset)", ""), ("always", "always"), ("failure", "failure"), ("success", "success"),
]
_WORKSPACE_STRATEGY_OPTIONS: list[tuple[str, str]] = [
    ("(unset)", ""), ("repo", "repo"), ("copy", "copy"), ("scratch", "scratch"),
]
_WORKER_VAL_OPTIONS: list[tuple[str, str]] = [
    ("(unset)", ""), ("intents-only", "intents-only"), ("strict", "strict"), ("off", "off"),
]


class SettingsScreen(Screen):  # type: ignore[type-arg,misc]
    """Edit pojolens-agents.toml configuration."""

    BINDINGS = [Binding("escape", "go_back", "Back", show=True)]

    def __init__(self) -> None:
        super().__init__()
        self._config_path: Any = None   # Path | None
        self._env_path:    Any = None   # Path | None
        self._api_key_set: bool = False  # whether ANTHROPIC_API_KEY is already in env

    def compose(self) -> ComposeResult:
        yield Header()
        with Container(id="top-bar"):
            yield Static("[ SETTINGS ]", id="set-title")
            yield Static("", id="set-path")
        with VerticalScroll(id="set-form"):
            yield Static("TOKENS / API KEYS", classes="set-section")
            yield Static("ANTHROPIC_API_KEY  (stored in .env, never in TOML)", classes="set-label")
            yield Input(id="f-api-key", password=True, placeholder="sk-ant-api03-...")
            yield Static("", id="f-api-key-status")
            yield Static("POJO_LENS_PROVIDER  (auto = sdk if key set, else subprocess)", classes="set-label")
            yield Select(
                [("auto", ""), ("sdk", "sdk"), ("subprocess", "subprocess")],
                id="f-provider", allow_blank=False,
            )

            yield Static("DEFAULTS", classes="set-section")
            yield Static("runtime_root", classes="set-label")
            yield Input(id="f-runtime-root", placeholder=".claude-orchestrator")
            yield Static("claude_bin", classes="set-label")
            yield Input(id="f-claude-bin", placeholder="claude")
            yield Static("max_parallel", classes="set-label")
            yield Input(id="f-max-parallel", placeholder="4")
            yield Static("continue_on_error", classes="set-label")
            yield Select(_BOOL_OPTIONS, id="f-continue-on-error", allow_blank=False)
            yield Static("worker_validation_mode", classes="set-label")
            yield Select(_WORKER_VAL_OPTIONS, id="f-worker-val-mode", allow_blank=False)

            yield Static("NOTIFICATIONS", classes="set-section")
            yield Static("desktop (OS notification)", classes="set-label")
            yield Select(_BOOL_OPTIONS, id="f-desktop", allow_blank=False)
            yield Static("notify_on", classes="set-label")
            yield Select(_NOTIFY_ON_OPTIONS, id="f-notify-on", allow_blank=False)
            yield Static("webhook_url", classes="set-label")
            yield Input(id="f-webhook-url", placeholder="https://...")
            yield Static("slack_webhook_url", classes="set-label")
            yield Input(id="f-slack-webhook", placeholder="https://hooks.slack.com/...")

            yield Static("WORKSPACE", classes="set-section")
            yield Static("strategy", classes="set-label")
            yield Select(_WORKSPACE_STRATEGY_OPTIONS, id="f-ws-strategy", allow_blank=False)
            yield Static("root (workspace dir)", classes="set-label")
            yield Input(id="f-ws-root", placeholder=".workspaces")
        with Horizontal(id="action-bar"):
            yield Button("SAVE", id="btn-save", variant="primary")
            yield Button("BACK", id="btn-back")
        yield Footer()

    def on_mount(self) -> None:
        self.app.title = "POJOLENS  //  SETTINGS"
        self.run_worker(self._load, thread=True, name="load-config")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "btn-save":
            self._save()
        elif event.button.id == "btn-back":
            self.action_go_back()

    def action_go_back(self) -> None:
        self.dismiss(None)

    # ── helpers ────────────────────────────────────────────────────────────────

    def _set_input(self, wid: str, value: str) -> None:
        try:
            self.query_one(f"#{wid}", Input).value = value
        except Exception:
            pass

    def _set_select(self, wid: str, value: str) -> None:
        try:
            self.query_one(f"#{wid}", Select).value = value
        except Exception:
            pass

    def _get_input(self, wid: str) -> str:
        try:
            return self.query_one(f"#{wid}", Input).value.strip()
        except Exception:
            return ""

    def _get_select(self, wid: str) -> str:
        try:
            v = self.query_one(f"#{wid}", Select).value
            return "" if v is Select.BLANK else str(v)
        except Exception:
            return ""

    # ── load ───────────────────────────────────────────────────────────────────

    def _load(self) -> None:
        import os
        from pathlib import Path as _Path
        try:
            from pojo_lens_agents.config_loader import _find_config_path
        except ImportError:
            return

        root = _Path(__file__).resolve().parents[3]
        cfg  = _find_config_path(None, dict(os.environ), root)
        if cfg is None:
            cfg = root / "pojolens-agents.toml"
        self._config_path = cfg
        self._env_path    = root / ".env"

        # Read .env
        env_vars: dict[str, str] = {}
        if self._env_path.exists():
            for line in self._env_path.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, _, v = line.partition("=")
                    env_vars[k.strip()] = v.strip()

        existing_key    = env_vars.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_API_KEY", "")
        existing_prov   = env_vars.get("POJO_LENS_PROVIDER") or os.environ.get("POJO_LENS_PROVIDER", "")
        self._api_key_set = bool(existing_key)

        # Read TOML
        raw: dict = {}
        if cfg.exists():
            try:
                try:
                    import tomllib as _tl
                except ImportError:
                    import tomli as _tl  # type: ignore[no-redef]
                with open(cfg, "rb") as fh:
                    raw = _tl.load(fh)
            except Exception:
                pass

        defs  = raw.get("defaults",      {}) or {}
        notif = raw.get("notifications", {}) or {}
        ws    = raw.get("workspace",     {}) or {}
        notify_on_val = (notif.get("notify_on") or [""])[0] if notif.get("notify_on") else ""

        exists_tag = "" if cfg.exists() else "  [#ffaa00](will be created)[/]"
        key_status = "[#00ff41]● SET[/]" if existing_key else "[#ff2244]● NOT SET[/]"

        def _fill() -> None:
            try:
                self.query_one("#set-path", Static).update(f"[dim]{cfg}[/]{exists_tag}")
                # tokens
                if existing_key:
                    self.query_one("#f-api-key", Input).placeholder = "(already set — leave blank to keep)"
                self.query_one("#f-api-key-status", Static).update(
                    f"  Current status: {key_status}  [dim]env/.env[/]"
                )
                self._set_select("f-provider", existing_prov)
                # defaults
                self._set_input("f-runtime-root", str(defs.get("runtime_root") or ""))
                self._set_input("f-claude-bin",   str(defs.get("claude_bin")   or ""))
                self._set_input("f-max-parallel", str(defs.get("max_parallel") or ""))
                self._set_select("f-continue-on-error", str(defs.get("continue_on_error", "")).lower() if "continue_on_error" in defs else "")
                self._set_select("f-worker-val-mode",   str(defs.get("worker_validation_mode") or ""))
                # notifications
                self._set_select("f-desktop",   str(notif.get("desktop", "")).lower() if "desktop" in notif else "")
                self._set_select("f-notify-on", notify_on_val)
                self._set_input("f-webhook-url",   str(notif.get("webhook_url")       or ""))
                self._set_input("f-slack-webhook", str(notif.get("slack_webhook_url") or ""))
                # workspace
                self._set_select("f-ws-strategy", str(ws.get("strategy") or ""))
                self._set_input("f-ws-root",      str(ws.get("root")     or ""))
            except Exception:
                pass

        self.app.call_from_thread(_fill)

    # ── save ───────────────────────────────────────────────────────────────────

    def _save(self) -> None:
        import os
        from pathlib import Path as _Path

        api_key       = self._get_input("f-api-key")
        provider      = self._get_select("f-provider")
        runtime_root  = self._get_input("f-runtime-root")
        claude_bin    = self._get_input("f-claude-bin")
        max_parallel_s= self._get_input("f-max-parallel")
        cont_err      = self._get_select("f-continue-on-error")
        worker_val    = self._get_select("f-worker-val-mode")
        desktop       = self._get_select("f-desktop")
        notify_on     = self._get_select("f-notify-on")
        webhook_url   = self._get_input("f-webhook-url")
        slack_webhook = self._get_input("f-slack-webhook")
        ws_strategy   = self._get_select("f-ws-strategy")
        ws_root       = self._get_input("f-ws-root")

        # validate max_parallel
        max_parallel: int | None = None
        if max_parallel_s:
            try:
                max_parallel = int(max_parallel_s)
                if max_parallel < 1:
                    raise ValueError
            except ValueError:
                self.app.notify("max_parallel must be a positive integer.", title="Validation", severity="error")  # type: ignore[attr-defined]
                return

        if self._config_path is None or self._env_path is None:
            self.app.notify("Config path unknown.", title="Error", severity="error")  # type: ignore[attr-defined]
            return

        # ── write .env ────────────────────────────────────────────────────────
        env_lines: list[str] = []
        # Preserve existing key if user left field blank
        effective_key = api_key or (os.environ.get("ANTHROPIC_API_KEY", "") if self._api_key_set else "")
        if effective_key:
            env_lines.append(f"ANTHROPIC_API_KEY={effective_key}")
        if provider:
            env_lines.append(f"POJO_LENS_PROVIDER={provider}")
        try:
            env_path = _Path(self._env_path)
            env_path.write_text("\n".join(env_lines) + ("\n" if env_lines else ""), encoding="utf-8")
        except Exception as exc:
            self.app.notify(f".env write failed: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]
            return

        # ── write pojolens-agents.toml ────────────────────────────────────────
        lines: list[str] = []

        def _s(k: str, v: str) -> str:  return f'{k} = "{v}"'
        def _b(k: str, v: str) -> str:  return f"{k} = {v}"

        d: list[str] = []
        if runtime_root:  d.append(_s("runtime_root", runtime_root))
        if claude_bin:    d.append(_s("claude_bin", claude_bin))
        if max_parallel:  d.append(f"max_parallel = {max_parallel}")
        if cont_err in ("true", "false"):  d.append(_b("continue_on_error", cont_err))
        if worker_val:    d.append(_s("worker_validation_mode", worker_val))
        if d:
            lines += ["[defaults]"] + d + [""]

        n: list[str] = []
        if desktop in ("true", "false"):  n.append(_b("desktop", desktop))
        if notify_on:     n.append(f'notify_on = ["{notify_on}"]')
        if webhook_url:   n.append(_s("webhook_url", webhook_url))
        if slack_webhook: n.append(_s("slack_webhook_url", slack_webhook))
        if n:
            lines += ["[notifications]"] + n + [""]

        w: list[str] = []
        if ws_strategy:  w.append(_s("strategy", ws_strategy))
        if ws_root:      w.append(_s("root", ws_root))
        if w:
            lines += ["[workspace]"] + w + [""]

        try:
            _Path(self._config_path).write_text("\n".join(lines), encoding="utf-8")
            self.app.notify("Settings saved (TOML + .env)", title="Saved")  # type: ignore[attr-defined]
        except Exception as exc:
            self.app.notify(f"TOML write failed: {exc}", title="Error", severity="error")  # type: ignore[attr-defined]
