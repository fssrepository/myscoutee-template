#!/usr/bin/env bash
# Template documents are minimal project content; a deployment supplies its own
# privacy text through the retained content/configuration models.
audit_project_seeds() {
  jq -e '
    [.[] | select(.documentType=="privacy" and .active==true)] as $documents
    | ($documents|length)==2 and ([$documents[].lang]|sort)==["en","hu"]
    and all($documents[]; (.sections|length)>0)
  ' "$1/helpCenterRevisions.json" >/dev/null || {
    echo 'Template privacy documents must contain active English/Hungarian content.' >&2; return 1;
  }
}
