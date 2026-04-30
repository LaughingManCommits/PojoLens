from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path
from types import ModuleType


REPO_ROOT_ENV = "POJOLENS_REPO_ROOT"
ORCHESTRATOR_RELATIVE_PATH = Path("scripts") / "ai" / "claude-orchestrator.py"


class CliBootstrapError(RuntimeError):
    """Raised when the repo-local orchestrator script cannot be resolved."""


def main(argv: list[str] | None = None) -> int:
    try:
        args = list(sys.argv[1:] if argv is None else argv)
        repo_root, remaining_args = extract_repo_root(args)
        root = resolve_repo_root(repo_root)
        module = load_orchestrator(root)
        previous_argv = sys.argv[:]
        try:
            sys.argv = [str(root / ORCHESTRATOR_RELATIVE_PATH), *remaining_args]
            return int(module.main())
        finally:
            sys.argv = previous_argv
    except CliBootstrapError as exc:
        print(f"[pojolens-agents] {exc}", file=sys.stderr)
        return 2


def extract_repo_root(args: list[str]) -> tuple[str | None, list[str]]:
    repo_root: str | None = None
    remaining: list[str] = []
    index = 0
    while index < len(args):
        arg = args[index]
        if arg == "--repo-root":
            if index + 1 >= len(args):
                raise CliBootstrapError("--repo-root expects a path")
            repo_root = args[index + 1]
            index += 2
            continue
        if arg.startswith("--repo-root="):
            repo_root = arg.split("=", 1)[1]
            index += 1
            continue
        remaining.append(arg)
        index += 1
    return repo_root, remaining


def resolve_repo_root(explicit_root: str | None = None) -> Path:
    candidates: list[Path] = []
    if explicit_root:
        candidates.append(Path(explicit_root))
    env_root = os.environ.get(REPO_ROOT_ENV, "").strip()
    if env_root:
        candidates.append(Path(env_root))
    candidates.extend(Path.cwd().resolve().parents)
    candidates.insert(0, Path.cwd().resolve())
    package_path = Path(__file__).resolve()
    candidates.extend(package_path.parents)

    for candidate in candidates:
        root = candidate.resolve()
        if (root / ORCHESTRATOR_RELATIVE_PATH).is_file():
            return root
    raise CliBootstrapError(
        "Unable to locate PojoLens repo root. Run from the repo or pass --repo-root."
    )


def load_orchestrator(repo_root: Path) -> ModuleType:
    module_path = repo_root / ORCHESTRATOR_RELATIVE_PATH
    spec = importlib.util.spec_from_file_location("pojo_lens_claude_orchestrator", module_path)
    if spec is None or spec.loader is None:
        raise CliBootstrapError(f"Unable to load orchestrator module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


if __name__ == "__main__":
    raise SystemExit(main())
