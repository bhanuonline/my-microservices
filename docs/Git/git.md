Untrack file
git rm --cached -r <file>


During pull if any conflict then ue it ours , theirs based on decision
git checkout --ours src/main/resources/application.properties
-X ours / -X theirs is used at merge start.

Git reset :
removes commit and code changes :git reset --hard HEAD~1
Go back to a known commit :git reset --hard <commit-hash>

👉 Your local repo is messy, you want exactly what’s on remote
git fetch origin
git reset --hard origin/main

👉 Delete files not tracked by git
git clean -fd
To preview first (recommended):
git clean -n

👉 Fresh start without recloning
git reset --hard
git clean -fd

git log origin/dev..origin/master --oneline

git merge-base origin/master origin/dev
→ 795d14b83cab54c278415199ad36d1ec0e38fbec
👉 This commit is the last common point where master and dev were in sync.

Step 1: Sync your local dev with remote dev
git fetch origin
git checkout dev
git reset --hard origin/dev

git fetch origin
git checkout dev
git reset --hard origin/dev
git merge origin/master

git merge-base origin/master origin/dev  : “From where did remote master and remote dev diverge?”
git merge-base master dev “From where did my local master and dev diverge?”
If hashes differ → ✅ root cause confirmed.

One-line takeaway
origin/* = server truth
local branches = your truth
Conflicts happen when those truths differ.