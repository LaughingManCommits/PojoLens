"""providers/anthropic_sdk.py — Built-in Anthropic SDK provider plugin."""
from __future__ import annotations

from typing import Any

from pojo_lens_agents.provider_plugin import (
    LLMProvider,
    ModelPricing,
    ProviderResult,
    RateLimitMeta,
)

_PROVIDER_ID = "anthropic-sdk"

_PRICING: dict[str, ModelPricing] = {
    "claude-opus-4-7": ModelPricing(input_per_1k_usd=0.015, output_per_1k_usd=0.075),
    "claude-sonnet-4-6": ModelPricing(input_per_1k_usd=0.003, output_per_1k_usd=0.015),
    "claude-haiku-4-5-20251001": ModelPricing(input_per_1k_usd=0.00025, output_per_1k_usd=0.00125),
}


class AnthropicSdkProvider:
    """Delegates to the existing run_sdk_provider implementation."""

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
        on_partial_text: Any = None,
        **kwargs: Any,
    ) -> ProviderResult:
        from pojo_lens_agents.sdk_provider import run_sdk_provider
        result = run_sdk_provider(
            system_prompt,
            user_prompt,
            model=model,
            workspace_root=workspace_root,
            timeout_sec=timeout_sec,
            extra_tools=extra_tools,
            shared_context_path=shared_context_path,
            task_id=task_id,
            on_partial_text=on_partial_text,
        )
        return ProviderResult(
            text=result.text or "",
            usage=result.usage or {},
            provider_id=_PROVIDER_ID,
            model=model,
            error=result.error or None,
        )

    def rate_limit_meta(self) -> RateLimitMeta:
        import os
        tpm = None
        rpm = None
        _tpm = os.environ.get("ANTHROPIC_TPM_LIMIT", "").strip()
        _rpm = os.environ.get("ANTHROPIC_RPM_LIMIT", "").strip()
        if _tpm.isdigit():
            tpm = int(_tpm)
        if _rpm.isdigit():
            rpm = int(_rpm)
        return RateLimitMeta(tpm_limit=tpm, rpm_limit=rpm)

    def model_pricing(self, model: str | None = None) -> ModelPricing:
        if model and model in _PRICING:
            return _PRICING[model]
        return ModelPricing()

    def map_usage(self, raw: Any) -> dict[str, Any]:
        if isinstance(raw, dict):
            return raw
        return {}
