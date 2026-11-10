# bpmn-to-code web app

[![Docker Hub](https://img.shields.io/docker/v/miragon/bpmn-to-code-web?sort=semver&label=Docker%20Hub&color=335DE5)](https://hub.docker.com/r/miragon/bpmn-to-code-web)

**Upload a BPMN model in the browser and download its typed Process API, without a build tool.**

The web app of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) runs the same generator as the Gradle and Maven plugins and keeps nothing on disk. Use the [hosted version](https://bpmn-to-code.miragon.io/static/index.html) or run this image on your own infrastructure.

- Output languages: Kotlin, Java, C# (experimental), TypeScript (experimental)
- Engines: Camunda 8 / Zeebe, Camunda 7, Operaton
- Up to three BPMN files per request; the generated code uses the package `com.example.process` (C#: namespace `Com.Example.Process`)
- The app names the runtime package the generated code needs next to the result

## Run

```bash
docker run -p 8080:8080 miragon/bpmn-to-code-web:latest
```

Then open http://localhost:8080. The image is built for `linux/amd64` (on Apple silicon, add `--platform linux/amd64`) and has no shell, so point your orchestrator's health check at `GET /health`.

## Configuration

| Environment variable   | Default | Meaning                                                                   |
| ---------------------- | ------- | ------------------------------------------------------------------------- |
| `PORT`                 | `8080`  | Port the server listens on                                                |
| `ALLOWED_CORS_ORIGINS` | empty   | Comma-separated origins allowed to call the API. Empty or `*` allows all. |
| `IMPRINT_URL`          | empty   | Shows a "Legal Notice" link in the footer when set                        |
| `PRIVACY_URL`          | empty   | Shows a "Privacy Policy" link in the footer when set                      |

The REST API, its routes and its responses are described in the [web app guide](https://miragon.github.io/bpmn-to-code/web/); `GET /swagger` serves the Swagger UI.

## Help and feedback

- **Documentation:** [miragon.github.io/bpmn-to-code](https://miragon.github.io/bpmn-to-code/)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](https://github.com/Miragon/bpmn-to-code/blob/main/LICENSE)
