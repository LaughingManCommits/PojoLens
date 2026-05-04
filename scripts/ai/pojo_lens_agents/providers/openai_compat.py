"""providers/openai_compat.py — OpenAI-compatible reference provider plugin.

Covers OpenAI, Azure OpenAI, Codex, and any /v1/chat/completions API.

Configuration (passed as ``config`` dict to constructor):
    base_url      str   API base URL (default: https://api.openai.com/v1)
    api_key_env   str   env var name holding the API key (default: OPENAI_API_KEY)
    model         str   default model name (default: gpt-4o)

The ``openai`` package is optional; importing this module succeeds even if it
is not installed. ``complete()`` raises ``ProviderConfigError`` if the package
is absent.
"""
from __future__ import annotations

import os
from typing import Any

from pojo_lens_agents.provider_plugin import (
    ModelPricing,
    ProviderConfigError,
    ProviderResult,
    RateLimitMeta,
)

_PROVIDER_ID = "openai-compat"

_OPENAI_PRICING: dict[str, ModelPricing] = {
    "gpt-4o": ModelPricing(input_per_1k_usd=0.005, output_per_1k_usd=0.015),
    "gpt-4o-mini": ModelPricing(input_per_1k_usd=0.00015, output_per_1k_usd=0.0006),
    "gpt-4-turbo": ModelPricing(input_per_1k_usd=0.01, output_per_1k_usd=0.03),
    "gpt-3.5-turbo": ModelPricing(input_per_1k_usd=0.0005, output_per_1k_usd=0.0015),
}

_DEFAULT_BASE_URL = "https://api.openai.com/v1"
_DEFAULT_MODEL = "gpt-4o"


def _openai_available() -> bool:
    try:
        import openai  # noqa: F401
        return True
    except ImportError:
        return False


class OpenAICompatProvider:
    """Reference OpenAI-compatible LLM provider."""

    def __init__(self, config: dict[str, Any] | None = None) -> None:
        cfg = config or {}
        self._base_url: str = str(cfg.get("base_url") or _DEFAULT_BASE_URL).rstrip("/")
        self._api_key_env: str = str(cfg.get("api_key_env") or "OPENAI_API_KEY")
        self._default_model: str = str(cfg.get("model") or _DEFAULT_MODEL)

    def _api_key(self) -> str:
        key = os.environ.get(self._api_key_env, "").strip()
        if not key:
            raise ProviderConfigError(
                f"OpenAICompatProvider: env var {self._api_key_env!r} is not set."
            )
        return key

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
        if not _openai_available():
            raise ProviderConfigError(
                "OpenAICompatProvider requires the 'openai' package. "
                "Install it: pip install openai"
            )
        import openai

        resolved_model = model or self._default_model
        client = openai.OpenAI(
            api_key=self._api_key(),
            base_url=self._base_url,
            timeout=float(timeout_sec) if timeout_sec else 1800.0,
        )
        messages: list[dict[str, str]] = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": user_prompt})
        try:
            response = client.chat.completions.create(
                model=resolved_model,
                messages=messages,
            )
        except openai.RateLimitError as exc:
            from pojo_lens_agents.provider_plugin import ProviderRateLimitError
            raise ProviderRateLimitError(str(exc)) from exc
        except openai.AuthenticationError as exc:
            from pojo_lens_agents.provider_plugin import ProviderAuthError
            raise ProviderAuthError(str(exc)) from exc
        except openai.APITimeoutError as exc:
            from pojo_lens_agents.provider_plugin import ProviderTimeoutError
            raise ProviderTimeoutError(str(exc)) from exc

        choice = response.choices[0] if response.choices else None
        text = (choice.message.content or "") if choice else ""
        usage = self.map_usage(response.usage)
        return ProviderResult(
            text=text,
            usage=usage,
            provider_id=_PROVIDER_ID,
            model=resolved_model,
            error=None,
        )

    def rate_limit_meta(self) -> RateLimitMeta:
        return RateLimitMeta()

    def model_pricing(self, model: str | None = None) -> ModelPricing:
        m = model or self._default_model
        return _OPENAI_PRICING.get(m, ModelPricing())

    def map_usage(self, raw: Any) -> dict[str, Any]:
        if raw is None:
            return {}
        if isinstance(raw, dict):
            return raw
        return {
            "inputTokens": getattr(raw, "prompt_tokens", None),
            "outputTokens": getattr(raw, "completion_tokens", None),
            "totalTokens": getattr(raw, "total_tokens", None),
        }
