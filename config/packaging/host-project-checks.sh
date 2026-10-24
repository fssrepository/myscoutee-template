#!/usr/bin/env bash
# Only the selected template data is required beyond the common host checks.
project_smoke_dependencies() { :; }
project_smoke_seeds() {
  require_count "demo showcase cards" "$2" "sampleCards"
  require_count "demo personal chats" "$2" "chats"
}
project_smoke_http() {
  check_http_or_internal "active explanation content" "/api/explanation/active?lang=en&contextKey=home" '"documentKind":"explanation"'
}
project_qualification_report() { :; }
