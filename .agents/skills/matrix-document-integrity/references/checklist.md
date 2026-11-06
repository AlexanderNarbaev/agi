# The defect checklist

Seven classes. Each has a command. A defect without a `path:line` cannot be fixed by the
person who reads the report.

| # | the defect class | what it means | the command that finds it |
|---|---|---|---|
| 1 | **broken relative link** | a markdown link points at a target that does not exist | `python3 scripts/docs-link-check.py --root . --strict` |
| 2 | **broken anchor** | a link names a heading that is not in the target | the same checker |
| 3 | **missing prose-referenced file** | a path written in backticks in the prose does not resolve | the same checker |
| 4 | **duplicate stable identifier** | two documents claim the same identifier | the same checker |
| 5 | **a current document citing a superseded one** | the link resolves, but the target is `SUPERSEDED` or `HISTORICAL` | read the target's status line |
| 6 | **a status that does not match the content** | the document claims `NORMATIVE` and its content is not a rule | read the document against `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` |
| 7 | **a count that does not match the tree** | the document says 43 and the tree holds 42 | re-derive the count with the command the document names |

## The other validators

| the validator | what it checks |
|---|---|
| `scripts/validate-project-registry.py` | the capability registry: every row, every field |
| `scripts/validate-project-state.py` | the project state against the branch, the commit, the canonical set and the registers |
| `scripts/validate-test-catalogue.py` | the per-scope test registry against its evidence |

Each exits 0 on a clean tree and non-zero on a defect. **The exit code is the evidence; the
console text is the report.**

## The two exemptions, and why they are auditable

A document that names a file **in order to say that the file does not exist** is not making
a broken reference, and the checker says so on the line itself:

```
<!-- no-link: the reason -->
<!-- no-id: the reason -->
```

**The reason is on the line.** A silent fix would hide exactly the facts this check exists
to surface. A checker run over the whole tree reports the external links as `UNVERIFIED`,
because it performs no network check and does not claim that any of them is valid.

## What is not a defect

- A file that simply does not exist, when no document claims that it does.
- An external link that was not fetched.
- A historical document that describes a past state — it is `HISTORICAL`, and that is the
  correct status for it.
