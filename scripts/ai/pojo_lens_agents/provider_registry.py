"""provider_registry.py — Plugin registry for WP78.

Usage
-----
    from pojo_lens_agents.provider_registry import get_registry

    registry = get_registry()
    provider = registry.get("anthropic-sdk")
    result = provider.complete(system, user, model="claude-sonnet-4-6")
"""
from __future__ import annotations

import importlib
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from pojo_lens_agents.provider_plugin import LLMProvider

from pojo_lens_agents.provider_plugin import ProviderConfigError

_BUILTIN_IDS = frozenset({"anthropic-sdk", "subprocess-claude"})


class ProviderRegistry:
    """Singleton registry for LLM provider plugins."""

    def __init__(self) -> None:
        self._providers: dict[str, "LLMProvider"] = {}
        self._register_builtins()

    def _register_builtins(self) -> None:
        try:
            from pojo_lens_agents.providers.anthropic_sdk import AnthropicSdkProvider
            self._providers["anthropic-sdk"] = AnthropicSdkProvider()
        except Exception:
            pass
        try:
            from pojo_lens_agents.providers.subprocess_claude import SubprocessClaudeProvider
            self._providers["subprocess-claude"] = SubprocessClaudeProvider()
        except Exception:
            pass

    def register(self, provider_id: str, provider: "LLMProvider") -> None:
        """Register a provider instance under an id."""
        self._providers[provider_id] = provider

    def get(self, provider_id: str) -> "LLMProvider":
        """Return the provider for the given id; raises KeyError if unknown."""
        if provider_id not in self._providers:
            raise KeyError(f"Unknown provider id {provider_id!r}. Registered: {sorted(self._providers)}")
        return self._providers[provider_id]

    def has(self, provider_id: str) -> bool:
        """Return True if the provider id is registered."""
        return provider_id in self._providers

    def list_ids(self) -> list[str]:
        """Return sorted list of registered provider ids."""
        return sorted(self._providers)

    def load_from_config(self, config: dict[str, Any]) -> None:
        """Register plugins declared in the ``[providers]`` TOML section.

        ``config`` is the dict parsed from ``[providers]`` — each key is a
        provider id, its value is a sub-dict with at least ``plugin_class``.
        The ``default`` key is reserved and skipped.
        """
        for provider_id, entry in config.items():
            if provider_id == "default":
                continue
            if not isinstance(entry, dict):
                continue
            plugin_class_path = str(entry.get("plugin_class") or "").strip()
            if not plugin_class_path:
                raise ProviderConfigError(
                    f"Provider {provider_id!r}: missing required key 'plugin_class'."
                )
            if provider_id in self._providers:
                raise ProviderConfigError(
                    f"Provider id {provider_id!r} is already registered."
                )
            module_path, _, class_name = plugin_class_path.rpartition(".")
            if not module_path or not class_name:
                raise ProviderConfigError(
                    f"Provider {provider_id!r}: 'plugin_class' must be a dotted import path "
                    f"(e.g. 'my_pkg.MyProvider'), got {plugin_class_path!r}."
                )
            try:
                module = importlib.import_module(module_path)
            except ImportError as exc:
                raise ProviderConfigError(
                    f"Provider {provider_id!r}: cannot import module {module_path!r}: {exc}"
                ) from exc
            cls = getattr(module, class_name, None)
            if cls is None:
                raise ProviderConfigError(
                    f"Provider {provider_id!r}: class {class_name!r} not found in {module_path!r}."
                )
            plugin_config = {k: v for k, v in entry.items() if k != "plugin_class"}
            try:
                instance = cls(config=plugin_config) if plugin_config else cls()
            except Exception as exc:
                raise ProviderConfigError(
                    f"Provider {provider_id!r}: failed to instantiate {plugin_class_path!r}: {exc}"
                ) from exc
            self._providers[provider_id] = instance


_REGISTRY: ProviderRegistry | None = None


def get_registry() -> ProviderRegistry:
    """Return the process-level singleton registry (lazily initialized)."""
    global _REGISTRY
    if _REGISTRY is None:
        _REGISTRY = ProviderRegistry()
    return _REGISTRY


def reset_registry() -> None:
    """Reset the singleton — used in tests only."""
    global _REGISTRY
    _REGISTRY = None
