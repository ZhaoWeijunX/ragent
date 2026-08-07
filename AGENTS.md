# Local validation environment

Use the checked local JDK 17 explicitly. The system `java` may point to JDK 8, which cannot build this project.

```powershell
$env:JAVA_HOME = 'D:\devlop_tool\java\jdk\jdk17'
& 'D:\devlop_tool\apache-maven-3.9.15\bin\mvn.cmd' -version
```

The root Maven project requires Java 17. For focused evaluation-workbench verification, run the normal lifecycle (not a direct `surefire:test` invocation):

```powershell
$env:JAVA_HOME = 'D:\devlop_tool\java\jdk\jdk17'
& 'D:\devlop_tool\apache-maven-3.9.15\bin\mvn.cmd' -pl bootstrap -am '-Dtest=DeterministicMetricsTest,EvalCaseImportSupportTest,EvalConfigSnapshotSupportTest,EvalRunCompareSupportTest,EvalRunTerminalStatusTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`mvn test` runs Spotless in this repository and can format changed source files. Check the diff afterwards instead of assuming a clean worktree.

## Frontend

There is no globally installed Node/npm in this environment. Use the bundled Node runtime with the existing `frontend/node_modules`; do not run pnpm against this checkout.

```powershell
$node = 'C:\Users\1data\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
Push-Location frontend
& $node '.\node_modules\vite\bin\vite.js' build
Pop-Location
```

The current ESLint command is blocked before linting by an `eslint-plugin-react-refresh` / ESLint 8 configuration incompatibility (`Unexpected top-level property "name"`). Treat it as an environment/tooling issue until the dependency configuration is updated; use the Vite production build for frontend compilation verification.
