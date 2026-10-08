# bpmn-to-code-web

The web app of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code): upload BPMN files in the browser and get the generated Process API or the process JSON, without a build tool. It uses the same generator as the Gradle and Maven plugins and keeps nothing on disk.

- Output languages: Kotlin, Java, C# (experimental)
- Engines: Camunda 8 / Zeebe, Camunda 7, Operaton
- Up to three BPMN files per request; the generated code uses the package `com.example.process`

Hosted version: https://bpmn-to-code.miragon.io/static/index.html

## Run

```bash
docker run -p 8080:8080 miragon/bpmn-to-code-web:latest
```

Then open http://localhost:8080. The image is built for `linux/amd64`.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Port the server listens on |
| `ALLOWED_CORS_ORIGINS` | empty | Comma-separated origins allowed to call the API. Empty or `*` allows all. |
| `IMPRINT_URL` | empty | Shows a "Legal Notice" link in the footer when set |
| `PRIVACY_URL` | empty | Shows a "Privacy Policy" link in the footer when set |

## Routes

| Route | Purpose |
|---|---|
| `GET /` | Redirects to the UI at `/static/index.html` |
| `POST /api/generate` | Generates the Process API |
| `POST /api/generate-json` | Generates the process JSON |
| `GET /api/config` | Version and legal links |
| `GET /health` | Health check, answers `{"status": "UP"}` |
| `GET /swagger` | Swagger UI for the API |
| `GET /examples/<engine>-bike-leasing.bpmn` | Bundled example model (`zeebe`, `c7`, `operaton`) |

The image has no shell, so configure health checks in your orchestrator against `/health`.

Documentation: https://miragon.github.io/bpmn-to-code/web/
