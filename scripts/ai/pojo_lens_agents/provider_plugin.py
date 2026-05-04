"""provider_plugin.py — LLMProvider plugin contract for WP78.

All third-party and built-in provider plugins implement this contract.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, AsyncIterator, Protocol, runtime_checkable


# ---------------------------------------------------------------------------
# Data types
# ---------------------------------------------------------------------------

@dataclass
class ProviderResult:
    """Normalized result from any LLM provider invocation."""
    text: str
    usage: dict[str, Any] = field(default_factory=dict)
    provider_id: str = ""
    model: str | None = None
    error: str | None = None


@dataclass
class RateLimitMeta:
    """TPM/RPM ceiling reported by a provider (None = unknown / unlimited)."""
    tpm_limit: int | None = None
    rpm_limit: int | None = None


@dataclass
class ModelPricing:
    """Per-1 000-token USD cost for a provider's model."""
    input_per_1k_usd: float = 0.0
    output_per_1k_usd: float = 0.0


# ---------------------------------------------------------------------------
# Exception hierarchy
# ---------------------------------------------------------------------------

class ProviderPluginError(RuntimeError):
    """Base for all provider plugin errors."""


class ProviderConfigError(ProviderPluginError):
    """Raised when plugin config is invalid or incomplete."""


class ProviderAuthError(ProviderPluginError):
    """Raised when provider authentication fails."""


class ProviderRateLimitError(ProviderPluginError):
    """Raised when the provider enforces a rate limit."""


class ProviderTimeoutError(ProviderPluginError):
    """Raised when a provider call exceeds its timeout."""


# ---------------------------------------------------------------------------
# Protocol
# ---------------------------------------------------------------------------

@runtime_checkable
class LLMProvider(Protocol):
    """Contract that every LLM provider plugin must satisfy."""

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
        """Execute the agent task and return the result."""
        ...

    def rate_limit_meta(self) -> RateLimitMeta:
        """Return TPM/RPM ceilings for rate-limit bucket seeding."""
        ...

    def model_pricing(self) -> ModelPricing:
        """Return per-1k-token USD pricing for pre-flight cost estimation."""
        ...

    def map_usage(self, raw: Any) -> dict[str, Any]:
        """Normalize provider-specific usage data to the internal dict shape."""
        ...
