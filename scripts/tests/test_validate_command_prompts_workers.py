import pathlib
import unittest
from types import SimpleNamespace

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandPromptsWorkersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_intents_only_output_contract_lives_in_agent_prompt(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt=(
                "Return JSON only. Emit only structured `validationIntents`. "
                "Use `[]` for known-empty `filesTouched`."
            ),
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
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            validation=["mvn -pl pojo-lens -Dtest=PojoLensCsvTest test"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="intent-only-worker-prompt",
            goal="Keep validation suggestions intent-only.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        agent_payload = orchestrator.agent_payload_for_claude(
            {"analyst": agent},
            selected_names=["analyst"],
        )

        self.assertNotIn("Emit only structured `validationIntents`", rendered.text)
        self.assertNotIn("Use `[]` for known-empty `filesTouched`", rendered.text)
        self.assertIn("Emit only structured `validationIntents`", agent_payload)
        self.assertIn("Use `[]` for known-empty `filesTouched`", agent_payload)

    def test_worker_prompt_minimal_mode_uses_task_local_read_paths_only(self):
        orchestrator = self.orchestrator
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
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            read_paths=["task-only.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="minimal-read-context",
            goal="Keep minimal worker prompts task-local.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=["shared.md"],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("`task-only.md`", rendered.text)
        self.assertNotIn("`shared.md`", rendered.text)

    def test_worker_prompt_full_mode_includes_shared_read_paths(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="analyst",
            description="analysis",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="high",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="full",
            timeout_sec=30,
            allowed_tools=["Read"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            read_paths=["task-only.md"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="full-read-context",
            goal="Keep full worker prompts inclusive.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=["shared.md"],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("`task-only.md`", rendered.text)
        self.assertIn("`shared.md`", rendered.text)

    def test_worker_prompt_execution_context_omits_absolute_workspace_paths(self):
        orchestrator = self.orchestrator
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
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            validation=["mvn -pl pojo-lens -Dtest=PojoLensCsvTest test"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="execution-context-shape",
            goal="Keep execution context cache-friendly.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )
        workspace = pathlib.Path("C:/tmp/workspace")

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            workspace,
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("Current working directory: isolated task workspace", rendered.text)
        self.assertNotIn(str(workspace), rendered.text)
        self.assertIn("Contract:", rendered.text)

    def test_worker_prompt_rules_fit_without_truncation(self):
        orchestrator = self.orchestrator
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
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
            validation=["mvn -pl pojo-lens -Dtest=PojoLensCsvTest test"],
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="worker-rules-shape",
            goal="Keep worker rules compact and complete.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        worker_rules = next(
            section for section in rendered.sections if section.name == "worker_rules"
        )
        self.assertFalse(worker_rules.truncated)
        self.assertIn("Treat this prompt plus the declared workspace as the full contract.", rendered.text)
        self.assertLessEqual(worker_rules.item_count, 16)
        self.assertIn("Treat `writePaths` as the edit contract.", rendered.text)
        self.assertIn("record the exact rule in `notes`", rendered.text)
        self.assertIn("derive assertions from the implementation and written contract", rendered.text)
        self.assertIn("update matching README/docs in scope", rendered.text)
        self.assertIn("mirror that exact entrypoint and args", rendered.text)
        self.assertIn("Do not swap `mvn` and `mvnw` or invent alternate wrappers.", rendered.text)
        self.assertIn("`repo-script` for `scripts/...` or `mvnw(.cmd)`", rendered.text)
        self.assertIn("Do not invent scripts or use `grep`, `findstr`, or shell fragments.", rendered.text)
        self.assertIn(
            "`mvn -pl pojo-lens -Dtest=PojoLensCsvTest test` -> `tool` `mvn` (mirror exactly; keep args unchanged)",
            rendered.text,
        )
        self.assertNotIn("Emit only structured `validationIntents`", rendered.text)
        self.assertNotIn("Use `[]` for known-empty", rendered.text)

    def test_worker_prompt_includes_lean_output_discipline(self):
        orchestrator = self.orchestrator
        agent = orchestrator.AgentDefinition(
            name="docs-implementer",
            description="docs implementation",
            prompt="Return JSON only.",
            model_profile="simple",
            effort="low",
            permission_mode="dontAsk",
            workspace_mode="copy",
            context_mode="minimal",
            output_profile="lean",
            timeout_sec=30,
            allowed_tools=["Read", "Edit"],
            disallowed_tools=[],
        )
        task = orchestrator.TaskDefinition(
            id="tighten-docs",
            title="Tighten docs",
            agent="docs-implementer",
            prompt="Tighten the docs.",
        )
        plan = orchestrator.TaskPlan(
            version=1,
            name="lean-output-profile",
            goal="Keep docs output terse.",
            shared_context=orchestrator.SharedContext(
                summary="Prompt test.",
                constraints=[],
                read_paths=[],
                validation=[],
            ),
            tasks=[task],
        )

        rendered = orchestrator.worker_prompt(
            plan,
            task,
            agent,
            "copy",
            pathlib.Path("C:/tmp/workspace"),
            "- none",
            worker_validation_mode="intents-only",
        )

        self.assertIn("Resolved profile: `lean`", rendered.text)
        self.assertIn("keep `summary` to one short sentence", rendered.text)
        self.assertIn("at most one validation intent", rendered.text)

    def test_coerce_worker_result_compacts_verbose_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Summary " * 80,
            "filesTouched": ["src/main/App.java", "src/main/App.java"],
            "validationIntents": [
                {
                    "kind": "repo-script",
                    "entrypoint": "scripts/docs/check-doc-consistency.ps1",
                },
                {
                    "kind": "tool",
                    "entrypoint": "mvn",
                    "args": ["-q", "test"],
                },
                {
                    "kind": "tool",
                    "entrypoint": "py",
                    "args": ["-3", "-m", "unittest"],
                },
            ],
            "followUps": [
                "First follow-up item.",
                "Second follow-up item.",
                "Third follow-up item.",
                "Fourth follow-up item should be dropped.",
            ],
            "notes": [
                "Note one.",
                "Note two.",
                "Note three.",
                "Note four.",
                "Note five.",
                "Note six should be dropped.",
            ],
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual("completed", result["status"])
        self.assertLessEqual(len(result["summary"]), orchestrator.MAX_WORKER_SUMMARY_CHARS)
        self.assertEqual(["src/main/App.java"], result["filesTouched"])
        self.assertEqual(2, len(result["validationIntents"]))
        self.assertEqual([], result["validationCommands"])
        self.assertEqual(3, len(result["followUps"]))
        self.assertEqual(5, len(result["notes"]))

    def test_coerce_worker_result_uses_tighter_lean_limits(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Summary " * 80,
            "filesTouched": ["docs/guide.md"],
            "validationIntents": [
                {
                    "kind": "repo-script",
                    "entrypoint": "scripts/docs/check-doc-consistency.ps1",
                },
                {
                    "kind": "tool",
                    "entrypoint": "py",
                    "args": ["-3", "-m", "unittest"],
                },
            ],
            "followUps": [
                "First follow-up item.",
                "Second follow-up item should be dropped.",
            ],
            "notes": [
                "Note one.",
                "Note two.",
                "Note three should be dropped.",
            ],
        }

        result = orchestrator.coerce_worker_result(payload, output_profile="lean")

        self.assertLessEqual(len(result["summary"]), orchestrator.LEAN_MAX_WORKER_SUMMARY_CHARS)
        self.assertEqual(1, len(result["validationIntents"]))
        self.assertEqual(1, len(result["followUps"]))
        self.assertEqual(2, len(result["notes"]))

    def test_coerce_worker_result_preserves_unknown_null_fields(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "blocked",
            "summary": "A tool failure left some fields unknown.",
            "filesTouched": None,
            "validationIntents": [],
            "followUps": ["Retry after the tool comes back."],
            "notes": None,
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual([], result["filesTouched"])
        self.assertEqual([], result["validationCommands"])
        self.assertEqual(["Retry after the tool comes back."], result["followUps"])
        self.assertEqual([], result["notes"])
        self.assertEqual(
            ["filesTouched", "notes"],
            result["unknownFields"],
        )

    def test_coerce_worker_result_accepts_structured_validation_intents(self):
        orchestrator = self.orchestrator
        payload = {
            "status": "completed",
            "summary": "Validation suggestions are structured.",
            "filesTouched": [],
            "validationIntents": [
                {
                    "kind": "repo-script",
                    "entrypoint": "scripts/docs/check-doc-consistency.ps1",
                },
                {
                    "kind": "tool",
                    "entrypoint": "mvn",
                    "args": ["-q", "test"],
                },
            ],
            "followUps": [],
            "notes": [],
        }

        result = orchestrator.coerce_worker_result(payload)

        self.assertEqual(2, len(result["validationIntents"]))
        self.assertEqual("repo-script", result["validationIntents"][0]["kind"])
        self.assertEqual("mvn", result["validationIntents"][1]["entrypoint"])

    def test_task_output_schema_json_requires_validation_intents_only(self):
        orchestrator = self.orchestrator

        schema = orchestrator.task_output_schema_json("intents-only")
        import json as _json
        schema = _json.loads(schema)

        self.assertIn("validationIntents", schema["required"])
        self.assertNotIn("validationCommands", schema["properties"])

    def test_coerce_worker_result_rejects_invalid_status(self):
        orchestrator = self.orchestrator

        with self.assertRaisesRegex(orchestrator.OrchestratorError, "field 'status' must be one of"):
            orchestrator.coerce_worker_result(
                {
                    "status": "planned",
                    "summary": "bad status",
                    "filesTouched": [],
                    "validationIntents": [],
                    "followUps": [],
                    "notes": [],
                }
            )


if __name__ == "__main__":
    unittest.main()
