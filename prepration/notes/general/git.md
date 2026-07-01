| Term          | Meaning                   |
| ------------- | ------------------------- |
| `HEAD`        | Your current local branch |
| `origin`      | Remote GitHub repository  |
| `origin/main` | Main branch on GitHub     |
| `origin/HEAD` | Default branch on GitHub  |

Local Repository
----------------
HEAD
|
v
main

Remote Repository (GitHub)
--------------------------
origin/HEAD
|
v
origin/main

Switch to SSH:
git remote set-url origin git@github.com:bhanuonline/test.git

added name/email:
git config --global user.name "Bhanu Pratap"
git config --global user.email "your-github-email@example.com"

See the commit that hasn't been pushed:
git log --oneline origin/main..HEAD

Cleanup ACtivity:
Since the feature branch has already been merged, you can delete it:
git branch -d feature/daily-workflow
git push origin --delete feature/daily-workflow

#revert
git reset --hard HEAD~1 or  git reset HEAD~1
git reset --hard 1f4f129 or git reset 1f4f129


#rebase
git rebase is used to move or replay your commits on top of another branch
When NOT to Use Rebase
❌ Don't rebase commits that other developers are already using on a shared branch.
git merge develop is safe

I want my branch to be based on the latest main/develop
git rebase main

