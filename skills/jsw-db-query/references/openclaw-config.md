# OpenClaw Configuration Example

Use these settings when you want OpenClaw to provide the JavaSqlWeb server URL and access token to this skill.

## Required Variables

- `JSW_BASE_URL`
- `JSW_ACCESS_TOKEN`

## Example `~/.openclaw/openclaw.json`

```json
{
  "skills": {
    "entries": {
      "jsw-db-query": {
        "enabled": true,
        "env": {
          "JSW_BASE_URL": "https://your-jsw.example.com",
          "JSW_ACCESS_TOKEN": "jsw_3ffb35c3161ebe8931216d65cd7c3133bf280909"
        },
        "config": {
          "baseUrl": "https://your-jsw.example.com"
        }
      }
    }
  }
}
```

## Notes

- Keep `JSW_ACCESS_TOKEN` in `env`, not hard-coded inside prompts.
- `config.baseUrl` is optional metadata for OpenClaw-side wiring; this skill itself reads the environment variable contract above.
- After updating `openclaw.json`, refresh or restart OpenClaw so it reloads skills and entry configuration.
