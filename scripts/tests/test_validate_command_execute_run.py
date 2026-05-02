import asyncio
import json
import pathlib
import subprocess
import tempfile
import unittest
from dataclasses import asdict
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandExecuteRunTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_execute_task_fails_when_worker_stdout_is_not_json(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_subprocess_async = orchestrator.run_subprocess_async
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            runtime_root = temp_path / "runtime"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            runtime_root.mkdir()
            workspaces_dir.mkdir()
            orchestrator.ROOT = repo_root

            async def fake_run_subprocess(command, *, cwd, timeout_sec, progress_action=None):
                return subprocess.CompletedProcess(command, 0, "worker narration only", "")

            orchestrator.run_subprocess_async = fake_run_subprocess
            try:
                agent = orchestrator.AgentDefinition(
                    name="analyst",
                    description="analysis",
                    prompt="Return JSON only.",
                    model_profile="simple",
                    effort="high",
                    permission_mode="dontAsk",
                    workspace_mode="repo",
                    context_mode="minimal",
                    timeout_sec=30,
                    allowed_tools=["Read"],
                    disallowed_tools=[],
                )
                task = orchestrator.TaskDefinition(
                    id="inspect-json",
                    title="Inspect JSON",
                    agent="analyst",
                    prompt="Inspect worker JSON output.",
                    workspace_mode="repo",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="worker-json-failure",
                    goal="Fail malformed worker output cleanly.",
                    shared_context=orchestrator.SharedContext(
                        summary="Worker JSON failure test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[task],
                )

                record = asyncio.run(orchestrator.execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    {"analyst": agent},
                    task,
                    {},
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=False,
                ))
                stdout_text = pathlib.Path(record.stdout_path).read_text(encoding="utf-8")
                stderr_text = pathlib.Path(record.stderr_path).read_text(encoding="utf-8")
            finally:
                orchestrator.run_subprocess_async = old_run_subprocess_async
                orchestrator.ROOT = old_root

        self.assertEqual("failed", record.status)
        self.assertIn("standalone JSON payload", record.summary)
        self.assertEqual("worker narration only", stdout_text)
        self.assertIn("standalone JSON payload", stderr_text)
        self.assertIsNone(record.result_path)

    def test_execute_task_fails_when_intents_only_worker_returns_raw_validation_commands(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_subprocess_async = orchestrator.run_subprocess_async
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            runtime_root = temp_path / "runtime"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            runtime_root.mkdir()
            workspaces_dir.mkdir()
            orchestrator.ROOT = repo_root

            async def fake_run_subprocess_intents(command, *, cwd, timeout_sec, progress_action=None):
                return subprocess.CompletedProcess(
                    command,
                    0,
                    json.dumps(
                        {
                            "status": "completed",
                            "summary": "Worker returned a legacy command.",
                            "filesTouched": [],
                            "validationIntents": [],
                            "validationCommands": ["scripts/docs/check-doc-consistency.ps1"],
                            "followUps": [],
                            "notes": [],
                        }
                    ),
                    "",
                )

            orchestrator.run_subprocess_async = fake_run_subprocess_intents
            try:
                agent = orchestrator.AgentDefinition(
                    name="analyst",
                    description="analysis",
                    prompt="Return JSON only.",
                    model_profile="simple",
                    effort="high",
                    permission_mode="dontAsk",
                    workspace_mode="repo",
                    context_mode="minimal",
                    timeout_sec=30,
                    allowed_tools=["Read"],
                    disallowed_tools=[],
                )
                task = orchestrator.TaskDefinition(
                    id="inspect-json",
                    title="Inspect JSON",
                    agent="analyst",
                    prompt="Inspect worker JSON output.",
                    workspace_mode="repo",
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="worker-intents-only-failure",
                    goal="Fail raw validationCommands under intents-only mode.",
                    shared_context=orchestrator.SharedContext(
                        summary="Worker intent-only failure test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[task],
                )

                record = asyncio.run(orchestrator.execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    {"analyst": agent},
                    task,
                    {},
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=False,
                    worker_validation_mode="intents-only",
                ))
                stderr_text = pathlib.Path(record.stderr_path).read_text(encoding="utf-8")
            finally:
                orchestrator.run_subprocess_async = old_run_subprocess_async
                orchestrator.ROOT = old_root

        self.assertEqual("failed", record.status)
        self.assertIn("must not include raw validationCommands", record.summary)
        self.assertIn("must not include raw validationCommands", stderr_text)

    def test_execute_task_fails_when_copy_mode_worker_mutates_live_repo(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_run_subprocess_async = orchestrator.run_subprocess_async
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            run_dir = temp_path / "run"
            runtime_root = temp_path / "runtime"
            workspaces_dir = temp_path / "workspaces"
            repo_root.mkdir()
            run_dir.mkdir()
            runtime_root.mkdir()
            workspaces_dir.mkdir()
            orchestrator.ROOT = repo_root

            async def fake_run_subprocess(command, *, cwd, timeout_sec, progress_action=None):
                (repo_root / "leaked.txt").write_text("leak", encoding="utf-8")
                return subprocess.CompletedProcess(
                    command,
                    0,
                    json.dumps(
                        {
                            "status": "completed",
                            "summary": "Done.",
                            "filesTouched": [],
                            "validationIntents": [],
                            "followUps": [],
                            "notes": [],
                        }
                    ),
                    "",
                )

            orchestrator.run_subprocess_async = fake_run_subprocess
            try:
                agent = orchestrator.AgentDefinition(
                    name="implementer",
                    description="implementation",
                    prompt="Return JSON only.",
                    model_profile="simple",
                    effort="high",
                    permission_mode="dontAsk",
                    workspace_mode="copy",
                    context_mode="minimal",
                    timeout_sec=30,
                    allowed_tools=["Read", "Write"],
                    disallowed_tools=[],
                )
                task = orchestrator.TaskDefinition(
                    id="isolated-write",
                    title="Isolated write",
                    agent="implementer",
                    prompt="Write only inside workspace.",
                    workspace_mode="copy",
                    write_paths=["out/**"],
                )
                plan = orchestrator.TaskPlan(
                    version=1,
                    name="repo-isolation-failure",
                    goal="Ensure copy mode cannot mutate repo root.",
                    shared_context=orchestrator.SharedContext(
                        summary="Repo isolation test.",
                        constraints=[],
                        read_paths=[],
                        validation=[],
                    ),
                    tasks=[task],
                )

                record = asyncio.run(orchestrator.execute_task(
                    run_dir,
                    runtime_root,
                    workspaces_dir,
                    plan,
                    {"implementer": agent},
                    task,
                    {},
                    claude_bin="claude",
                    agents_json="{}",
                    dry_run=False,
                ))
            finally:
                orchestrator.run_subprocess_async = old_run_subprocess_async
                orchestrator.ROOT = old_root

        self.assertEqual("failed", record.status)
        self.assertIn("Repository isolation violation", record.summary)
        self.assertIn("workspaceMode='copy'", record.summary)

    def test_run_loaded_plan_fail_fast_blocks_remaining_ready_tasks(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_fail = orchestrator.TaskDefinition(
                id="a-fail",
                title="A fails",
                agent="analyst",
                prompt="Fail.",
            )
            task_ready = orchestrator.TaskDefinition(
                id="c-ready",
                title="C ready",
                agent="analyst",
                prompt="Would have run next.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="fail-fast",
                goal="Stop scheduling after the first failure.",
                shared_context=orchestrator.SharedContext(
                    summary="Fail-fast test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_fail, task_ready],
            )
            executed_task_ids: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="failed",
                    summary="Worker failed.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["a-fail"], executed_task_ids)
        self.assertEqual({"failed": 1, "blocked": 1}, payload["statusCounts"])
        self.assertEqual("blocked", tasks_by_id["c-ready"]["status"])
        self.assertIn(
            "Coordinator stopped scheduling new tasks after a worker failure.",
            tasks_by_id["c-ready"]["summary"],
        )

    def test_run_loaded_plan_ignores_follow_up_tasks_by_default(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            implementer = orchestrator.AgentDefinition(
                name="implementer",
                description="implementation",
                prompt="Return JSON only.",
                model_profile="balanced",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read", "Write"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="follow-up-ignore",
                goal="Keep default follow-up behavior inert.",
                shared_context=orchestrator.SharedContext(
                    summary="Ignore follow-up tasks by default.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.follow_up_tasks = [
                    {
                        "id": "follow-up-task",
                        "title": "Follow-up task",
                        "agent": "implementer",
                        "prompt": "Implement the discovered fix.",
                    }
                ]
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst, "implementer": implementer},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["inspect-a"], [task_record["id"] for task_record in payload["tasks"]])
        self.assertEqual("ignore", payload["followUpBehavior"])
        self.assertEqual(
            [],
            [event for event in payload["events"] if event["phase"] == "task-injected"],
        )

    def test_run_loaded_plan_injects_follow_up_tasks_and_persists_selected_plan(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            implementer = orchestrator.AgentDefinition(
                name="implementer",
                description="implementation",
                prompt="Return JSON only.",
                model_profile="balanced",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read", "Write"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="follow-up-inject",
                goal="Inject follow-up tasks at runtime.",
                shared_context=orchestrator.SharedContext(
                    summary="Dynamic follow-up injection test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                if task.id == "inspect-a":
                    record.follow_up_tasks = [
                        {
                            "id": "implement-fix",
                            "title": "Implement fix",
                            "agent": "implementer",
                            "prompt": "Apply the discovered fix.",
                            "writePaths": ["docs/fix.md"],
                        }
                    ]
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst, "implementer": implementer},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    follow_up_behavior_override="inject",
                )
                selected_plan = json.loads(
                    (pathlib.Path(payload["runDir"]) / "selected-plan.json").read_text(encoding="utf-8")
                )
            finally:
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task_record["id"]: task_record for task_record in payload["tasks"]}
        self.assertEqual(["inspect-a", "implement-fix"], [task_record["id"] for task_record in payload["tasks"]])
        self.assertEqual("inject", payload["followUpBehavior"])
        self.assertEqual("inject", payload["followUpBehaviorOverride"])
        self.assertEqual("inspect-a", tasks_by_id["implement-fix"]["injected_from"])
        injected_events = [event for event in payload["events"] if event["phase"] == "task-injected"]
        self.assertEqual(["implement-fix"], [event["taskId"] for event in injected_events])
        selected_tasks = {task_payload["id"]: task_payload for task_payload in selected_plan["tasks"]}
        self.assertEqual("inspect-a", selected_tasks["implement-fix"]["injectedFrom"])
        self.assertEqual(["inspect-a"], selected_tasks["implement-fix"]["dependsOn"])

    def test_run_loaded_plan_stops_after_budget_limit_before_later_batch(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="inspect-b",
                title="Inspect B",
                agent="analyst",
                prompt="Inspect B.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="budget-stop",
                goal="Stop before the next batch after the run budget is reached.",
                shared_context=orchestrator.SharedContext(
                    summary="Budget stop test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    run_budget_usd=0.5,
                    budget_behavior="stop",
                ),
            )
            executed_task_ids: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed with spend.",
                    workspace_path=str(workspaces_dir / task.id),
                    usage={
                        "inputTokens": 10,
                        "outputTokens": 5,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.75,
                        "modelUsage": {},
                    },
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["inspect-a"], executed_task_ids)
        self.assertEqual({"completed": 1, "blocked": 1}, payload["statusCounts"])
        self.assertEqual({"runBudgetUsd": 0.5, "budgetBehavior": "stop"}, payload["runPolicy"])
        self.assertEqual("stop", payload["runGovernance"]["status"])
        self.assertEqual(1, payload["runGovernance"]["blockingAlertCount"])
        self.assertEqual("blocked", tasks_by_id["inspect-b"]["status"])
        self.assertIn("Run policy stop triggered", tasks_by_id["inspect-b"]["summary"])
        self.assertEqual("inspect-a", payload["runGovernance"]["highestCostTasks"][0]["taskId"])

    def test_run_loaded_plan_warns_on_budget_limit_and_continues(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(id="inspect-a", title="Inspect A", agent="analyst", prompt="A.")
            task_b = orchestrator.TaskDefinition(id="inspect-b", title="Inspect B", agent="analyst", prompt="B.")
            plan = orchestrator.TaskPlan(
                version=1,
                name="budget-warn",
                goal="Warn on budget and continue.",
                shared_context=orchestrator.SharedContext(
                    summary="Budget warn test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    run_budget_usd=0.5,
                    budget_behavior="warn",
                ),
            )
            executed_task_ids: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed.",
                    workspace_path=str(workspaces_dir / task.id),
                    usage={
                        "inputTokens": 10,
                        "outputTokens": 5,
                        "cacheReadInputTokens": 0,
                        "cacheCreationInputTokens": 0,
                        "totalCostUsd": 0.75 if task.id == "inspect-a" else 0.0,
                        "modelUsage": {},
                    },
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["inspect-a", "inspect-b"], executed_task_ids)
        self.assertEqual({"completed": 2}, payload["statusCounts"])
        self.assertEqual("warn", payload["runGovernance"]["status"])
        self.assertEqual(1, payload["runGovernance"]["alertCount"])
        self.assertEqual(0, payload["runGovernance"]["blockingAlertCount"])

    def test_run_loaded_plan_emits_event_trace_with_lineage(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="inspect-b",
                title="Inspect B",
                agent="analyst",
                prompt="Inspect B.",
                depends_on=["inspect-a"],
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="event-trace",
                goal="Emit run events.",
                shared_context=orchestrator.SharedContext(
                    summary="Event trace test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary=f"Completed {task.id}.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=2,
                    continue_on_error=False,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        phases = [event["phase"] for event in payload["events"]]
        self.assertEqual("run-start", phases[0])
        self.assertIn("batch-ready", phases)
        finished = [event for event in payload["events"] if event["phase"] == "task-finished"]
        self.assertEqual([], finished[0]["parentTaskIds"])
        self.assertEqual(["inspect-a"], finished[1]["parentTaskIds"])
        self.assertEqual("run-finished", phases[-1])

    def test_example_trace_multibatch_fixture_proves_event_and_branch_lineage(self):
        orchestrator = self.orchestrator
        root = pathlib.Path(__file__).resolve().parents[2]
        task_plan = root / "ai" / "orchestrator" / "tasks" / "example-trace-multibatch.json"
        agents = root / "ai" / "orchestrator" / "agents.json"
        with tempfile.TemporaryDirectory() as tempdir:
            payload = orchestrator.run_plan(
                SimpleNamespace(
                    agents=str(agents),
                    task_plan=str(task_plan),
                    selected_tasks=[],
                    claude_bin="claude",
                    runtime_root=tempdir,
                    max_parallel=2,
                    continue_on_error=False,
                    dry_run=True,
                    worker_validation_mode=None,
                )
            )

        batch_ready_events = [event for event in payload["events"] if event["phase"] == "batch-ready"]
        self.assertEqual(2, len(batch_ready_events))
        self.assertEqual(["inspect-trace-contract"], batch_ready_events[0]["taskIds"])
        self.assertEqual(
            ["inspect-evaluator-surface<-inspect-trace-contract", "inspect-status-surface<-inspect-trace-contract"],
            sorted(batch_ready_events[1]["branchContextIds"]),
        )
        self.assertEqual(3, payload["branchSummary"]["contextCount"])
        self.assertEqual(["inspect-trace-contract"], payload["branchSummary"]["rootContextIds"])
        self.assertEqual(
            ["inspect-evaluator-surface<-inspect-trace-contract", "inspect-status-surface<-inspect-trace-contract"],
            sorted(payload["branchSummary"]["leafContextIds"]),
        )

    def test_export_trace_writes_span_graph_for_events_and_checkpoints(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "trace-run"
            run_dir.mkdir(parents=True)
            manifest_path = run_dir / "manifest.json"
            review_dir = run_dir / "review"
            validation_dir = run_dir / "validation"
            promotion_dir = run_dir / "promotion"
            review_dir.mkdir()
            validation_dir.mkdir()
            promotion_dir.mkdir()
            review_summary_path = review_dir / "summary.json"
            validation_summary_path = validation_dir / "summary.json"
            promotion_summary_path = promotion_dir / "summary.json"
            review_summary_path.write_text("{}\n", encoding="utf-8")
            validation_summary_path.write_text("{}\n", encoding="utf-8")
            promotion_summary_path.write_text("{}\n", encoding="utf-8")
            workspace_a = runtime_root / "workspaces" / "trace-run" / "inspect-a" / "docs"
            workspace_b = runtime_root / "workspaces" / "trace-run" / "inspect-b" / "docs"
            workspace_a.mkdir(parents=True)
            workspace_b.mkdir(parents=True)
            repo_docs = pathlib.Path(__file__).resolve().parents[2] / "docs"
            repo_docs.mkdir(exist_ok=True)
            (workspace_a / "a.md").write_text("trace a\n", encoding="utf-8")
            (workspace_b / "b.md").write_text("trace b\n", encoding="utf-8")

            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="inspect-b",
                title="Inspect B",
                agent="analyst",
                prompt="Inspect B.",
                depends_on=["inspect-a"],
            )
            record_a = make_task_run_record(
                orchestrator,
                task_a,
                status="completed",
                summary="Completed A.",
                workspace_path=str(runtime_root / "workspaces" / "trace-run" / "inspect-a"),
                files_touched=["docs/a.md"],
                actual_files_touched=["docs/a.md"],
                usage={
                    "inputTokens": 12,
                    "outputTokens": 5,
                    "cacheReadInputTokens": 2,
                    "totalCostUsd": 0.07,
                },
            )
            record_b = make_task_run_record(
                orchestrator,
                task_b,
                status="completed",
                summary="Completed B.",
                branch_context_id="inspect-b<-inspect-a",
                branch_parent_context_ids=["inspect-a"],
                workspace_path=str(runtime_root / "workspaces" / "trace-run" / "inspect-b"),
                files_touched=["docs/b.md"],
                actual_files_touched=["docs/b.md"],
                usage={
                    "inputTokens": 18,
                    "outputTokens": 9,
                    "cacheReadInputTokens": 4,
                    "totalCostUsd": 0.11,
                },
            )
            manifest = {
                "runId": "trace-run",
                "generatedAt": "2026-05-01T10:00:00+00:00",
                "dryRun": False,
                "plan": {
                    "name": "trace-export",
                    "goal": "Export trace spans.",
                    "taskIds": ["inspect-a", "inspect-b"],
                },
                "usageTotals": {},
                "runGovernance": {"status": "ok", "alertCount": 0, "blockingAlertCount": 0, "artifactTotals": {"totalBytes": 0}},
                "topology": {"batchCount": 2, "maxParallelWidth": 1, "warningCount": 0},
                "events": [
                    {"ts": "2026-05-01T10:00:00+00:00", "phase": "run-start"},
                    {
                        "ts": "2026-05-01T10:00:01+00:00",
                        "phase": "batch-ready",
                        "taskIds": ["inspect-a"],
                        "branchContextIds": ["inspect-a"],
                        "details": {"pendingTaskIds": ["inspect-a", "inspect-b"]},
                    },
                    {
                        "ts": "2026-05-01T10:00:02+00:00",
                        "phase": "task-finished",
                        "taskId": "inspect-a",
                        "parentTaskIds": [],
                        "branchContextId": "inspect-a",
                        "status": "completed",
                    },
                    {
                        "ts": "2026-05-01T10:00:03+00:00",
                        "phase": "batch-ready",
                        "taskIds": ["inspect-b"],
                        "branchContextIds": ["inspect-b<-inspect-a"],
                        "details": {"pendingTaskIds": ["inspect-b"]},
                    },
                    {
                        "ts": "2026-05-01T10:00:04+00:00",
                        "phase": "task-finished",
                        "taskId": "inspect-b",
                        "parentTaskIds": ["inspect-a"],
                        "branchContextId": "inspect-b<-inspect-a",
                        "status": "completed",
                    },
                    {"ts": "2026-05-01T10:00:05+00:00", "phase": "run-finished"},
                ],
                "coordinatorReview": {
                    "runId": "trace-run",
                    "taskCount": 2,
                    "summary": {"changedTaskCount": 2, "changedFileCount": 2},
                    "summaryPath": str(review_summary_path),
                },
                "coordinatorValidation": {
                    "generatedAt": "2026-05-01T10:00:06+00:00",
                    "runId": "trace-run",
                    "executionScope": "repo",
                    "dryRun": False,
                    "commandCount": 1,
                    "acceptedCommandCount": 1,
                    "rejectedCommandCount": 0,
                    "selectedTaskIds": ["inspect-a", "inspect-b"],
                    "allPassed": True,
                    "summaryPath": str(validation_summary_path),
                },
                "coordinatorPromotion": {
                    "runId": "trace-run",
                    "dryRun": True,
                    "promotionAllowed": True,
                    "filesPromotable": 2,
                    "filesPromoted": 0,
                    "promotableTaskIds": ["inspect-a", "inspect-b"],
                    "blockedReasons": [],
                    "summaryPath": str(promotion_summary_path),
                },
                "tasks": {
                    "inspect-a": asdict(record_a),
                    "inspect-b": asdict(record_b),
                },
            }
            manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

            payload = orchestrator.export_trace(
                SimpleNamespace(
                    run_ref=str(manifest_path),
                    selected_tasks=[],
                    out="",
                    dry_run=False,
                    json=True,
                )
            )

            trace_path = pathlib.Path(payload["tracePath"])
            self.assertTrue(trace_path.exists())
            self.assertEqual("pojo-lens-orchestrator-trace/v1", payload["traceFormat"])
            self.assertEqual({"run": 1, "batch": 2, "task": 2, "validation": 1, "approval": 2}, payload["kindCounts"])
            spans_by_id = {span["id"]: span for span in payload["spans"]}
            self.assertEqual(["run:trace-run"], spans_by_id["batch:trace-run:1"]["parentSpanIds"])
            self.assertEqual(
                ["batch:trace-run:2", "task:trace-run:inspect-a"],
                spans_by_id["task:trace-run:inspect-b"]["parentSpanIds"],
            )
            self.assertEqual("passed", spans_by_id["validation:trace-run"]["status"])
            self.assertEqual("allowed", spans_by_id["approval:trace-run:promotion"]["status"])
            self.assertEqual(
                ["inspect-a"],
                spans_by_id["task:trace-run:inspect-b"]["attributes"]["dependencyTaskIds"],
            )
            self.assertEqual("standard", spans_by_id["task:trace-run:inspect-a"]["attributes"]["outputProfile"])
            self.assertEqual(0.11, spans_by_id["task:trace-run:inspect-b"]["attributes"]["usage.totalCostUsd"])
            self.assertEqual(4, spans_by_id["task:trace-run:inspect-b"]["attributes"]["usage.cacheReadTokens"])

    def test_run_loaded_plan_reports_dry_run_otel_summary_without_emitting(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="otel-dry-run",
                goal="Show OTEL summary without emission during dry-run.",
                shared_context=orchestrator.SharedContext(
                    summary="OTEL dry-run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    otel_endpoint="http://collector:4318/v1/traces",
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual(
            {
                "enabled": True,
                "endpoint": "http://collector:4318/v1/traces",
                "emitted": False,
                "reason": "dry-run",
            },
            payload["otel"],
        )

    def test_run_plan_estimate_returns_cost_estimate_without_creating_run(self):
        orchestrator = self.orchestrator
        old_run_loaded_plan = orchestrator.run_loaded_plan
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "balanced",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read", "Edit"],
                                "timeoutSec": 30,
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "estimate-only",
                        "goal": "Return a pre-flight estimate without scheduling.",
                        "sharedContext": {
                            "summary": "Estimate path test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "implement",
                                "title": "Implement",
                                "agent": "implementer",
                                "prompt": "Implement the requested change.",
                                "writePaths": ["CHANGELOG.md"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            def fail_run_loaded_plan(*args, **kwargs):
                raise AssertionError("run_loaded_plan should not be called for --estimate")

            orchestrator.run_loaded_plan = fail_run_loaded_plan
            try:
                payload = orchestrator.run_plan(
                    SimpleNamespace(
                        agents=str(agents_path),
                        task_plan=str(plan_path),
                        claude_bin="claude",
                        runtime_root=str(runtime_root),
                        max_parallel=2,
                        continue_on_error=False,
                        dry_run=False,
                        worker_validation_mode="",
                        effort="",
                        selected_tasks=[],
                        max_task_retries=None,
                        hitl=False,
                        hitl_mode="batch",
                        hitl_auto_approve=False,
                        otel_endpoint="",
                        estimate=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan

        self.assertTrue(payload["estimatedOnly"])
        self.assertTrue(payload["dryRun"])
        self.assertNotIn("runDir", payload)
        self.assertEqual(1, payload["taskCount"])
        self.assertGreater(payload["costEstimate"]["totals"]["minUsd"], 0.0)
        self.assertEqual("heuristic", payload["costEstimate"]["estimateMode"])

    def test_run_plan_passes_tui_flag_to_loaded_plan(self):
        orchestrator = self.orchestrator
        old_run_loaded_plan = orchestrator.run_loaded_plan
        captured: dict[str, object] = {}
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "agents": {
                            "planner": {
                                "description": "planning",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read"],
                                "timeoutSec": 30,
                            },
                            "implementer": {
                                "description": "implementation",
                                "prompt": "Return JSON only.",
                                "modelProfile": "simple",
                                "workspaceMode": "copy",
                                "contextMode": "minimal",
                                "permissionMode": "dontAsk",
                                "allowedTools": ["Read", "Edit"],
                                "timeoutSec": 30,
                            }
                        },
                    }
                ),
                encoding="utf-8",
            )
            plan_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "name": "tui-pass-through",
                        "goal": "Verify TUI flag propagation.",
                        "sharedContext": {
                            "summary": "TUI pass-through test.",
                            "constraints": [],
                            "readPaths": [],
                            "validation": [],
                        },
                        "tasks": [
                            {
                                "id": "implement",
                                "title": "Implement",
                                "agent": "implementer",
                                "prompt": "Implement the requested change.",
                                "writePaths": ["CHANGELOG.md"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            def fake_run_loaded_plan(*args, **kwargs):
                captured.update(kwargs)
                return {"runId": "run-x", "statusCounts": {"planned": 1}}

            orchestrator.run_loaded_plan = fake_run_loaded_plan
            try:
                orchestrator.run_plan(
                    SimpleNamespace(
                        agents=str(agents_path),
                        task_plan=str(plan_path),
                        claude_bin="claude",
                        runtime_root=str(runtime_root),
                        max_parallel=2,
                        continue_on_error=False,
                        dry_run=True,
                        worker_validation_mode="",
                        effort="",
                        selected_tasks=[],
                        max_task_retries=None,
                        hitl=False,
                        hitl_mode="batch",
                        hitl_auto_approve=False,
                        otel_endpoint="",
                        estimate=False,
                        follow_up_mode="",
                        reuse_unchanged=False,
                        watch=False,
                        tui=True,
                    )
                )
            finally:
                orchestrator.run_loaded_plan = old_run_loaded_plan

        self.assertTrue(captured["tui"])

    def test_run_loaded_plan_stops_after_artifact_limit_before_later_batch(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(id="inspect-a", title="Inspect A", agent="analyst", prompt="A.")
            task_b = orchestrator.TaskDefinition(id="inspect-b", title="Inspect B", agent="analyst", prompt="B.")
            plan = orchestrator.TaskPlan(
                version=1,
                name="artifact-stop",
                goal="Stop before the next batch when task artifacts are too large.",
                shared_context=orchestrator.SharedContext(
                    summary="Artifact stop test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
                run_policy=orchestrator.RunPolicy(
                    max_task_stdout_bytes=10,
                    artifact_behavior="stop",
                ),
            )
            executed_task_ids: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                executed_task_ids.append(task.id)
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="completed",
                    summary="Completed with large stdout.",
                    workspace_path=str(workspaces_dir / task.id),
                    stdout_bytes=32 if task.id == "inspect-a" else 0,
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual(["inspect-a"], executed_task_ids)
        self.assertEqual("stop", payload["runGovernance"]["status"])
        self.assertEqual(32, payload["runGovernance"]["artifactTotals"]["totalBytes"])
        self.assertEqual("inspect-a", payload["runGovernance"]["artifactTotals"]["largestTasks"][0]["taskId"])
        self.assertEqual("blocked", tasks_by_id["inspect-b"]["status"])
        self.assertIn("Run policy stop triggered", tasks_by_id["inspect-b"]["summary"])

    def test_run_loaded_plan_blocks_dependents_after_failed_dependency(self):
        orchestrator = self.orchestrator
        old_ensure_claude_available = orchestrator.ensure_claude_available
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            agent = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_fail = orchestrator.TaskDefinition(
                id="a-fail",
                title="A fails",
                agent="analyst",
                prompt="Fail.",
            )
            task_blocked = orchestrator.TaskDefinition(
                id="b-needs-a",
                title="B needs A",
                agent="analyst",
                prompt="Wait for A.",
                depends_on=["a-fail"],
            )
            task_independent = orchestrator.TaskDefinition(
                id="c-independent",
                title="C independent",
                agent="analyst",
                prompt="Can still run.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="dependency-blocking",
                goal="Block dependents while letting independent tasks continue.",
                shared_context=orchestrator.SharedContext(
                    summary="Dependency blocking test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_fail, task_blocked, task_independent],
            )
            executed_task_ids: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                executed_task_ids.append(task.id)
                status = "failed" if task.id == "a-fail" else "completed"
                summary = "Upstream failed." if task.id == "a-fail" else "Independent task completed."
                return make_task_run_record(
                    orchestrator,
                    task,
                    status=status,
                    summary=summary,
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ensure_claude_available = lambda claude_bin: None
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": agent},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=2,
                    continue_on_error=True,
                    dry_run=False,
                )
            finally:
                orchestrator.ensure_claude_available = old_ensure_claude_available
                orchestrator.execute_task = old_execute_task

        tasks_by_id = {task["id"]: task for task in payload["tasks"]}
        self.assertEqual({"a-fail", "c-independent"}, set(executed_task_ids))
        self.assertEqual({"failed": 1, "blocked": 1, "completed": 1}, payload["statusCounts"])
        self.assertEqual("blocked", tasks_by_id["b-needs-a"]["status"])
        self.assertIn("Dependency failed or was blocked.", tasks_by_id["b-needs-a"]["summary"])
        self.assertEqual("completed", tasks_by_id["c-independent"]["status"])

    def test_run_loaded_plan_uses_tracked_worker_validation_modes(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                worker_validation_mode="intents-only",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            reviewer = orchestrator.AgentDefinition(
                name="reviewer",
                description="review",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task_a = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            task_b = orchestrator.TaskDefinition(
                id="review-b",
                title="Review B",
                agent="reviewer",
                prompt="Review B.",
                worker_validation_mode="intents-only",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="tracked-worker-validation-mode",
                goal="Use tracked validation modes.",
                shared_context=orchestrator.SharedContext(
                    summary="Tracked mode test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task_a, task_b],
            )
            seen_modes: dict[str, str] = {}

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                seen_modes[task.id] = str(worker_validation_mode)
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.worker_validation_mode = orchestrator.resolved_worker_validation_mode(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                record.worker_validation_mode_source = orchestrator.resolved_worker_validation_mode_source(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst, "reviewer": reviewer},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=2,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual({"inspect-a": "None", "review-b": "None"}, seen_modes)
        self.assertEqual("intents-only", payload["workerValidationMode"])
        self.assertIsNone(payload["workerValidationModeOverride"])
        self.assertEqual(
            {"inspect-a": "intents-only", "review-b": "intents-only"},
            payload["taskWorkerValidationModes"],
        )
        self.assertEqual(
            {"inspect-a": "agent", "review-b": "task"},
            payload["taskWorkerValidationModeSources"],
        )
        self.assertEqual(
            {"inspect-a": "standard", "review-b": "standard"},
            payload["taskOutputProfiles"],
        )
        self.assertEqual(
            {"inspect-a": "agent", "review-b": "agent"},
            payload["taskOutputProfileSources"],
        )
        self.assertEqual(
            ["agent", "task"],
            [task["worker_validation_mode_source"] for task in payload["tasks"]],
        )

    def test_run_loaded_plan_worker_validation_override_beats_tracked_modes(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                worker_validation_mode="intents-only",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
                worker_validation_mode="intents-only",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="override-worker-validation-mode",
                goal="Override tracked validation modes.",
                shared_context=orchestrator.SharedContext(
                    summary="Override mode test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )
            seen_modes: list[str] = []

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                seen_modes.append(str(worker_validation_mode))
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.worker_validation_mode = orchestrator.resolved_worker_validation_mode(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                record.worker_validation_mode_source = orchestrator.resolved_worker_validation_mode_source(
                    task,
                    agents[task.agent],
                    run_override=worker_validation_mode,
                )
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    worker_validation_mode="intents-only",
                )
            finally:
                orchestrator.execute_task = old_execute_task

        self.assertEqual(["intents-only"], seen_modes)
        self.assertEqual("intents-only", payload["workerValidationMode"])
        self.assertEqual("intents-only", payload["workerValidationModeOverride"])
        self.assertEqual({"inspect-a": "intents-only"}, payload["taskWorkerValidationModes"])
        self.assertEqual({"inspect-a": "override"}, payload["taskWorkerValidationModeSources"])
        self.assertEqual({"inspect-a": "standard"}, payload["taskOutputProfiles"])
        self.assertEqual({"inspect-a": "agent"}, payload["taskOutputProfileSources"])
        self.assertEqual(
            {"inspect-a": orchestrator.MODEL_PROFILE_TO_MODEL["simple"]},
            payload["taskModels"],
        )
        self.assertEqual({"inspect-a": "simple"}, payload["taskModelProfiles"])
        self.assertEqual([], payload["complexModelTaskIds"])
        self.assertEqual(0, payload["complexModelTaskCount"])
        self.assertEqual("override", payload["tasks"][0]["worker_validation_mode_source"])

    def test_run_loaded_plan_reports_complex_model_tasks_in_payload_and_manifest(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="balanced",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="deep-design",
                title="Deep design",
                agent="analyst",
                prompt="Handle the hardest architecture slice.",
                model_profile="complex",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="complex-model-run-visibility",
                goal="Expose complex model tasks in run output.",
                shared_context=orchestrator.SharedContext(
                    summary="Complex model run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                record = make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )
                record.model = orchestrator.resolved_model(task, agents[task.agent])
                record.model_profile = orchestrator.resolved_model_profile(task, agents[task.agent])
                return record

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest = json.loads(
                (pathlib.Path(payload["runDir"]) / "manifest.json").read_text(encoding="utf-8")
            )

        self.assertEqual(
            {"deep-design": orchestrator.MODEL_PROFILE_TO_MODEL["complex"]},
            payload["taskModels"],
        )
        self.assertEqual({"deep-design": "complex"}, payload["taskModelProfiles"])
        self.assertEqual(["deep-design"], payload["complexModelTaskIds"])
        self.assertEqual(1, payload["complexModelTaskCount"])
        self.assertEqual(
            {"deep-design": orchestrator.MODEL_PROFILE_TO_MODEL["complex"]},
            manifest["taskModels"],
        )
        self.assertEqual({"deep-design": "complex"}, manifest["taskModelProfiles"])
        self.assertEqual(["deep-design"], manifest["complexModelTaskIds"])
        self.assertEqual(1, manifest["complexModelTaskCount"])
        self.assertIn("costEstimate", payload)
        self.assertIn("costEstimate", manifest)
        self.assertEqual(1, payload["costEstimate"]["totals"]["taskCount"])
        self.assertEqual(1, manifest["costEstimate"]["totals"]["taskCount"])

    def test_run_loaded_plan_and_summary_report_topology(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="topology-run-visibility",
                goal="Expose topology in run output.",
                shared_context=orchestrator.SharedContext(
                    summary="Topology run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest_path = pathlib.Path(payload["runDir"]) / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            summary, _ = orchestrator.summarize_run_manifest(manifest_path, manifest)

        self.assertEqual(1, payload["topology"]["taskCount"])
        self.assertEqual({"analyst": 1}, payload["topology"]["agentCounts"])
        self.assertEqual(1, payload["topology"]["batchCount"])
        self.assertEqual(1, payload["topology"]["maxParallelWidth"])
        self.assertEqual(0, payload["topology"]["warningCount"])
        self.assertEqual(1, manifest["topology"]["batchCount"])
        self.assertEqual({"inspect-a": 0}, manifest["topology"]["taskDependencyHops"])
        self.assertEqual(1, summary["topologyBatchCount"])
        self.assertEqual(1, summary["topologyMaxParallelWidth"])
        self.assertEqual(0, summary["topologyWarningCount"])
        self.assertEqual({"standard": 1}, summary["outputProfileCounts"])
        self.assertEqual([], summary["unexpectedlyVerboseTaskIds"])

    def test_run_loaded_plan_can_reuse_existing_run_directories_without_overwriting_snapshot(self):
        orchestrator = self.orchestrator
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            run_dir = runtime_root / "runs" / "same-run"
            workspaces_dir = runtime_root / "workspaces" / "same-run"
            run_dir.mkdir(parents=True)
            workspaces_dir.mkdir(parents=True)
            selected_plan_path = run_dir / "selected-plan.json"
            selected_plan_path.write_text("{\"sentinel\":true}\n", encoding="utf-8")
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="same-run-reuse",
                goal="Reuse an existing run directory.",
                shared_context=orchestrator.SharedContext(
                    summary="Existing run test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    existing_run_id="same-run",
                    existing_run_dir=run_dir,
                    existing_workspaces_dir=workspaces_dir,
                    write_plan_snapshot=False,
                )
            finally:
                orchestrator.execute_task = old_execute_task

            manifest = orchestrator.read_json(run_dir / "manifest.json")
            selected_plan_text = selected_plan_path.read_text(encoding="utf-8")

        self.assertEqual("same-run", payload["runId"])
        self.assertEqual(str(run_dir.resolve()), payload["runDir"])
        self.assertEqual(str(workspaces_dir.resolve()), payload["workspacesDir"])
        self.assertEqual("{\"sentinel\":true}\n", selected_plan_text)
        self.assertEqual("same-run", manifest["runId"])
        self.assertEqual(str(run_dir.resolve()), manifest["runDir"])
        self.assertEqual(str(workspaces_dir.resolve()), manifest["workspacesDir"])

    def test_run_loaded_plan_uses_external_workspaces_dir_by_default(self):
        orchestrator = self.orchestrator
        old_root = orchestrator.ROOT
        old_execute_task = orchestrator.execute_task
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            repo_root = temp_path / "repo"
            runtime_root = temp_path / "runtime"
            repo_root.mkdir()
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="external-workspaces",
                goal="Use external workspaces by default.",
                shared_context=orchestrator.SharedContext(
                    summary="External workspace test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            async def fake_execute_task(
                run_dir,
                runtime_root,
                workspaces_dir,
                plan,
                agents,
                task,
                dependency_records,
                *,
                claude_bin,
                agents_json,
                dry_run,
                worker_validation_mode=None,
                effort_override=None,
            ):
                return make_task_run_record(
                    orchestrator,
                    task,
                    status="planned",
                    summary="Dry run only; Claude was not invoked.",
                    workspace_path=str(workspaces_dir / task.id),
                )

            orchestrator.ROOT = repo_root
            orchestrator.execute_task = fake_execute_task
            try:
                payload = orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                )
            finally:
                orchestrator.execute_task = old_execute_task
                orchestrator.ROOT = old_root

            workspaces_dir = pathlib.Path(payload["workspacesDir"]).resolve()
            manifest = orchestrator.read_json(pathlib.Path(payload["runDir"]) / "manifest.json")

        self.assertTrue(workspaces_dir.name.startswith(payload["runId"]))
        self.assertFalse(str(workspaces_dir).startswith(str(repo_root.resolve())))
        self.assertNotEqual((runtime_root / "workspaces" / payload["runId"]).resolve(), workspaces_dir)
        self.assertEqual(str(workspaces_dir), manifest["workspacesDir"])

    def test_run_loaded_plan_rejects_compat_worker_validation_override(self):
        orchestrator = self.orchestrator
        with tempfile.TemporaryDirectory() as tempdir:
            temp_path = pathlib.Path(tempdir)
            runtime_root = temp_path / "runtime"
            runtime_root.mkdir()
            agents_path = temp_path / "agents.json"
            plan_path = temp_path / "plan.json"
            agents_path.write_text("{}", encoding="utf-8")
            plan_path.write_text("{}", encoding="utf-8")
            analyst = orchestrator.AgentDefinition(
                name="analyst",
                description="analysis",
                prompt="Return JSON only.",
                model_profile="simple",
                effort="high",
                permission_mode="dontAsk",
                workspace_mode="copy",
                context_mode="minimal",
                timeout_sec=30,
                allowed_tools=["Read"],
                disallowed_tools=[],
            )
            task = orchestrator.TaskDefinition(
                id="inspect-a",
                title="Inspect A",
                agent="analyst",
                prompt="Inspect A.",
            )
            plan = orchestrator.TaskPlan(
                version=1,
                name="reject-compat-run",
                goal="Reject compat workers before execution.",
                shared_context=orchestrator.SharedContext(
                    summary="Compat rejection test.",
                    constraints=[],
                    read_paths=[],
                    validation=[],
                ),
                tasks=[task],
            )

            with self.assertRaisesRegex(
                orchestrator.OrchestratorError,
                "workerValidationMode='compat' was removed from the live worker contract",
            ):
                orchestrator.run_loaded_plan(
                    plan_path,
                    agents_path,
                    {"analyst": analyst},
                    plan,
                    claude_bin="claude",
                    runtime_root=runtime_root,
                    max_parallel=1,
                    continue_on_error=False,
                    dry_run=True,
                    worker_validation_mode="compat",
                )


if __name__ == "__main__":
    unittest.main()
