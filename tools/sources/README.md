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

To move the frontend to a remote repository, first publish the recorded frontend
commit there. Then run `git submodule set-url frontend <repository-url>` and
`git submodule sync -- frontend`, commit `.gitmodules`, and remove the unused
bundle. The same bootstrap command subsequently uses ordinary Git checkout.

This uses the standard [Git bundle transport](https://git-scm.com/docs/git-bundle)
and [local submodule URL configuration](https://git-scm.com/docs/git-submodule).
