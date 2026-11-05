---
filePattern: "**/*"
---

﻿---
filePattern: "**/*"
---

# tidy-changes

Allow small related cleanup while preserving the requested scope.

- Clean up code directly touched by the requested change when it improves readability or consistency.
- Keep cleanup local to the modified area; do not start unrelated refactors.
- Remove dead code, unused imports, or duplicated lines introduced or exposed by the change.
- Mention any larger cleanup opportunities instead of including them silently.

