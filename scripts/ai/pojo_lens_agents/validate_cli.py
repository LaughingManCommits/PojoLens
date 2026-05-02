from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any


def validate_command(args: argparse.Namespace, *, deps: dict[str, Any]) -> dict[str, Any]:
    agents_path = Path(args.agents).resolve()
    agents = deps["load_agents"](agents_path)
    payload: dict[str, Any] = {
        "agentsPath": str(agents_path),
        "agentCount": len(agents),
        "agents": sorted(agents),
        "agentSkills": {
            name: list(agent.skills) for name, agent in sorted(agents.items())
        },
        "agentWorkerValidationModes": {
            name: agent.worker_validation_mode for name, agent in sorted(agents.items())
        },
    }
    if args.task_plan:
        plan_path = Path(args.task_plan).resolve()
        plan = deps["load_task_plan"](plan_path, agents)
        deps["validate_scope_contract"](plan, agents)
        task_worker_validation_modes = deps["effective_plan_worker_validation_modes"](plan, agents)
        task_worker_validation_mode_sources = deps["effective_plan_worker_validation_mode_sources"](plan, agents)
        task_model_profiles = deps["effective_plan_model_profiles"](plan, agents)
        task_models = deps["effective_plan_models"](plan, agents)
        task_efforts = deps["effective_plan_efforts"](plan, agents)
        task_effort_sources = deps["effective_plan_effort_sources"](plan, agents)
        complex_model_tasks = deps["complex_model_task_ids"](task_model_profiles)
        topology = deps["analyze_plan_topology"](plan, agents)
        plan_batches = deps["topological_batches"](plan.tasks)
        payload.update(
            {
                "taskPlanPath": str(plan_path),
                "planName": plan.name,
                "runPolicy": deps["serialize_run_policy"](plan.run_policy),
                "taskCount": len(plan.tasks),
                "taskIds": [task.id for task in plan.tasks],
                "taskWorkerValidationModes": task_worker_validation_modes,
                "taskWorkerValidationModeSources": task_worker_validation_mode_sources,
                "taskEfforts": task_efforts,
                "taskEffortSources": task_effort_sources,
                "taskModels": task_models,
                "taskModelProfiles": task_model_profiles,
                "complexModelTaskIds": complex_model_tasks,
                "complexModelTaskCount": len(complex_model_tasks),
                "topology": topology,
                "tasks": [
                    {
                        "id": task.id,
                        "agent": task.agent,
                        "skills": list(task.skills),
                        "resolvedSkills": deps["effective_task_skills"](task, agents[task.agent]),
                        "model": task_models[task.id],
                        "modelProfile": task_model_profiles[task.id],
                        "effort": task_efforts[task.id],
                        "effortSource": task_effort_sources[task.id],
                        "readPaths": deps["effective_task_read_paths"](plan, task),
                        "writePaths": deps["effective_task_write_scope"](task),
                        "dependencyMaterialization": deps["effective_dependency_materialization_mode"](task),
                        "workerValidationMode": task_worker_validation_modes[task.id],
                        "workerValidationModeSource": task_worker_validation_mode_sources[task.id],
                    }
                    for task in plan.tasks
                ],
                "batches": [[task.id for task in batch] for batch in plan_batches],
                "parallelConflicts": deps["detect_parallel_scope_conflicts"](plan, agents),
            }
        )
    return payload
