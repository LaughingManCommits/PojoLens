from __future__ import annotations

from copy import deepcopy
from pathlib import Path
from typing import Any

from pojo_lens_agents.orchestrator_contracts import AI_ORCHESTRATOR_DIR


DEFAULT_MODEL_PRICING_PATH = AI_ORCHESTRATOR_DIR / "model-pricing.json"

EFFORT_ORDER = ("low", "medium", "high", "xhigh")
DEFAULT_EFFORT = "medium"
EFFORT_HEURISTICS: dict[str, dict[str, float]] = {
    "low": {
        "input_min_multiplier": 1.0,
        "input_max_multiplier": 1.2,
        "output_min_multiplier": 0.35,
        "output_max_multiplier": 1.0,
        "min_output_floor": 120.0,
        "max_output_floor": 700.0,
        "duration_overhead_min_sec": 15.0,
        "duration_overhead_max_sec": 60.0,
    },
    "medium": {
        "input_min_multiplier": 1.0,
        "input_max_multiplier": 1.35,
        "output_min_multiplier": 0.5,
        "output_max_multiplier": 1.7,
        "min_output_floor": 180.0,
        "max_output_floor": 1400.0,
        "duration_overhead_min_sec": 30.0,
        "duration_overhead_max_sec": 150.0,
    },
    "high": {
        "input_min_multiplier": 1.05,
        "input_max_multiplier": 1.65,
        "output_min_multiplier": 0.75,
        "output_max_multiplier": 2.5,
        "min_output_floor": 280.0,
        "max_output_floor": 2600.0,
        "duration_overhead_min_sec": 60.0,
        "duration_overhead_max_sec": 360.0,
    },
    "xhigh": {
        "input_min_multiplier": 1.1,
        "input_max_multiplier": 2.0,
        "output_min_multiplier": 1.0,
        "output_max_multiplier": 3.6,
        "min_output_floor": 420.0,
        "max_output_floor": 4200.0,
        "duration_overhead_min_sec": 120.0,
        "duration_overhead_max_sec": 720.0,
    },
}
MODEL_PROFILE_HEURISTICS: dict[str, dict[str, float]] = {
    "simple": {
        "output_scale": 0.85,
        "duration_scale": 0.7,
        "tokens_per_second_fast": 180.0,
        "tokens_per_second_slow": 70.0,
    },
    "balanced": {
        "output_scale": 1.0,
        "duration_scale": 1.0,
        "tokens_per_second_fast": 120.0,
        "tokens_per_second_slow": 40.0,
    },
    "complex": {
        "output_scale": 1.25,
        "duration_scale": 1.45,
        "tokens_per_second_fast": 90.0,
        "tokens_per_second_slow": 28.0,
    },
}


def _round_money(value: float) -> float:
    return round(float(value), 6)


def _round_minutes(value: float) -> float:
    return round(float(value), 2)


def _normalize_effort(value: str | None) -> str:
    normalized = str(value or DEFAULT_EFFORT).strip().lower()
    if normalized not in EFFORT_HEURISTICS:
        return DEFAULT_EFFORT
    return normalized


def _normalize_profile(value: str | None) -> str:
    normalized = str(value or "balanced").strip().lower()
    if normalized not in MODEL_PROFILE_HEURISTICS:
        return "balanced"
    return normalized


def _task_may_write(task: Any) -> bool:
    return bool(getattr(task, "write_paths", None) or getattr(task, "writePaths", None))


def _task_prompt_seed_tokens(task: Any, plan: Any, *, estimate_tokens) -> int:
    shared_summary = str(getattr(getattr(plan, "shared_context", None), "summary", "") or "")
    task_prompt = str(getattr(task, "prompt", "") or "")
    task_title = str(getattr(task, "title", "") or "")
    constraints = [str(item) for item in list(getattr(task, "constraints", []) or [])]
    validation = [str(item) for item in list(getattr(task, "validation", []) or [])]
    read_paths = [str(item) for item in list(getattr(task, "read_paths", []) or [])]
    write_paths = [str(item) for item in list(getattr(task, "write_paths", []) or [])]
    shared_read_paths = [str(item) for item in list(getattr(getattr(plan, "shared_context", None), "read_paths", []) or [])]
    dependency_count = len(list(getattr(task, "depends_on", []) or []))
    raw_text = "\n".join(
        [
            shared_summary,
            task_title,
            task_prompt,
            *constraints,
            *validation,
            *read_paths,
            *write_paths,
            *shared_read_paths,
        ]
    )
    base_tokens = max(int(estimate_tokens(raw_text)), 1)
    path_overhead = 12 * (len(read_paths) + len(write_paths) + len(shared_read_paths))
    dependency_overhead = 80 * dependency_count
    return max(base_tokens + path_overhead + dependency_overhead, 1)


def _resolved_prompt_budget_tokens(task: Any, agent: Any) -> int | None:
    task_budget = getattr(task, "max_prompt_estimated_tokens", None)
    if task_budget is not None:
        return int(task_budget)
    agent_budget = getattr(agent, "max_prompt_estimated_tokens", None)
    if agent_budget is not None:
        return int(agent_budget)
    return None


def load_model_pricing(
    pricing_path: Path | None = None,
    *,
    read_json,
    error_factory: type[Exception],
) -> dict[str, Any]:
    path = (pricing_path or DEFAULT_MODEL_PRICING_PATH).resolve()
    payload = read_json(path)
    if not isinstance(payload, dict):
        raise error_factory(f"{path}: model pricing payload must be a JSON object")
    models = payload.get("models")
    if not isinstance(models, dict) or not models:
        raise error_factory(f"{path}: model pricing payload must define at least one model")
    normalized_models: dict[str, dict[str, Any]] = {}
    alias_map: dict[str, str] = {}
    for model_name, model_payload in models.items():
        if not isinstance(model_payload, dict):
            raise error_factory(f"{path}: pricing entry '{model_name}' must be an object")
        input_price = model_payload.get("inputUsdPerMillionTokens")
        output_price = model_payload.get("outputUsdPerMillionTokens")
        if not isinstance(input_price, (int, float)) or input_price <= 0:
            raise error_factory(f"{path}: pricing entry '{model_name}' must set positive inputUsdPerMillionTokens")
        if not isinstance(output_price, (int, float)) or output_price <= 0:
            raise error_factory(f"{path}: pricing entry '{model_name}' must set positive outputUsdPerMillionTokens")
        aliases = model_payload.get("aliases", [])
        if not isinstance(aliases, list) or any(not isinstance(alias, str) or not alias.strip() for alias in aliases):
            raise error_factory(f"{path}: pricing entry '{model_name}' aliases must be a string list")
        normalized_models[model_name] = {
            "name": model_name,
            "inputUsdPerMillionTokens": float(input_price),
            "outputUsdPerMillionTokens": float(output_price),
            "aliases": [alias.strip() for alias in aliases],
        }
        alias_map[model_name] = model_name
        for alias in aliases:
            alias_map[alias.strip()] = model_name
    result = deepcopy(payload)
    result["pricingPath"] = str(path)
    result["models"] = normalized_models
    result["aliasMap"] = alias_map
    return result


def estimate_task_cost(
    task: Any,
    agent: Any,
    plan: Any,
    *,
    pricing: dict[str, Any],
    task_model: str | None = None,
    task_model_profile: str | None = None,
    task_effort: str | None = None,
    prompt_estimated_tokens: int | None = None,
    batch_index: int | None = None,
    estimate_tokens,
    error_factory: type[Exception],
) -> dict[str, Any]:
    model_name = str(task_model or getattr(task, "model", "") or "").strip()
    if not model_name:
        raise error_factory(f"Task '{getattr(task, 'id', '<unknown>')}' is missing a resolved model")
    alias_map = pricing.get("aliasMap", {})
    canonical_name = alias_map.get(model_name, model_name)
    model_pricing = pricing["models"].get(canonical_name)
    if model_pricing is None:
        raise error_factory(
            f"Task '{getattr(task, 'id', '<unknown>')}' resolved model '{model_name}' is missing from {pricing.get('pricingPath', 'pricing table')}"
        )
    effort = _normalize_effort(task_effort or getattr(task, "effort", None) or getattr(agent, "effort", None))
    profile = _normalize_profile(task_model_profile or getattr(task, "model_profile", None) or getattr(agent, "model_profile", None))
    effort_heuristic = EFFORT_HEURISTICS[effort]
    profile_heuristic = MODEL_PROFILE_HEURISTICS[profile]
    prompt_budget_tokens = _resolved_prompt_budget_tokens(task, agent)
    observed_prompt_tokens = int(prompt_estimated_tokens) if prompt_estimated_tokens is not None else None
    prompt_seed_tokens = observed_prompt_tokens
    if prompt_seed_tokens is None:
        prompt_seed_tokens = _task_prompt_seed_tokens(task, plan, estimate_tokens=estimate_tokens)
    input_min = max(int(round(prompt_seed_tokens * effort_heuristic["input_min_multiplier"])), 1)
    input_max = max(int(round(prompt_seed_tokens * effort_heuristic["input_max_multiplier"])), input_min)
    if prompt_budget_tokens is not None:
        input_max = min(input_max, int(prompt_budget_tokens))
        input_min = min(input_min, input_max)
    write_scale = 1.15 if _task_may_write(task) else 0.95
    output_min = max(
        int(
            round(
                max(
                    prompt_seed_tokens
                    * effort_heuristic["output_min_multiplier"]
                    * profile_heuristic["output_scale"]
                    * write_scale,
                    effort_heuristic["min_output_floor"],
                )
            )
        ),
        1,
    )
    output_max = max(
        int(
            round(
                max(
                    prompt_seed_tokens
                    * effort_heuristic["output_max_multiplier"]
                    * profile_heuristic["output_scale"]
                    * write_scale,
                    effort_heuristic["max_output_floor"],
                )
            )
        ),
        output_min,
    )
    total_min = input_min + output_min
    total_max = input_max + output_max
    input_usd_per_token = float(model_pricing["inputUsdPerMillionTokens"]) / 1_000_000.0
    output_usd_per_token = float(model_pricing["outputUsdPerMillionTokens"]) / 1_000_000.0
    min_usd = _round_money((input_min * input_usd_per_token) + (output_min * output_usd_per_token))
    max_usd = _round_money((input_max * input_usd_per_token) + (output_max * output_usd_per_token))
    min_seconds = (
        effort_heuristic["duration_overhead_min_sec"] * profile_heuristic["duration_scale"]
    ) + (total_min / profile_heuristic["tokens_per_second_fast"])
    max_seconds = (
        effort_heuristic["duration_overhead_max_sec"] * profile_heuristic["duration_scale"]
    ) + (total_max / profile_heuristic["tokens_per_second_slow"])
    return {
        "taskId": getattr(task, "id"),
        "title": getattr(task, "title"),
        "agent": getattr(task, "agent"),
        "model": canonical_name,
        "modelAlias": model_name if model_name != canonical_name else None,
        "modelProfile": profile,
        "effort": effort,
        "batchIndex": int(batch_index) if batch_index is not None else None,
        "dependsOn": list(getattr(task, "depends_on", []) or []),
        "writeTask": bool(_task_may_write(task)),
        "promptEstimateSource": "observed" if observed_prompt_tokens is not None else "heuristic",
        "promptEstimatedTokens": int(prompt_seed_tokens),
        "promptBudgetTokens": prompt_budget_tokens,
        "inputTokens": {"min": input_min, "max": input_max},
        "outputTokens": {"min": output_min, "max": output_max},
        "totalTokens": {"min": total_min, "max": total_max},
        "costUsd": {"min": min_usd, "max": max_usd},
        "durationMinutes": {
            "min": _round_minutes(min_seconds / 60.0),
            "max": _round_minutes(max_seconds / 60.0),
        },
    }


def estimate_plan_cost(
    plan: Any,
    agents: dict[str, Any],
    *,
    pricing: dict[str, Any],
    task_models: dict[str, str | None],
    task_model_profiles: dict[str, str | None],
    task_efforts: dict[str, str | None],
    prompt_estimated_tokens_by_task: dict[str, int] | None = None,
    topology: dict[str, Any] | None = None,
    topological_batches,
    estimate_tokens,
    error_factory: type[Exception],
) -> dict[str, Any]:
    prompt_estimated_tokens_by_task = dict(prompt_estimated_tokens_by_task or {})
    batches = topological_batches(plan.tasks)
    task_estimates: list[dict[str, Any]] = []
    by_task_id: dict[str, dict[str, Any]] = {}
    total_min_usd = 0.0
    total_max_usd = 0.0
    total_min_input_tokens = 0
    total_max_input_tokens = 0
    total_min_output_tokens = 0
    total_max_output_tokens = 0
    prompt_observed_task_count = 0
    batch_estimates: list[dict[str, Any]] = []
    wall_clock_min_minutes = 0.0
    wall_clock_max_minutes = 0.0
    for batch_index, batch in enumerate(batches, start=1):
        batch_items: list[dict[str, Any]] = []
        batch_min_usd = 0.0
        batch_max_usd = 0.0
        batch_min_minutes = 0.0
        batch_max_minutes = 0.0
        for task in batch:
            prompt_tokens = prompt_estimated_tokens_by_task.get(task.id)
            if prompt_tokens is not None:
                prompt_observed_task_count += 1
            item = estimate_task_cost(
                task,
                agents[task.agent],
                plan,
                pricing=pricing,
                task_model=task_models.get(task.id),
                task_model_profile=task_model_profiles.get(task.id),
                task_effort=task_efforts.get(task.id),
                prompt_estimated_tokens=prompt_tokens,
                batch_index=batch_index,
                estimate_tokens=estimate_tokens,
                error_factory=error_factory,
            )
            batch_items.append(item)
            by_task_id[task.id] = item
            task_estimates.append(item)
            total_min_usd += float(item["costUsd"]["min"])
            total_max_usd += float(item["costUsd"]["max"])
            total_min_input_tokens += int(item["inputTokens"]["min"])
            total_max_input_tokens += int(item["inputTokens"]["max"])
            total_min_output_tokens += int(item["outputTokens"]["min"])
            total_max_output_tokens += int(item["outputTokens"]["max"])
            batch_min_usd += float(item["costUsd"]["min"])
            batch_max_usd += float(item["costUsd"]["max"])
            batch_min_minutes = max(batch_min_minutes, float(item["durationMinutes"]["min"]))
            batch_max_minutes = max(batch_max_minutes, float(item["durationMinutes"]["max"]))
        batch_estimates.append(
            {
                "batchIndex": batch_index,
                "taskIds": [task.id for task in batch],
                "parallelWidth": len(batch),
                "costUsd": {
                    "min": _round_money(batch_min_usd),
                    "max": _round_money(batch_max_usd),
                },
                "durationMinutes": {
                    "min": _round_minutes(batch_min_minutes),
                    "max": _round_minutes(batch_max_minutes),
                },
            }
        )
        wall_clock_min_minutes += batch_min_minutes
        wall_clock_max_minutes += batch_max_minutes
    observed_count = prompt_observed_task_count
    heuristic_count = len(task_estimates) - observed_count
    if heuristic_count == 0:
        estimate_mode = "observed"
    elif observed_count == 0:
        estimate_mode = "heuristic"
    else:
        estimate_mode = "mixed"
    warnings: list[dict[str, Any]] = []
    run_budget_usd = getattr(getattr(plan, "run_policy", None), "run_budget_usd", None)
    rounded_min_usd = _round_money(total_min_usd)
    rounded_max_usd = _round_money(total_max_usd)
    if run_budget_usd is not None and float(run_budget_usd) < rounded_min_usd:
        warnings.append(
            {
                "kind": "run-budget-below-min-estimate",
                "severity": "warn",
                "message": (
                    f"runBudgetUsd ${float(run_budget_usd):.6f} is below the minimum estimated run cost "
                    f"${rounded_min_usd:.6f}."
                ),
                "runBudgetUsd": float(run_budget_usd),
                "estimatedMinUsd": rounded_min_usd,
            }
        )
    return {
        "currency": str(pricing.get("currency", "USD")),
        "pricingVersion": str(pricing.get("version", "")),
        "pricingPath": str(pricing.get("pricingPath", "")),
        "estimateMode": estimate_mode,
        "promptObservedTaskCount": observed_count,
        "promptHeuristicTaskCount": heuristic_count,
        "totals": {
            "taskCount": len(task_estimates),
            "minUsd": rounded_min_usd,
            "maxUsd": rounded_max_usd,
            "minInputTokens": total_min_input_tokens,
            "maxInputTokens": total_max_input_tokens,
            "minOutputTokens": total_min_output_tokens,
            "maxOutputTokens": total_max_output_tokens,
            "minTokens": total_min_input_tokens + total_min_output_tokens,
            "maxTokens": total_max_input_tokens + total_max_output_tokens,
        },
        "wallClock": {
            "minMinutes": _round_minutes(wall_clock_min_minutes),
            "maxMinutes": _round_minutes(wall_clock_max_minutes),
            "batchCount": len(batch_estimates),
            "maxParallelWidth": int((topology or {}).get("maxParallelWidth", max((len(batch) for batch in batches), default=0)) or 0),
        },
        "tasks": task_estimates,
        "taskById": by_task_id,
        "batches": batch_estimates,
        "warnings": warnings,
    }
