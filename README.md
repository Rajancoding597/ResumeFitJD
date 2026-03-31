# AI Resume Tailor

Spring Boot web app that rewrites LaTeX resume bullet points against a pasted job description while preserving the surrounding LaTeX structure.

## Features

- Paste LaTeX resume source and a job description into a single-page UI.
- Extract bullet content from `\item` blocks and `\resumeItem{...}` style bullets.
- Use the Gemini API to produce structured rewrites and change reasons.
- Validate model output and fall back to original bullets when a rewrite looks unsafe.
- Return updated LaTeX plus a side-by-side preview of each change.

## Configuration

Create a project-local `.env` file:

```powershell
Copy-Item .env.example .env
```

If you want the server to use a local fallback Gemini key, put it in `.env`:

```env
GEMINI_API_KEY=your_api_key_here
```

If you want to test the hosted-style BYOK flow, you can leave `GEMINI_API_KEY` unset and enter the key in the UI instead.

Optional settings:

- `GEMINI_MODEL` defaults to `gemini-2.5-flash-lite`
- `GEMINI_API_BASE_URL` defaults to `https://generativelanguage.googleapis.com/v1beta/models`
- `GEMINI_TIMEOUT_SECONDS` defaults to `60`

## Run

Simplest Windows command:

```powershell
.\run.cmd
```

This wrapper bypasses PowerShell script policy for this project, ensures the local tools are ready, and then starts the app.

Or run the underlying steps yourself:

Bootstrap project-local tools first:

```powershell
.\scripts\Ensure-ProjectTools.ps1
```

Run tests with only project-local tooling:

```powershell
.\scripts\Test-Local.ps1
```

Start the app with only project-local tooling:

```powershell
.\scripts\Run-Local.ps1
```

Then open `http://localhost:8080`.

## Local Tooling Layout

This project is set up to keep tooling and downloaded dependencies inside the project folder:

- `tools\jdk-21\`
- `tools\maven\`
- `.m2\repository\`
- `target\`

`Ensure-ProjectTools.ps1` copies a local JDK 21 and Maven distribution into `tools\` from known machine locations when available, so the project can run without relying on global PATH entries afterward.
