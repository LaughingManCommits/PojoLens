"""providers/subprocess_claude.py — Built-in subprocess-Claude provider plugin."""
from __future__ import annotations

from typing import Any

from pojo_lens_agents.provider_plugin import (
    ModelPricing,
    ProviderResult,
    RateLimitMeta,
)

_PROVIDER_ID = "subprocess-claude"


class SubprocessClaudeProvider:
    """Delegates to the subprocess execution path (claude binary).

    ``complete()`` is intentionally a no-op here; subprocess dispatch is
    handled directly by task_execution.py for richer streaming/signal support.
    Registering this class lets the registry resolve the built-in id and
    expose metadata (pricing, rate-limit) without duplicating the dispatch
    path.
    """

    def complete(
        self,
        system_prompt: str,
        user_prompt: str,
        *,
        model: str | None = None,
        workspace_root: Any = None,
        timeout_sec: int | None = None,
        extra_tools: list[dict[str, Any]] | None = None,
        shared_context_path: Any = None,
        task_id: str = "",
        **kwargs: Any,
    ) -> ProviderResult:
        raise NotImplementedError(
            "SubprocessClaudeProvider.complete() is not invoked directly; "
            "task_execution uses the subprocess path natively."
        )

    def rate_limit_meta(self) -> RateLimitMeta:
        return RateLimitMeta()

    def model_pricing(self, model: str | None = None) -> ModelPricing:
        return ModelPricing()

    def map_usage(self, raw: Any) -> dict[str, Any]:
        if isinstance(raw, dict):
            return raw
        return {}
