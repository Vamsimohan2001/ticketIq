# Build and test results

## Latest local verification

| Check | Result |
|---|---|
| Full Maven build and test suite (`mvn -B verify`) | Passed: 93 tests, 0 failures (79 core tests and 14 application tests) |
| Browser JavaScript syntax (`node --check ticketiq-app/src/main/resources/static/app.js`) | Passed |
| Application health at `http://localhost:8080/actuator/health` | `UP` |
| Provider metadata and agent sign-in | Passed; the local instance uses the mock provider and agent sign-in returns the AGENT role |
| Updated frontend copy served by the application | Passed |

The application can be started with `scripts/run.ps1` on Windows or `bash scripts/run.sh` on Linux and macOS.
The full test suite can be run with `scripts/test.ps1` or `bash scripts/test.sh`.

## Known limitations

* The mock provider uses local keyword rules and hashed embeddings. Its evaluation scores describe those rules and are
  not a measure of Gemini or Ollama performance.
* Gemini and Ollama require their respective services and configuration for live-provider testing.
* The vector index is held in memory and rebuilt at startup. See `DECISIONS.md`.
