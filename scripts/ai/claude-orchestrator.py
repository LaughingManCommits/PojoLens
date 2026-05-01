#!/usr/bin/env python3
from __future__ import annotations

import importlib
import sys
import types
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

_APP_MODULE_NAME = "pojo_lens_agents.orchestrator_app"
_PROXY_INTERNALS = {
    "__class__",
    "__dict__",
    "__doc__",
    "__file__",
    "__loader__",
    "__name__",
    "__package__",
    "__spec__",
    "_APP_MODULE",
    "_APP_MODULE_NAME",
    "_PROXY_INTERNALS",
    "_ProxyModule",
    "_load_app",
    "SCRIPT_DIR",
    "main",
}
_APP_MODULE: Any | None = None


def _load_app() -> Any:
    global _APP_MODULE
    if _APP_MODULE is None:
        _APP_MODULE = importlib.import_module(_APP_MODULE_NAME)
    return _APP_MODULE


class _ProxyModule(types.ModuleType):
    def __getattr__(self, name: str) -> Any:
        return getattr(_load_app(), name)

    def __setattr__(self, name: str, value: Any) -> None:
        if name in _PROXY_INTERNALS:
            types.ModuleType.__setattr__(self, name, value)
            return
        setattr(_load_app(), name, value)
        types.ModuleType.__setattr__(self, name, value)

    def __dir__(self) -> list[str]:
        return sorted(set(types.ModuleType.__dir__(self)) | set(dir(_load_app())))


sys.modules[__name__].__class__ = _ProxyModule


def main() -> int:
    return int(_load_app().main())


if __name__ == "__main__":
    raise SystemExit(main())
