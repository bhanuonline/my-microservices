# Git Pull vs Git Pull --Rebase

## Scenario

Suppose:

- **Remote branch:** `A → B → C`
- **My local branch:** `A → B → D`

Where:

- `C` = teammate's latest change
- `D` = my local change

---

## 1. `git pull`

```bash
git pull
```

- Gets latest remote changes
- Merges them with my local changes
- Can create a merge commit

**Result:**

```
A → B → C
     \   \
      D → M
```

`M` = merge commit

---

## 2. `git pull --rebase`

```bash
git pull --rebase
```

- Gets latest remote changes
- Temporarily removes my local commit `D`
- Applies remote commit `C` first
- Puts my commit `D` on top of `C`

**Result:**

```
A → B → C → D
            ↑
         my change
```

---

## Why Use Rebase?

**Without rebase:**

```
A → B → C
     \   \
      D → M
```

**With rebase:**

```
A → B → C → D
```

So rebase keeps the Git history **clean and linear**.

---

## Simple Meaning

| Command | Meaning |
|---|---|
| `git pull` | Get remote changes + **MERGE** my changes |
| `git pull --rebase` | Get remote changes + **PUT MY CHANGES ON TOP** |

---

## Important

Rebase does **NOT** mean deleting my code.

It:

1. Temporarily moves my commit
2. Gets the latest remote changes
3. Reapplies my commit on top

---

## Your Error

```
error: cannot pull with rebase:
Your index contains uncommitted changes.
```

**Meaning:** You have uncommitted local changes.

Git is saying:

> "I cannot start the rebase until your working tree is clean."

---

## Safest Solution

```bash
git status
git stash
git pull --rebase
git stash pop
```

---

## Flow

```
My uncommitted changes
        ↓
     git stash
        ↓
 Working tree clean
        ↓
  git pull --rebase
        ↓
Latest remote changes
        ↓
  My changes restored
        ↓
    git stash pop
```

---

# Merge vs Rebase

Both combine changes from two branches, but they do it **differently**.

---

## Starting Point (same for both)

You and your teammate both branched from `main` at commit `B`.

```
main:     A → B → C        (teammate added C)
              \
feature:       D → E       (you added D, E)
```

You want to bring `C` into your `feature` branch.

---

## 1. Git Merge

```bash
git checkout feature
git merge main
```

Git creates a **new merge commit `M`** that ties both histories together.

**Result:**

```
main:     A → B → C
              \    \
feature:       D → E → M
```

- `M` has **two parents**: `E` (your work) and `C` (teammate's work)
- History is **preserved exactly** as it happened
- Branch shape is **non-linear** (you can see the split)

---

## 2. Git Rebase

```bash
git checkout feature
git rebase main
```

Git **replays** your commits `D` and `E` on top of `C`, one by one.

**Result:**

```
main:     A → B → C
                   \
feature:            D' → E'
```

- `D'` and `E'` are **new commits** (same changes, new commit IDs)
- No merge commit
- History is **linear** — looks like you branched from `C` originally

---

## Side-by-Side

| Aspect | Merge | Rebase |
|---|---|---|
| History shape | Non-linear (shows branching) | Linear (straight line) |
| Extra commit? | Yes (merge commit `M`) | No |
| Original commits kept? | Yes | No — rewritten as `D'`, `E'` |
| Safe on shared branches? | Yes | **No** (rewrites history) |
| Conflict resolution | Once, in the merge commit | Possibly once **per commit** replayed |

---

## Real Example

You're working on `feature/login`. Teammate merged a fix to `main`.

### With merge

```bash
git checkout feature/login
git merge main
```

Log looks like:

```
*   M   Merge branch 'main' into feature/login
|\
| * C   fix: null check in auth
* | E   feat: add login button
* | D   feat: login form
|/
* B   initial
```

### With rebase

```bash
git checkout feature/login
git rebase main
```

Log looks like:

```
* E'  feat: add login button
* D'  feat: login form
* C   fix: null check in auth
* B   initial
```

Clean, linear — as if you started your work **after** the teammate's fix.

---

## When to Use Which

**Use merge when:**

- Working on a shared/public branch (others have pulled it)
- You want to preserve exact history
- The branch is long-lived (e.g., merging `feature` → `main`)

**Use rebase when:**

- Cleaning up **your own local** commits before pushing
- You want linear history
- Pulling latest `main` into your feature branch (`git pull --rebase`)

---

## Golden Rule

**Never rebase commits that others have already pulled.**

Rebase creates new commit IDs (`D → D'`), so anyone with the old commits will get confused and end up with duplicate history.

> Rule of thumb: **rebase local, merge public.**

---

# Git Pull vs Git Fetch

Both bring changes from the **remote** to your **local** machine — but they behave very differently.

---

## The Key Difference

| Command | What it does |
|---|---|
| `git fetch` | Downloads remote changes **but doesn't touch your working branch** |
| `git pull` | Downloads remote changes **AND merges them into your current branch** |

**Simple formula:**

```
git pull = git fetch + git merge
```

---

## Starting Point

- **Your local `main`:** `A → B`
- **Remote `main` (origin/main):** `A → B → C → D` (teammate pushed `C` and `D`)

---

## 1. Git Fetch

```bash
git fetch origin
```

Git downloads `C` and `D` into a **hidden tracking branch** called `origin/main`. Your local `main` is **untouched**.

**Result:**

```
Local main:         A → B                (unchanged)

origin/main:        A → B → C → D        (updated — this is the remote snapshot)
```

Now you can **inspect** what's new before deciding what to do:

```bash
git log main..origin/main       # see new commits
git diff main origin/main       # see what changed
```

Then you decide:

```bash
git merge origin/main           # merge them in
# OR
git rebase origin/main          # rebase your work on top
# OR
# do nothing — you just wanted to look
```

---

## 2. Git Pull

```bash
git pull origin main
```

Git does **two things automatically**:

1. `git fetch` — downloads `C` and `D`
2. `git merge origin/main` — merges them into your local `main`

**Result:**

```
Local main:         A → B → C → D        (updated immediately)

origin/main:        A → B → C → D
```

Your working branch **moves forward automatically**. No chance to inspect first.

---

## Real-World Example

You come back to work in the morning. You want to know what your team did overnight.

### Safe way — fetch first

```bash
git fetch origin
git log HEAD..origin/main --oneline
```

Output:

```
d4f5e6a  fix: race condition in checkout
a1b2c3d  feat: add dark mode toggle
```

Now you **see** what's coming before you take it:

```bash
git merge origin/main         # okay, I want these changes
```

### Fast way — pull directly

```bash
git pull origin main
```

Changes are applied immediately. If there's a conflict, you're already in a broken state and have to resolve it right now.

---

## Side-by-Side

| Aspect | `git fetch` | `git pull` |
|---|---|---|
| Downloads remote commits? | Yes | Yes |
| Changes your working branch? | **No** | **Yes** |
| Safe to run anytime? | **Yes** (never breaks anything) | No (can cause conflicts) |
| Requires clean working tree? | No | Yes (recommended) |
| Good for inspecting first? | **Yes** | No |

---

## Visual Flow

### Fetch

```
Remote:  A → B → C → D
                    ↓
              (downloaded)
                    ↓
Local:   A → B                 ← still here, untouched
         ↑
      your HEAD

origin/main tracks → A → B → C → D
```

### Pull

```
Remote:  A → B → C → D
                    ↓
              (downloaded + merged)
                    ↓
Local:   A → B → C → D         ← moved forward automatically
                    ↑
                 your HEAD
```

---

## When to Use Which

**Use `git fetch` when:**

- You want to **see** what changed before integrating
- You're on a shared branch and want to be careful
- You want to inspect a specific remote branch without switching
- You need remote refs for `git log`, `git diff`, or comparisons

**Use `git pull` when:**

- You're on **your own** feature branch and just want the latest
- You trust the incoming changes
- You want speed over caution

---

## Pro Tip

Many teams prefer:

```bash
git pull --rebase
```

This does `fetch` + `rebase` instead of `fetch` + `merge` — keeping history linear (see the Merge vs Rebase section above).

---

## Golden Rule (Pull vs Fetch)

> **`fetch` is safe. `pull` is fast. When in doubt, fetch first.**

---

# `git status` — What It Shows You

`git status` is your **dashboard**. It tells you the current state of your working directory and staging area compared to the last commit.

---

## What It Reports

`git status` shows **four things**:

1. **Which branch** you're on
2. **How your branch compares to the remote** (ahead / behind / diverged)
3. **Staged changes** — files ready to be committed
4. **Unstaged changes** — modified files not yet staged
5. **Untracked files** — new files Git doesn't know about yet

---

## Example Output

```bash
$ git status

On branch feature/login
Your branch is ahead of 'origin/feature/login' by 2 commits.
  (use "git push" to publish your local commits)

Changes to be committed:
  (use "git restore --staged <file>..." to unstage)
        modified:   src/auth.js
        new file:   src/login.js

Changes not staged for commit:
  (use "git add <file>..." to update what will be committed)
  (use "git restore <file>..." to discard changes in working directory)
        modified:   README.md

Untracked files:
  (use "git add <file>..." to include in what will be committed)
        notes.txt
```

---

## Breaking It Down

### 1. Branch info

```
On branch feature/login
Your branch is ahead of 'origin/feature/login' by 2 commits.
```

- You're on `feature/login`
- You have **2 local commits** that aren't on the remote yet
- Fix: `git push`

Other messages you might see:

| Message | Meaning |
|---|---|
| `Your branch is up to date with 'origin/main'.` | Local and remote match |
| `Your branch is behind 'origin/main' by 3 commits.` | Remote has commits you don't have → `git pull` |
| `Your branch and 'origin/main' have diverged` | Both sides have unique commits → merge or rebase needed |

---

### 2. Changes to be committed (staged)

```
Changes to be committed:
        modified:   src/auth.js
        new file:   src/login.js
```

- These files are in the **staging area** (index)
- They **will be included** in your next `git commit`
- To unstage: `git restore --staged <file>`

---

### 3. Changes not staged for commit (modified but not staged)

```
Changes not staged for commit:
        modified:   README.md
```

- You edited this file, but haven't run `git add` on it
- It will **NOT be included** in the next commit unless you stage it
- To stage: `git add README.md`
- To discard changes: `git restore README.md`

---

### 4. Untracked files

```
Untracked files:
        notes.txt
```

- New file — Git has **never seen it before**
- Not part of any commit
- To track: `git add notes.txt`
- To ignore forever: add to `.gitignore`

---

## The Three Areas Git Cares About

`git status` is essentially showing you the state of these three zones:

```
┌──────────────────┐   git add    ┌────────────────┐   git commit   ┌────────────────┐
│ Working Directory│ ───────────► │ Staging Area   │ ─────────────► │  Repository    │
│ (your files)     │              │ (index)        │                │  (.git)        │
└──────────────────┘              └────────────────┘                └────────────────┘
      ▲                                  ▲
      │                                  │
"Changes not staged"              "Changes to be committed"
"Untracked files"
```

---

## Useful Variants

```bash
git status                # full output
git status -s             # short format (compact)
git status -b             # show branch info in short format
git status --ignored      # also show ignored files
```

**Short format example:**

```bash
$ git status -s
 M README.md          # modified, not staged
M  src/auth.js        # modified, staged
A  src/login.js       # added (new file, staged)
?? notes.txt          # untracked
```

Column 1 = staged status, Column 2 = working directory status.

| Symbol | Meaning |
|---|---|
| `M` | Modified |
| `A` | Added (new file staged) |
| `D` | Deleted |
| `R` | Renamed |
| `??` | Untracked |
| `!!` | Ignored |

---

## Interview-Friendly One-Liner

> **`git status` shows the state of your working directory and staging area relative to `HEAD` and the tracked remote branch — what's changed, what's staged, what's untracked, and whether you're ahead of or behind the remote.**

---

## When You'd Run It

- **Before every commit** — verify you're committing what you think you are
- **After pulling** — check for conflicts
- **When lost** — "what state am I in?"
- **Before switching branches** — see if you have uncommitted work

It's the **safest command in Git** — it only reads, never modifies. Run it as often as you want.

---

# Working Directory vs Staging Area vs Repository

These are the **three zones** Git tracks. Understanding them is the foundation of everything else in Git.

---

## The Big Picture

```
┌────────────────────┐    git add    ┌────────────────┐   git commit   ┌────────────────┐
│  Working Directory │ ────────────► │  Staging Area  │ ─────────────► │   Repository   │
│  (your files)      │               │  (index)       │                │   (.git dir)   │
│                    │ ◄──────────── │                │ ◄───────────── │                │
└────────────────────┘  git restore  └────────────────┘   git reset    └────────────────┘
```

Every file in your project exists in **one or more of these three zones**.

---

## 1. Working Directory

**What it is:** The actual files and folders on your disk that you edit.

- The stuff you see in VS Code / file explorer
- What your editor reads and writes
- Files here can be **tracked** (Git knows about them) or **untracked** (new files Git hasn't seen)

**Example:**

```
my-project/
├── src/
│   └── login.js       ← you just edited this
├── README.md
└── notes.txt          ← brand new, Git doesn't know about it yet
```

**Analogy:** Your **desk** — where you actually do the work.

---

## 2. Staging Area (Index)

**What it is:** A **holding zone** where you prepare the exact set of changes for the next commit.

- Physically lives inside `.git/index` (a binary file)
- Also called the **"index"** or **"cache"**
- You control what enters it with `git add`
- Only staged changes get committed

**Example:**

```bash
git add src/login.js       # staged
git add README.md          # staged
# notes.txt still untracked, NOT staged
```

Now the staging area contains `login.js` and `README.md`. If you commit now, **only those two** go in — even if you have 10 other modified files.

**Analogy:** A **shipping box** — you decide what to put in before sealing it.

---

## 3. Repository (`.git` directory)

**What it is:** The **permanent history** of your project — every commit, branch, tag, and object.

- Lives in the hidden `.git/` folder at your project root
- Once something is committed here, it's **safe** (part of history)
- Contains all commits, trees, blobs, refs, config, hooks
- This is what gets pushed/pulled from remotes

**Example:**

```
.git/
├── objects/      ← all commits, trees, and file snapshots (blobs)
├── refs/         ← branch and tag pointers
├── HEAD          ← what you have checked out
├── index         ← the staging area
└── config        ← repo config
```

**Analogy:** A **filing cabinet** — sealed boxes (commits) stored permanently.

---

## How a File Moves Through the Three Zones

Let's trace a new file called `login.js`:

### Step 1 — Create the file

```bash
touch src/login.js
```

- Lives in: **Working Directory** only
- Git status: **Untracked**

### Step 2 — Stage it

```bash
git add src/login.js
```

- Lives in: **Working Directory + Staging Area**
- Git status: **Staged (new file)**

### Step 3 — Commit it

```bash
git commit -m "add login"
```

- Lives in: **Working Directory + Repository**
- Staging area is now clean (matches the last commit)
- Git status: **Clean / nothing to commit**

### Step 4 — Modify it

```bash
# edit src/login.js
```

- Working Directory version ≠ Staging Area version
- Git status: **Modified, not staged**

### Step 5 — Stage and commit again

```bash
git add src/login.js
git commit -m "update login"
```

- All three zones now match again.

---

## Side-by-Side

| Zone | Location | Purpose | Command to move IN | Command to move OUT |
|---|---|---|---|---|
| **Working Directory** | Your project folder | Where you edit files | `git checkout` / `git restore` | edit / delete files |
| **Staging Area** | `.git/index` | Prepare next commit | `git add` | `git restore --staged` |
| **Repository** | `.git/objects` | Permanent history | `git commit` | `git reset` |

---

## Visual: Which Zone Does Each Command Touch?

```
git add <file>          →  Working Dir  →  Staging Area
git commit              →  Staging Area →  Repository
git restore <file>      →  Repository   →  Working Dir  (discards local changes)
git restore --staged    →  Repository   →  Staging Area (unstages)
git reset --soft HEAD~1 →  moves Repository pointer, keeps staging + working dir
git reset --mixed HEAD~1→  moves Repository + resets staging, keeps working dir
git reset --hard HEAD~1 →  moves Repository + resets staging + working dir
git checkout <branch>   →  updates staging + working dir from Repository
```

---

## Why Three Zones? Why Not Two?

Most VCS systems (like SVN) only have **two**: working directory + repository.

Git's **staging area** gives you a superpower:

**You can commit *part* of your changes.**

Example: you fixed a bug AND added a feature in the same edit session. You want them in **two separate commits** for clarity.

```bash
git add src/bug-fix.js         # stage only the bug fix
git commit -m "fix: null check"

git add src/new-feature.js     # now stage the feature
git commit -m "feat: add dark mode"
```

Without a staging area, you'd have to commit everything together — or manually stash half of it.

---

## Interview-Friendly One-Liner

> **The working directory is where you edit files, the staging area (index) is where you prepare exactly what goes into the next commit, and the repository (`.git`) is the permanent history of all commits. `git add` moves changes from working dir → staging, and `git commit` moves them from staging → repository.**

---

## Quick Mental Model

| Zone | Think of it as |
|---|---|
| Working Directory | Your **desk** — messy, in-progress work |
| Staging Area | A **shipping box** — you pack it carefully before sealing |
| Repository | The **filing cabinet** — sealed boxes stored forever |

---

# What is `HEAD` in Git?

`HEAD` is Git's answer to the question: **"Where am I right now?"**

It's a **pointer** that tells Git which commit your working directory currently reflects.

---

## The Simple Definition

> **`HEAD` is a pointer to the current branch, which in turn points to the latest commit on that branch.**

So `HEAD` is essentially: **"the commit I'm currently sitting on."**

---

## Where `HEAD` Lives

Physically, `HEAD` is a **file** inside your `.git/` folder:

```bash
$ cat .git/HEAD
ref: refs/heads/main
```

This says: *"HEAD is pointing to the branch called `main`."*

And `refs/heads/main` is another file:

```bash
$ cat .git/refs/heads/main
a1b2c3d4e5f6...
```

That's the **SHA of the latest commit** on `main`.

---

## The Chain of Pointers

```
HEAD  →  refs/heads/main  →  commit a1b2c3d  →  (tree, parent, etc.)
 ↑              ↑                    ↑
"where       "the branch          "the actual
 am I?"       I'm on"              snapshot"
```

Three levels of indirection:

1. **HEAD** points to a **branch**
2. **Branch** points to a **commit**
3. **Commit** points to a **snapshot** of files

---

## Visual Example

You're on `main`, latest commit is `C`.

```
A ─── B ─── C  ← main  ← HEAD
```

Now you commit `D`:

```bash
git commit -m "add D"
```

Both `main` and `HEAD` move forward automatically:

```
A ─── B ─── C ─── D  ← main  ← HEAD
```

Now you switch branches:

```bash
git checkout feature
```

`HEAD` moves to point at `feature`:

```
A ─── B ─── C ─── D  ← main
            \
             E ─── F  ← feature  ← HEAD
```

---

## Detached HEAD

Normally `HEAD` points to a **branch**. But sometimes it points **directly to a commit**. This is called **detached HEAD**.

**How it happens:**

```bash
git checkout a1b2c3d       # checked out a specific commit, not a branch
```

Now:

```
A ─── B ─── C  ← main
      ↑
    HEAD  (pointing directly to commit B — no branch!)
```

**Why it's "detached":** any new commits you make here aren't attached to any branch. If you switch away, they can be **lost**.

```bash
$ cat .git/HEAD
a1b2c3d4e5f6...       ← just a raw SHA, no "ref:" prefix
```

**How to fix it:** create a branch to save your work.

```bash
git checkout -b my-new-branch
```

---

## `HEAD` Shortcuts (Very Common)

Git gives you shorthand for navigating relative to `HEAD`:

| Notation | Meaning |
|---|---|
| `HEAD` | Current commit |
| `HEAD~1` or `HEAD~` | Parent of HEAD (1 commit back) |
| `HEAD~2` | Grandparent (2 commits back) |
| `HEAD~5` | 5 commits back |
| `HEAD^` | Same as `HEAD~1` for most cases |
| `HEAD^2` | **Second parent** of a merge commit |
| `@` | Alias for `HEAD` |

**Example:**

```
A ─── B ─── C ─── D  ← HEAD
```

- `HEAD` = `D`
- `HEAD~1` = `C`
- `HEAD~2` = `B`
- `HEAD~3` = `A`

---

## Common Commands That Use `HEAD`

```bash
git log HEAD                   # log starting from current commit
git diff HEAD                  # what's changed since last commit
git diff HEAD~1 HEAD           # diff between last two commits
git reset --hard HEAD          # discard all uncommitted changes
git reset --hard HEAD~1        # undo last commit (and its changes)
git reset --soft HEAD~3        # undo last 3 commits, keep changes staged
git revert HEAD                # create new commit that undoes last commit
git checkout HEAD -- file.js   # restore file to last committed state
git show HEAD                  # show details of current commit
```

---

## `HEAD` vs `origin/HEAD`

Don't confuse these:

| Ref | What it points to |
|---|---|
| `HEAD` | Your **current local** position |
| `origin/HEAD` | The **default branch** on the remote (usually `origin/main`) |
| `origin/main` | The last known state of `main` on the remote |

---

## `HEAD` vs Branch — What's the Difference?

They **usually** point to the same commit, but they're not the same thing:

- **Branch** = a named pointer to a commit (`main`, `feature`, etc.)
- **HEAD** = a pointer that says *"which branch am I on?"*

Think of it like:

- Branch = a bookmark in a book
- HEAD = your finger showing which bookmark you're currently reading from

---

## The `reflog` — HEAD's History

Git keeps a log of **every place HEAD has been** for ~90 days:

```bash
git reflog
```

Output:

```
a1b2c3d HEAD@{0}: commit: add login form
9e8d7c6 HEAD@{1}: checkout: moving from main to feature
b5c4d3e HEAD@{2}: commit: fix null check
```

This is a **lifesaver** — if you "lose" a commit (bad reset, deleted branch), `reflog` finds it.

---

## Interview-Friendly One-Liner

> **`HEAD` is a pointer to the currently checked-out commit — usually indirectly, by pointing to the current branch, which points to the latest commit. When it points directly to a commit instead of a branch, you're in "detached HEAD" state.**

---

## Quick Mental Model

```
HEAD  =  "You are here" pin on a map

Normally:   HEAD → branch → commit
Detached:   HEAD → commit  (no branch — dangerous)
```

