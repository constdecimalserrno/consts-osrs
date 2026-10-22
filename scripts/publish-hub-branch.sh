#!/usr/bin/env bash
# Publish one plugin to its Plugin Hub branch, plugin-hub/<plugin>: the plugin's folder alone, at the root.
#
# The Plugin Hub only builds a plugin whose runelite-plugin.properties is at the root of the repo, and its
# manifest names a repository and a commit, not a branch. So the monorepo stays the source of truth and each
# plugin gets a branch cut from it with git subtree split. The split is deterministic, so every publish
# fast-forwards the branch and older hub commits stay reachable.
#
#   scripts/publish-hub-branch.sh consts-exp
#
# Prints the two lines for the plugin's manifest in runelite/plugin-hub (plugins/<plugin>).
set -euo pipefail

plugin="${1:?usage: scripts/publish-hub-branch.sh <plugin-folder>}"
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -f "$plugin/runelite-plugin.properties" ]]; then
  echo "$plugin/runelite-plugin.properties is missing" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit first: the branch is cut from HEAD." >&2
  exit 1
fi

branch="plugin-hub/$plugin"
commit="$(git subtree split --prefix="$plugin" HEAD 2>/dev/null)"
git branch -f "$branch" "$commit"
git push origin "$branch"

echo
echo "repository=$(git remote get-url origin | sed 's#\.git$##').git"
echo "commit=$commit"
