"""Tests for WP78: provider_plugin.py, provider_registry.py, built-in providers."""
from __future__ import annotations

import pytest

from pojo_lens_agents.provider_plugin import (
    LLMProvider,
    ModelPricing,
    ProviderConfigError,
    ProviderPluginError,
    ProviderResult,
    RateLimitMeta,
)
from pojo_lens_agents.provider_registry import ProviderRegistry, get_registry, reset_registry


# ---------------------------------------------------------------------------
# Helpers / fixtures
# ---------------------------------------------------------------------------

class _FakeProvider:
    """Minimal conformant provider for testing."""

    def complete(self, system_prompt, user_prompt, **kwargs) -> ProviderResult:
        return ProviderResult(
            text=f"echo:{user_prompt[:20]}",
            usage={"inputTokens": 10, "outputTokens": 5},
            provider_id="fake",
            model=kwargs.get("model"),
        )

    def rate_limit_meta(self) -> RateLimitMeta:
        return RateLimitMeta(tpm_limit=100_000, rpm_limit=60)

    def model_pricing(self) -> ModelPricing:
        return ModelPricing(input_per_1k_usd=0.001, output_per_1k_usd=0.002)

    def map_usage(self, raw) -> dict:
        return raw if isinstance(raw, dict) else {}


@pytest.fixture(autouse=True)
def fresh_registry():
    """Reset global registry singleton before each test."""
    reset_registry()
    yield
    reset_registry()


# ---------------------------------------------------------------------------
# ProviderResult dataclass
# ---------------------------------------------------------------------------

def test_provider_result_defaults():
    r = ProviderResult(text="hello")
    assert r.usage == {}
    assert r.provider_id == ""
    assert r.model is None
    assert r.error is None


def test_provider_result_full():
    r = ProviderResult(text="hi", usage={"i": 1}, provider_id="x", model="m", error=None)
    assert r.text == "hi"
    assert r.usage == {"i": 1}


# ---------------------------------------------------------------------------
# RateLimitMeta / ModelPricing
# ---------------------------------------------------------------------------

def test_rate_limit_meta_defaults():
    m = RateLimitMeta()
    assert m.tpm_limit is None
    assert m.rpm_limit is None


def test_model_pricing_defaults():
    p = ModelPricing()
    assert p.input_per_1k_usd == 0.0
    assert p.output_per_1k_usd == 0.0


# ---------------------------------------------------------------------------
# Exception hierarchy
# ---------------------------------------------------------------------------

def test_provider_config_error_is_plugin_error():
    assert issubclass(ProviderConfigError, ProviderPluginError)


def test_all_plugin_errors_are_runtime():
    from pojo_lens_agents.provider_plugin import (
        ProviderAuthError, ProviderRateLimitError, ProviderTimeoutError,
    )
    for cls in (ProviderConfigError, ProviderAuthError, ProviderRateLimitError, ProviderTimeoutError):
        assert issubclass(cls, RuntimeError)


# ---------------------------------------------------------------------------
# LLMProvider Protocol conformance
# ---------------------------------------------------------------------------

def test_fake_provider_satisfies_protocol():
    assert isinstance(_FakeProvider(), LLMProvider)


# ---------------------------------------------------------------------------
# ProviderRegistry — register / get / list / has
# ---------------------------------------------------------------------------

def test_registry_register_and_get():
    r = ProviderRegistry()
    r._providers.clear()  # clear auto-registered builtins
    r.register("fake", _FakeProvider())
    provider = r.get("fake")
    assert isinstance(provider, _FakeProvider)


def test_registry_has():
    r = ProviderRegistry()
    r._providers.clear()
    r.register("fake", _FakeProvider())
    assert r.has("fake")
    assert not r.has("missing")


def test_registry_list_ids_sorted():
    r = ProviderRegistry()
    r._providers.clear()
    r.register("z-prov", _FakeProvider())
    r.register("a-prov", _FakeProvider())
    assert r.list_ids() == ["a-prov", "z-prov"]


def test_registry_get_unknown_raises():
    r = ProviderRegistry()
    r._providers.clear()
    with pytest.raises(KeyError, match="unknown-id"):
        r.get("unknown-id")


# ---------------------------------------------------------------------------
# ProviderRegistry — built-ins auto-registered
# ---------------------------------------------------------------------------

def test_builtins_registered():
    r = ProviderRegistry()
    ids = r.list_ids()
    assert "anthropic-sdk" in ids
    assert "subprocess-claude" in ids


# ---------------------------------------------------------------------------
# ProviderRegistry — load_from_config
# ---------------------------------------------------------------------------

def test_load_from_config_registers_plugin(tmp_path):
    import sys
    # Create a minimal provider module on disk
    pkg_dir = tmp_path / "testpkg"
    pkg_dir.mkdir()
    (pkg_dir / "__init__.py").write_text("")
    (pkg_dir / "myprovider.py").write_text(
        "from pojo_lens_agents.provider_plugin import ProviderResult, RateLimitMeta, ModelPricing\n"
        "class MyProv:\n"
        "    def complete(self, s, u, **kw): return ProviderResult(text='ok')\n"
        "    def rate_limit_meta(self): return RateLimitMeta()\n"
        "    def model_pricing(self): return ModelPricing()\n"
        "    def map_usage(self, r): return {}\n"
    )
    sys.path.insert(0, str(tmp_path))
    try:
        r = ProviderRegistry()
        r._providers.clear()
        r.load_from_config({"my-prov": {"plugin_class": "testpkg.myprovider.MyProv"}})
        assert r.has("my-prov")
    finally:
        sys.path.remove(str(tmp_path))


def test_load_from_config_missing_plugin_class_raises():
    r = ProviderRegistry()
    r._providers.clear()
    with pytest.raises(ProviderConfigError, match="plugin_class"):
        r.load_from_config({"bad": {"some_key": "val"}})


def test_load_from_config_bad_import_raises():
    r = ProviderRegistry()
    r._providers.clear()
    with pytest.raises(ProviderConfigError, match="cannot import"):
        r.load_from_config({"bad": {"plugin_class": "nonexistent_module_xyz.FooProvider"}})


def test_load_from_config_duplicate_id_raises():
    r = ProviderRegistry()
    r._providers.clear()
    r.register("dup", _FakeProvider())
    with pytest.raises(ProviderConfigError, match="already registered"):
        r.load_from_config({"dup": {"plugin_class": "some.Class"}})


def test_load_from_config_skips_default_key():
    r = ProviderRegistry()
    r._providers.clear()
    r.load_from_config({"default": "anthropic-sdk"})
    assert not r.has("default")


# ---------------------------------------------------------------------------
# get_registry / reset_registry singleton
# ---------------------------------------------------------------------------

def test_get_registry_returns_same_instance():
    a = get_registry()
    b = get_registry()
    assert a is b


def test_reset_registry_creates_new_instance():
    a = get_registry()
    reset_registry()
    b = get_registry()
    assert a is not b


# ---------------------------------------------------------------------------
# Built-in providers — basic behaviour
# ---------------------------------------------------------------------------

def test_anthropic_sdk_provider_importable():
    from pojo_lens_agents.providers.anthropic_sdk import AnthropicSdkProvider
    p = AnthropicSdkProvider()
    assert isinstance(p.rate_limit_meta(), RateLimitMeta)
    assert isinstance(p.model_pricing(), ModelPricing)
    assert p.map_usage({"a": 1}) == {"a": 1}
    assert p.map_usage(None) == {}


def test_subprocess_claude_provider_importable():
    from pojo_lens_agents.providers.subprocess_claude import SubprocessClaudeProvider
    p = SubprocessClaudeProvider()
    assert isinstance(p.rate_limit_meta(), RateLimitMeta)
    assert isinstance(p.model_pricing(), ModelPricing)
    assert p.map_usage({}) == {}


def test_subprocess_claude_complete_raises_not_implemented():
    from pojo_lens_agents.providers.subprocess_claude import SubprocessClaudeProvider
    p = SubprocessClaudeProvider()
    with pytest.raises(NotImplementedError):
        p.complete("sys", "user")


# ---------------------------------------------------------------------------
# OpenAI compat provider — config and metadata
# ---------------------------------------------------------------------------

def test_openai_compat_importable():
    from pojo_lens_agents.providers.openai_compat import OpenAICompatProvider
    p = OpenAICompatProvider(config={"model": "gpt-4o-mini"})
    assert isinstance(p.rate_limit_meta(), RateLimitMeta)
    pricing = p.model_pricing("gpt-4o-mini")
    assert pricing.input_per_1k_usd > 0
    assert p.map_usage({"totalTokens": 5}) == {"totalTokens": 5}


def test_openai_compat_complete_raises_when_package_missing(monkeypatch):
    import builtins
    real_import = builtins.__import__

    def _mock_import(name, *args, **kwargs):
        if name == "openai":
            raise ImportError("no openai")
        return real_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", _mock_import)
    from pojo_lens_agents.providers.openai_compat import OpenAICompatProvider
    p = OpenAICompatProvider()
    with pytest.raises(ProviderConfigError, match="openai"):
        p.complete("sys", "user")


# ---------------------------------------------------------------------------
# orchestrator_contracts: provider field on AgentDefinition / TaskDefinition
# ---------------------------------------------------------------------------

def test_agent_definition_provider_default():
    from pojo_lens_agents.orchestrator_contracts import AgentDefinition
    a = AgentDefinition(name="a", description="d", prompt="p")
    assert a.provider is None


def test_task_definition_provider_default():
    from pojo_lens_agents.orchestrator_contracts import TaskDefinition
    t = TaskDefinition(id="t1", title="T", agent="a", prompt="p")
    assert t.provider is None


def test_agent_model_provider_field():
    from pojo_lens_agents.orchestrator_models import AgentDefinitionModel
    m = AgentDefinitionModel.model_validate({
        "name": "ag",
        "description": "d",
        "prompt": "p",
        "provider": "openai-compat",
    })
    assert m.provider == "openai-compat"


def test_task_model_provider_field():
    from pojo_lens_agents.orchestrator_models import TaskDefinitionModel
    m = TaskDefinitionModel.model_validate({
        "id": "t1",
        "title": "T",
        "agent": "ag",
        "prompt": "p",
        "provider": "my-provider",
    })
    assert m.provider == "my-provider"


# ---------------------------------------------------------------------------
# config_loader: load_providers_config
# ---------------------------------------------------------------------------

def test_load_providers_config_empty_when_no_config_path():
    from pojo_lens_agents.config_loader import load_providers_config
    result = load_providers_config(config_path=None, env={}, root=__import__("pathlib").Path("/nonexistent"))
    assert result == {}


def test_load_providers_config_parses_section(tmp_path):
    import tomllib
    cfg = tmp_path / "pojolens-agents.toml"
    cfg.write_bytes(b"""
[providers]
default = "anthropic-sdk"

[providers.openai]
plugin_class = "pojo_lens_agents.providers.openai_compat.OpenAICompatProvider"
model = "gpt-4o"
""")
    from pojo_lens_agents.config_loader import load_providers_config
    result = load_providers_config(config_path=str(cfg))
    assert result.get("default") == "anthropic-sdk"
    assert result["openai"]["plugin_class"].endswith("OpenAICompatProvider")
