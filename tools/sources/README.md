# Local frontend submodule

`frontend.bundle` is Git's transport archive of the standalone template frontend
at the parent repository's exact gitlink. It contains committed frontend source
and history, not dependencies, build output or the components repository.

After cloning the parent repository, run `tools/bootstrap-submodules.sh`.
The frontend is initialized locally from the bundle. Its `components` submodule
is fetched from the existing remote repository at its recorded commit.

After a frontend change, commit inside `frontend`, run
`tools/sync-local-frontend.sh`, and commit the gitlink and bundle together in the
parent. Keep this one stable bundle path. Never put credentials in either Git
repository.

## Push from VS Code to a local frontend checkout

The frontend may use a separate local repository as its `origin` while the
parent still distributes the portable bundle. If that local repository has
the receiving branch checked out, configure it once with Git's
[`receive.denyCurrentBranch=updateInstead`](https://git-scm.com/docs/git-config#Documentation/git-config.txt-receivedenyCurrentBranch):

```sh
git -C /path/to/local/frontend-origin config --local receive.denyCurrentBranch updateInstead
```

Git then updates the receiving branch, index and clean working tree together.
It still refuses an update when the receiving checkout has uncommitted changes.
Use the frontend repository's ordinary **Push** or **Sync Changes** in VS Code;
no custom command or force push is needed for each update. The local frontend
branch must track the corresponding `origin` branch.

The bundle is the parent repository's portable source artifact, not a writable
Git remote. Export it with the existing sync script when recording a new
frontend gitlink in the parent, as described above. A fresh bundle-only checkout
needs a local or hosted repository configured as `origin` before it can push.

The MSC-122 development checkout uses the existing sibling
`myscoutee-template-frontend` as this local receiver. On 2026-10-12 the standard
`git push origin master:master` succeeded from `2eb5ef8` to `6fd3abd`; both
checkouts were clean and `master` matched `origin/master` afterward.

To move the frontend to a remote repository, first publish the recorded frontend
commit there. Then run `git submodule set-url frontend <repository-url>` and
`git submodule sync -- frontend`, commit `.gitmodules`, and remove the unused
bundle. The same bootstrap command subsequently uses ordinary Git checkout.

This uses the standard [Git bundle transport](https://git-scm.com/docs/git-bundle)
and [local submodule URL configuration](https://git-scm.com/docs/git-submodule).
