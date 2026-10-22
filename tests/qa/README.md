# QA

All current campaigns are closed. This is the consolidated acceptance baseline; no test replay is queued.

The matrix keeps its original columns and domain overview. Cases, recorded results and project-specific tools are grouped by the matrix category. There is one current copy; intermediate logs and superseded variants remain in Git.

- [CSV matrix](QA_MATRIX.csv)

| Category | Cases |
| --- | --- |
| [QA infrastructure](categories/QA%20infrastructure) | 1 |
| [Identity & access](categories/Identity%20%26%20access) | 4 |
| [Chat](categories/Chat) | 1 |
| [Administration](categories/Administration) | 1 |
| [Content](categories/Content) | 1 |
| [General UI](categories/General%20UI) | 2 |
| [Notifications](categories/Notifications) | 1 |
| [API test](categories/API%20test) | 1 |
| [Operator](categories/Operator) | 2 |

The versioned `@fssrepository/myscoutee-qa@1.4.6` package loads `tests/qa/config/browser-session-checkpoints.json` directly: `npm --prefix tests/qa run browser -- CHECKPOINT_ID`. Its startup boundary and fixture checks are unchanged. A future deliberately requested replay follows the selected case; documentation cleanup starts no browser or application test.

## Node package

The private QA package has its own lockfile and configuration in `config/`.
It uses the published `@fssrepository/myscoutee-qa@1.4.6` dependency.

```sh
npm --prefix tests/qa ci --ignore-scripts
```

The shared browser command loads this package’s configuration directly;
there is no project launcher copy. Installing dependencies does not run a case.
