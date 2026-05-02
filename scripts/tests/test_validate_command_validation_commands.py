import unittest
from unittest import mock

from scripts.tests.test_claude_orchestrator_helpers import (
    load_orchestrator_module,
    make_task_run_record,
)


class ValidateCommandValidationCommandsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.orchestrator = load_orchestrator_module()

    def test_validation_command_policy_rejects_shell_composition(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_command_policy(
            "grep -n 'foo' ai/orchestrator/README.md | grep bar"
        )

        self.assertFalse(policy["accepted"])
        self.assertIn("shell composition", policy["reason"])

    def test_validation_command_policy_accepts_repo_script(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_command_policy("scripts/ai/refresh-ai-memory.ps1 -Check")

        self.assertTrue(policy["accepted"])
        self.assertEqual("scripts/ai/refresh-ai-memory.ps1", policy["entrypoint"])
        self.assertEqual("repo-script", policy["intent"]["kind"])

    def test_validation_intent_policy_accepts_repo_script(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_intent_policy(
            orchestrator.ValidationIntent(
                kind="repo-script",
                entrypoint="scripts/docs/check-doc-consistency.ps1",
                args=[],
            )
        )

        self.assertTrue(policy["accepted"])
        self.assertEqual("scripts/docs/check-doc-consistency.ps1", policy["entrypoint"])

    def test_validation_intent_policy_rejects_unknown_tool(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_intent_policy(
            orchestrator.ValidationIntent(
                kind="tool",
                entrypoint="unknown-tool",
                args=["--flag"],
            )
        )

        self.assertFalse(policy["accepted"])
        self.assertIn("not an approved executable", policy["reason"])

    def test_validation_intent_policy_rejects_repo_script_tool_kind_mismatch(self):
        orchestrator = self.orchestrator

        policy = orchestrator.validation_intent_policy(
            orchestrator.ValidationIntent(
                kind="repo-script",
                entrypoint="mvn",
                args=["-q", "test"],
            )
        )

        self.assertFalse(policy["accepted"])
        self.assertIn("approved repo-local script or wrapper", policy["reason"])

    def test_validation_intent_execution_tokens_resolve_tool_wrapper(self):
        orchestrator = self.orchestrator

        with mock.patch.object(
            orchestrator.shutil,
            "which",
            return_value=r"C:\tools\apache-maven\bin\mvn.cmd",
        ):
            tokens = orchestrator.validation_intent_execution_tokens(
                orchestrator.ValidationIntent(
                    kind="tool",
                    entrypoint="mvn",
                    args=["-q", "test"],
                )
            )

        self.assertEqual(
            [r"C:\tools\apache-maven\bin\mvn.cmd", "-q", "test"],
            tokens,
        )

    def test_collect_validation_commands_marks_unknown_validation_commands(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection finished with partial data.",
        )
        record.unknown_fields = ["validationCommands"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual([], command_payloads)
        self.assertEqual([], task_payloads[0]["validationCommands"])
        self.assertFalse(task_payloads[0]["validationCommandsKnown"])
        self.assertEqual(["validationCommands"], task_payloads[0]["unknownFields"])

    def test_collect_validation_commands_normalizes_safe_raw_command(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection finished with one direct validation command.",
        )
        record.validation_commands = ["scripts/docs/check-doc-consistency.ps1"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual(1, task_payloads[0]["legacyValidationCommandCount"])
        self.assertTrue(task_payloads[0]["legacyValidationCommandsPresent"])
        self.assertEqual(1, len(command_payloads))
        self.assertEqual("command", command_payloads[0]["sourceKind"])
        self.assertTrue(command_payloads[0]["compatibilityOnly"])
        self.assertIsNone(command_payloads[0]["intent"])
        self.assertEqual("repo-script", command_payloads[0]["normalizedIntent"]["kind"])

    def test_collect_validation_commands_prefers_structured_intents_for_duplicates(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="inspect",
            title="Inspect",
            agent="analyst",
            prompt="Inspect the coordinator.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Inspection produced one structured validation suggestion.",
        )
        record.validation_intents = [
            orchestrator.ValidationIntent(
                kind="repo-script",
                entrypoint="scripts/docs/check-doc-consistency.ps1",
                args=[],
            )
        ]
        record.validation_commands = ["scripts/docs/check-doc-consistency.ps1"]

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertEqual(
            ["scripts/docs/check-doc-consistency.ps1"],
            task_payloads[0]["renderedValidationIntents"],
        )
        self.assertEqual(1, len(command_payloads))
        self.assertEqual("intent", command_payloads[0]["sourceKind"])
        self.assertEqual(["inspect"], command_payloads[0]["taskIds"])

    def test_collect_validation_commands_adds_docs_consistency_helper_for_docs_only_changes(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="edit-docs",
            title="Edit docs",
            agent="implementer",
            prompt="Improve quickstart docs.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Improved README.",
            files_touched=["examples/spring-boot-starter-quickstart/README.md"],
            actual_files_touched=["examples/spring-boot-starter-quickstart/README.md"],
        )

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertTrue(task_payloads[0]["docsOnlyTouchedFiles"])
        self.assertTrue(task_payloads[0]["docsValidationRecommended"])
        helper = next(command for command in command_payloads if command["sourceKind"] == "coordinator-helper")
        self.assertEqual("docs-consistency", helper["helperKind"])
        self.assertEqual("scripts/docs/check-doc-consistency.ps1", helper["command"])
        self.assertEqual(["edit-docs"], helper["taskIds"])

    def test_collect_validation_commands_does_not_add_docs_helper_for_non_doc_changes(self):
        orchestrator = self.orchestrator
        task = orchestrator.TaskDefinition(
            id="edit-code",
            title="Edit code",
            agent="implementer",
            prompt="Improve controller.",
        )
        record = make_task_run_record(
            orchestrator,
            task,
            status="completed",
            summary="Improved controller.",
            files_touched=["examples/spring-boot-starter-quickstart/src/main/java/App.java"],
            actual_files_touched=["examples/spring-boot-starter-quickstart/src/main/java/App.java"],
        )

        task_payloads, command_payloads = orchestrator.collect_validation_commands(
            [record],
            included_statuses={"completed"},
        )

        self.assertFalse(task_payloads[0]["docsOnlyTouchedFiles"])
        self.assertFalse(task_payloads[0]["docsValidationRecommended"])
        self.assertEqual([], [command for command in command_payloads if command["sourceKind"] == "coordinator-helper"])


if __name__ == "__main__":
    unittest.main()
