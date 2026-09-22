# Docker Learning Notes — From Scratch to Production + Interview

A structured, phase-by-phase guide to learning Docker deeply. Each phase builds intuition before moving on. Use this as a self-study curriculum and interview prep.

---

## Index

- [Phase 1 — Foundations (Why Docker Exists)](#phase-1--foundations-why-docker-exists)
  - [1. The Problem Docker Solves](#1-the-problem-docker-solves)
  - [2. Container vs Virtual Machine](#2-container-vs-virtual-machine-critical-concept)
  - [3. The Linux Primitives (The Real "Magic")](#3-the-linux-primitives-the-real-magic)
  - [4. Docker Architecture](#4-docker-architecture)
  - [5. Images vs Containers vs Registries](#5-images-vs-containers-vs-registries)
  - [6. Interview Answers (Phase 1)](#6-quick-interview-answers-phase-1)
  - [Mental Checkpoints](#mental-checkpoints-before-moving-on)
- [Phase 2 — Hands-on Basics](#phase-2--hands-on-basics)
  - [1. Install Docker](#1-install-docker)
  - [2. The Container Lifecycle](#2-the-container-lifecycle-most-important-mental-model)
  - [3. Core Container Commands](#3-core-container-commands)
  - [4. Core Image Commands](#4-core-image-commands)
  - [5. Common Flags You'll Use Every Day](#5-common-flags-youll-use-every-day)
  - [6. Interactive Shell into a Container](#6-interactive-shell-into-a-container)
  - [7. Cleanup — Reclaim Disk Space](#7-cleanup--reclaim-disk-space)
  - [8. Practice Exercises](#8-practice-exercises)
  - [9. Interview Answers (Phase 2)](#9-interview-answers-phase-2)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-3)
- [Phase 3 — Building Images (Dockerfile)](#phase-3--building-images-dockerfile)
  - [1. What is a Dockerfile?](#1-what-is-a-dockerfile)
  - [2. The Build Process](#2-the-build-process-mental-model)
  - [3. Dockerfile Instructions Reference](#3-dockerfile-instructions-reference)
  - [4. CMD vs ENTRYPOINT (Classic Interview Trap)](#4-cmd-vs-entrypoint-classic-interview-trap)
  - [5. COPY vs ADD](#5-copy-vs-add)
  - [6. Layer Caching — The #1 Build Speedup](#6-layer-caching--the-1-build-speedup)
  - [7. Build Context and .dockerignore](#7-build-context-and-dockerignore)
  - [8. Multi-Stage Builds](#8-multi-stage-builds)
  - [9. Base Image Choices](#9-base-image-choices)
  - [10. A Complete Real-World Example](#10-a-complete-real-world-example)
  - [11. Practice Exercises](#11-practice-exercises)
  - [12. Interview Answers (Phase 3)](#12-interview-answers-phase-3)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-4)
- [Phase 4 — Data & Networking](#phase-4--data--networking)
  - [1. Why This Phase Matters](#1-why-this-phase-matters)
  - [2. Container Filesystem — The Default Behavior](#2-container-filesystem--the-default-behavior)
  - [3. Storage Options — Three Ways](#3-storage-options--three-ways-to-persist-data)
  - [4. Volumes Deep Dive](#4-volumes-deep-dive)
  - [5. Bind Mounts Deep Dive](#5-bind-mounts-deep-dive)
  - [6. tmpfs Mounts](#6-tmpfs-mounts)
  - [7. Docker Networking — The Big Picture](#7-docker-networking--the-big-picture)
  - [8. Network Drivers (Modes)](#8-network-drivers-modes)
  - [9. User-Defined Bridge Networks (The Right Default)](#9-user-defined-bridge-networks-the-right-default)
  - [10. Port Publishing vs Exposing](#10-port-publishing--vs-exposing)
  - [11. Container-to-Container Communication](#11-container-to-container-communication)
  - [12. A Complete Example — App + Postgres](#12-a-complete-example--app--postgres)
  - [13. Practice Exercises](#13-practice-exercises)
  - [14. Interview Answers (Phase 4)](#14-interview-answers-phase-4)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-5)
- [Phase 5 — Docker Compose](#phase-5--docker-compose)
  - [1. Why Compose Exists](#1-why-compose-exists)
  - [2. Anatomy of a docker-compose.yml](#2-anatomy-of-a-docker-composeyml)
  - [3. Service Configuration Reference](#3-service-configuration-reference)
  - [4. Everyday Compose Commands](#4-everyday-compose-commands)
  - [5. Environment Variables and .env Files](#5-environment-variables-and-env-files)
  - [6. Volumes and Networks in Compose](#6-volumes-and-networks-in-compose)
  - [7. depends_on and Startup Ordering](#7-depends_on-and-startup-ordering-the-classic-trap)
  - [8. Healthchecks](#8-healthchecks)
  - [9. Profiles and Overrides](#9-profiles-and-overrides)
  - [10. Build vs Image](#10-build-vs-image)
  - [11. A Complete Real-World Stack](#11-a-complete-real-world-stack)
  - [12. Practice Exercises](#12-practice-exercises)
  - [13. Interview Answers (Phase 5)](#13-interview-answers-phase-5)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-6)
- [Phase 6 — Production Concerns](#phase-6--production-concerns)
  - [1. The Dev-to-Prod Gap](#1-the-dev-to-prod-gap)
  - [2. Image Optimization](#2-image-optimization)
  - [3. Security — Run as Non-Root](#3-security--run-as-non-root)
  - [4. Security — Image Scanning](#4-security--image-scanning)
  - [5. Security — Image Signing and Provenance](#5-security--image-signing-and-provenance)
  - [6. Security — Runtime Hardening](#6-security--runtime-hardening)
  - [7. Secrets Management](#7-secrets-management)
  - [8. Resource Limits and Restart Policies](#8-resource-limits-and-restart-policies)
  - [9. Logging (12-Factor)](#9-logging-12-factor)
  - [10. Monitoring and Metrics](#10-monitoring-and-metrics)
  - [11. Registries in Production](#11-registries-in-production)
  - [12. Image Tagging Strategy](#12-image-tagging-strategy)
  - [13. CI/CD with Docker](#13-cicd-with-docker)
  - [14. Practice Exercises](#14-practice-exercises)
  - [15. Interview Answers (Phase 6)](#15-interview-answers-phase-6)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-7)
- [Phase 7 — Orchestration](#phase-7--orchestration)
  - [1. Why Orchestration Exists](#1-why-orchestration-exists)
  - [2. What an Orchestrator Actually Does](#2-what-an-orchestrator-actually-does)
  - [3. Docker Swarm — Quick Intro](#3-docker-swarm--quick-intro)
  - [4. Kubernetes — The Landscape](#4-kubernetes--the-landscape)
  - [5. Kubernetes Core Objects](#5-kubernetes-core-objects-minimum-vocabulary)
  - [6. Pods Wrap Containers](#6-pods-wrap-containers-the-key-mental-shift)
  - [7. How K8s Actually Runs a Container](#7-how-k8s-actually-runs-a-container)
  - [8. Container Runtimes and OCI](#8-container-runtimes-and-oci)
  - [9. Why K8s Deprecated Docker (Dockershim)](#9-why-k8s-deprecated-docker-dockershim)
  - [10. Compose → Kubernetes Mapping](#10-compose--kubernetes-mapping)
  - [11. When to Use What](#11-when-to-use-what)
  - [12. Practice Exercises](#12-practice-exercises)
  - [13. Interview Answers (Phase 7)](#13-interview-answers-phase-7)
  - [Mental Checkpoints](#mental-checkpoints-before-phase-8)
- [Phase 8 — Deep Internals](#phase-8--deep-internals)
  - [1. Why This Phase Matters](#1-why-this-phase-matters)
  - [2. Image Layers and Content-Addressable Storage](#2-image-layers-and-content-addressable-storage)
  - [3. Image Manifest and Config](#3-image-manifest-and-config)
  - [4. OverlayFS Deep Dive](#4-overlayfs-deep-dive)
  - [5. Namespaces Deep Dive](#5-namespaces-deep-dive)
  - [6. cgroups Deep Dive](#6-cgroups-deep-dive)
  - [7. The Container Lifecycle at Syscall Level](#7-the-container-lifecycle-at-syscall-level)
  - [8. BuildKit Internals](#8-buildkit-internals)
  - [9. Rootless Docker](#9-rootless-docker)
  - [10. Docker Desktop on Mac and Windows](#10-docker-desktop-on-mac-and-windows-the-hidden-vm)
  - [11. Networking Under the Hood](#11-networking-under-the-hood)
  - [12. Common Debugging Techniques](#12-common-debugging-techniques)
  - [13. Practice Exercises](#13-practice-exercises)
  - [14. Senior Interview Answers (Phase 8)](#14-senior-interview-answers-phase-8)
  - [Final Wrap-Up](#final-wrap-up)
- [Appendix — Docker for Java Developers](#appendix--docker-for-java-developers)
  - [A1. The Java Container Landscape](#a1-the-java-container-landscape-whats-different)
  - [A2. Base Image Choices for Java](#a2-base-image-choices-for-java)
  - [A3. The Wrong Way — What Most Beginners Write](#a3-the-wrong-way--what-most-beginners-write)
  - [A4. Multi-Stage Build (Maven)](#a4-multi-stage-build-the-right-way--maven)
  - [A5. Multi-Stage Build (Gradle)](#a5-multi-stage-build-gradle)
  - [A6. BuildKit Cache Mount for Maven/Gradle](#a6-buildkit-cache-mount--even-faster)
  - [A7. Spring Boot Layered JARs](#a7-spring-boot-layered-jars-best-practice-for-spring)
  - [A8. JVM in a Container — Critical Gotchas](#a8-jvm-in-a-container--the-critical-gotchas)
  - [A9. Production Spring Boot Dockerfile](#a9-complete-production-spring-boot-dockerfile)
  - [A10. Alternative Build Tools (Jib, Buildpacks)](#a10-alternative-build-tools)
  - [A11. Compose for a Java Microservice](#a11-docker-compose-for-a-java-microservice)
  - [A12. Dev Workflow — Live Reload](#a12-dev-workflow--live-reload-with-docker)
  - [A13. Native Images with GraalVM](#a13-native-images-with-graalvm-advanced)
  - [A14. Practice Path (Java-Specific)](#a14-practice-path-java-specific)
  - [A15. Java-in-Docker Interview Questions](#a15-java-in-docker-interview-questions)

---

# Phase 1 — Foundations (Why Docker Exists)

Before touching commands, understand the problem Docker solves and the Linux features that make it work.

---

## 1. The Problem Docker Solves

### The "works on my machine" nightmare
Imagine you build an app on your laptop:
- Node.js v18, Ubuntu 22.04, some system libraries, specific env vars

You hand it to a colleague — their machine has Node v16, macOS, different libraries. **It breaks.**

You deploy to a server — different OS, missing dependencies. **It breaks again.**

**Traditional solutions and why they hurt:**
- **Install docs** ("run these 40 commands") — fragile, humans skip steps
- **Config management** (Ansible, Chef) — better, but still slow, still drift
- **Virtual Machines** — solve it, but each VM is 2–20 GB, boots in minutes, wastes RAM

Docker's answer: **package the app + all its dependencies + OS libraries into a single portable unit called a container**. Runs identically on any machine that has Docker.

---

## 2. Container vs Virtual Machine (Critical Concept)

This is the #1 interview question. Build the mental model.

### Virtual Machine
```
┌─────────────────────────────────────┐
│   App A    │   App B    │   App C   │
├─────────────────────────────────────┤
│  Bins/Libs │  Bins/Libs │  Bins/Libs│
├─────────────────────────────────────┤
│  Guest OS  │  Guest OS  │  Guest OS │  ← Full OS per VM (2-20 GB each)
├─────────────────────────────────────┤
│         Hypervisor (VMware, KVM)     │
├─────────────────────────────────────┤
│           Host Operating System      │
├─────────────────────────────────────┤
│              Hardware                │
└─────────────────────────────────────┘
```
Each VM has its **own kernel**. Heavy, slow to boot, but strong isolation.

### Container
```
┌─────────────────────────────────────┐
│   App A    │   App B    │   App C   │
├─────────────────────────────────────┤
│  Bins/Libs │  Bins/Libs │  Bins/Libs│
├─────────────────────────────────────┤
│      Docker Engine (container runtime)│
├─────────────────────────────────────┤
│    Host Operating System (kernel)    │  ← SHARED kernel
├─────────────────────────────────────┤
│              Hardware                │
└─────────────────────────────────────┘
```
Containers **share the host kernel**. Only app + libraries are packaged. Tiny (MBs, not GBs), boot in **milliseconds**.

### Key differences

| Aspect | VM | Container |
|---|---|---|
| Size | GBs | MBs |
| Startup | Minutes | Milliseconds |
| Kernel | Own kernel per VM | Shares host kernel |
| Isolation | Strong (hardware-level) | Weaker (process-level) |
| Density | ~10s per host | 100s–1000s per host |
| OS flexibility | Any OS | Same kernel family as host |

**Intuition:** A VM is a whole house. A container is an apartment in a shared building — cheaper, faster to move into, but you share the foundation (kernel).

**Interview trap:** "Can you run Windows containers on Linux?" → No, because containers share the host kernel. (Docker Desktop on Mac/Windows cheats by running a hidden Linux VM.)

---

## 3. The Linux Primitives (The Real "Magic")

Docker is not magic. It's a friendly wrapper around **existing Linux kernel features**. Understanding these = understanding containers deeply.

### a) Namespaces — Isolation
A namespace makes a process **think it's alone** on the system. Docker uses 6 main ones:

| Namespace | What it isolates | Effect |
|---|---|---|
| **PID** | Process IDs | Container sees its own process tree (its app is PID 1) |
| **NET** | Network stack | Container has its own network interfaces, IPs, ports |
| **MNT** | Mount points | Container has its own filesystem view |
| **UTS** | Hostname | Container can have its own hostname |
| **IPC** | Inter-process comm | Isolated shared memory / semaphores |
| **USER** | User IDs | Container root can map to non-root on host |

**Intuition:** Namespaces answer "what can this process **see**?"

Try it: inside a container, run `ps aux` — you only see container processes, not the host's.

### b) cgroups (Control Groups) — Resource limits
cgroups answer "how much can this process **use**?"

They cap: CPU, memory, disk I/O, network bandwidth, number of processes.

Example: `docker run --memory=512m --cpus=1.5 myapp` — enforced via cgroups.

**Without cgroups**, one runaway container could eat all your host's RAM.

### c) Union Filesystems (OverlayFS) — Layered images
This is why Docker images are efficient.

An image is built as **stacked read-only layers**:
```
Layer 4:  Your app code            (5 MB)
Layer 3:  npm install dependencies (150 MB)
Layer 2:  Node.js runtime          (80 MB)
Layer 1:  Ubuntu base              (70 MB)
```

When you run a container, Docker adds a **thin writable layer on top**. Multiple containers from the same image **share the read-only layers** — you don't pay 300 MB per container.

**OverlayFS** is the kernel feature that merges these layers into a single filesystem view. Reads pull from lower layers; writes go to the top writable layer (**copy-on-write**).

### d) chroot — The ancestor
`chroot` changes the "apparent root directory" for a process — it can't see files outside its jail. Containers extend this idea with namespaces for much stronger isolation.

**Summary formula (memorize):**
> **Container = namespaces (what you see) + cgroups (what you can use) + union FS (how your files are stored)**

---

## 4. Docker Architecture

Docker isn't one program — it's a stack.

```
┌──────────────────────────────────────┐
│  docker CLI  (you type commands here)│
└──────────────┬───────────────────────┘
               │ REST API (Unix socket / TCP)
               ▼
┌──────────────────────────────────────┐
│  dockerd (Docker daemon)             │
│  - manages images, networks, volumes │
│  - talks to containerd               │
└──────────────┬───────────────────────┘
               ▼
┌──────────────────────────────────────┐
│  containerd  (container runtime)     │
│  - image pulling, container lifecycle│
└──────────────┬───────────────────────┘
               ▼
┌──────────────────────────────────────┐
│  runc  (low-level OCI runtime)       │
│  - actually creates the container    │
│    using namespaces + cgroups        │
└──────────────┬───────────────────────┘
               ▼
        [Linux kernel features]
```

**Walk-through of `docker run nginx`:**
1. CLI sends request to `dockerd`
2. `dockerd` checks if `nginx` image exists locally; if not, pulls from registry
3. `dockerd` tells `containerd` to create a container
4. `containerd` calls `runc`
5. `runc` uses kernel syscalls to create namespaces + cgroups
6. Nginx process starts inside the isolated environment

**Why the layers?** Modularity. Kubernetes originally used Docker, then dropped it and talks to `containerd` directly — skipping the `dockerd` layer.

---

## 5. Images vs Containers vs Registries

The three core nouns. Get these straight.

### Image
- A **read-only template** — like a class in OOP
- Built from a Dockerfile
- Made of stacked layers
- Identified by a name + tag: `nginx:1.25` or a SHA256 digest
- Stored on disk once, reused by many containers

### Container
- A **running instance** of an image — like an object in OOP
- Image + writable layer + runtime state
- Has its own PID, network, filesystem view
- Can be started, stopped, deleted; the image survives

### Registry
- A **server that stores images** — like GitHub for code
- Public: **Docker Hub**, GitHub Container Registry (GHCR)
- Private: AWS ECR, Google GCR, Harbor
- `docker pull` downloads from registry; `docker push` uploads

**Analogy:**
- Image = recipe
- Container = the actual dish you cooked
- Registry = the cookbook library

---

## 6. Quick Interview Answers (Phase 1)

**Q: How is a container different from a VM?**
> A VM virtualizes hardware and runs a full guest OS with its own kernel — heavy but strongly isolated. A container virtualizes the OS: it shares the host kernel and only packages the app plus its dependencies, using Linux namespaces for isolation and cgroups for resource limits. Result: containers are MBs vs GBs, boot in milliseconds vs minutes, and you can pack far more per host.

**Q: What Linux features make containers possible?**
> Namespaces isolate what a process can see (PID, network, mount, hostname, IPC, user). cgroups limit what it can use (CPU, memory, I/O). Union filesystems like OverlayFS enable layered, copy-on-write images. Together these give the illusion of an isolated machine without a hypervisor.

**Q: Why did Kubernetes deprecate Docker?**
> Kubernetes only needs the low-level runtime (containerd/runc), not the full Docker daemon. Talking to containerd directly via the CRI (Container Runtime Interface) is simpler and lighter. Docker-built images still work — they're OCI-compliant.

---

## Mental Checkpoints Before Moving On

Ask yourself:
- Could I explain to a friend why containers are lighter than VMs?
- Can I name the 3 primitives (namespaces, cgroups, union FS) and what each does?
- Do I know the difference between an image and a container?
- Can I trace what happens when I run `docker run nginx`?

If yes → ready for **Phase 2 (hands-on commands)**.

---

# Phase 2 — Hands-on Basics

**Goal:** Become fluent with everyday Docker commands. You should be able to pull an image, run a container, inspect it, exec into it, and clean up — without thinking.

---

## 1. Install Docker

- **Mac / Windows** → install **Docker Desktop** (includes CLI + hidden Linux VM + GUI)
- **Linux** → install **Docker Engine** (native, no VM needed)

Verify:
```bash
docker --version           # CLI version
docker info                # daemon status + system info
docker run hello-world     # end-to-end smoke test
```

If `hello-world` prints a welcome message, your Docker stack is working: CLI → daemon → containerd → runc → kernel.

---

## 2. The Container Lifecycle (Most Important Mental Model)

Every container moves through these states. Memorize this diagram — most `docker` commands are just transitions.

```
   docker pull                docker create              docker start
[Registry] ─────────► [Image] ─────────────► [Created] ─────────────► [Running]
                        │                                                │
                        │ docker run (= create + start)                  │
                        └────────────────────────────────────────────────┘
                                                                         │
                              docker stop / kill                         │
                     ┌───────────────────────────────────────────────────┘
                     ▼
                 [Stopped/Exited] ──── docker rm ────► [Gone]
                     │
                     └── docker start ──► [Running] again
```

**Key insight:** A stopped container **still exists** on disk (its writable layer + config). It only truly disappears after `docker rm`. This is why `docker ps -a` shows stopped containers you forgot about.

---

## 3. Core Container Commands

| Command | What it does |
|---|---|
| `docker run <image>` | Create + start a container from an image (most common) |
| `docker ps` | List **running** containers |
| `docker ps -a` | List **all** containers (including stopped) |
| `docker stop <id\|name>` | Graceful stop (SIGTERM, then SIGKILL after 10s) |
| `docker kill <id\|name>` | Immediate stop (SIGKILL) |
| `docker start <id\|name>` | Start a stopped container |
| `docker restart <id\|name>` | Stop + start |
| `docker rm <id\|name>` | Delete a stopped container |
| `docker rm -f <id\|name>` | Force-delete (stops if running) |
| `docker logs <id\|name>` | Print container's stdout/stderr |
| `docker logs -f <id\|name>` | Follow logs live (like `tail -f`) |
| `docker exec -it <id\|name> sh` | Run a command inside a running container |
| `docker inspect <id\|name>` | Full JSON details (IP, mounts, env, config) |
| `docker stats` | Live CPU/memory/network usage |
| `docker top <id\|name>` | Processes running inside a container |

**Tip:** You can reference containers by name (`my-nginx`) or by the first few chars of the ID (`a3f`). Names are easier — always name your containers with `--name`.

---

## 4. Core Image Commands

| Command | What it does |
|---|---|
| `docker images` | List images stored locally |
| `docker pull <image>` | Download image from a registry |
| `docker push <image>` | Upload image to a registry (requires login) |
| `docker rmi <image>` | Delete an image (must have no containers using it) |
| `docker tag <src> <dst>` | Give an image another name/tag |
| `docker login` | Authenticate to a registry (Docker Hub by default) |
| `docker history <image>` | Show layers that built the image |
| `docker image prune` | Remove dangling (untagged) images |

**Image naming format:** `registry/repo/name:tag`
- `nginx` → shorthand for `docker.io/library/nginx:latest`
- `ghcr.io/myorg/myapp:v1.2` → full form
- `:tag` defaults to `:latest` if omitted (**avoid `latest` in production**)

---

## 5. Common Flags You'll Use Every Day

Learn these — they show up in every `docker run` command.

| Flag | Purpose | Example |
|---|---|---|
| `-d` | Detached mode (run in background) | `docker run -d nginx` |
| `--name` | Give the container a name | `docker run --name web nginx` |
| `-p host:container` | Publish a port to the host | `-p 8080:80` (host 8080 → container 80) |
| `-P` | Publish **all** exposed ports to random host ports | `docker run -P nginx` |
| `-e KEY=VAL` | Set an environment variable | `-e POSTGRES_PASSWORD=secret` |
| `--env-file` | Load env vars from a file | `--env-file .env` |
| `-v host:container` | Mount a volume/bind mount | `-v /data:/var/lib/postgres` |
| `--rm` | Auto-delete the container when it exits | `docker run --rm alpine echo hi` |
| `-it` | Interactive + TTY (for shells) | `docker run -it ubuntu bash` |
| `--network` | Attach to a specific network | `--network mynet` |
| `--restart` | Restart policy | `--restart unless-stopped` |
| `--memory` `--cpus` | Resource limits (cgroups) | `--memory=512m --cpus=1` |

**Concrete example — run nginx serving on host port 8080:**
```bash
docker run -d --name web -p 8080:80 nginx
curl http://localhost:8080     # should return nginx welcome page
docker logs web                # see access logs
docker stop web && docker rm web
```

---

## 6. Interactive Shell into a Container

Two very different scenarios — don't confuse them.

### Enter a **running** container
```bash
docker exec -it <name> sh          # or bash if the image has it
```
Runs a **new process** inside the container. Original app keeps running.

### Run a **fresh** container just for exploration
```bash
docker run -it --rm ubuntu bash    # throwaway ubuntu shell
```

**Why `-it`?**
- `-i` = keep stdin open (interactive)
- `-t` = allocate a pseudo-TTY (terminal)
Without them, your shell won't behave right (no prompt, Ctrl-C weird).

**Debug tip:** if a container is crashing on startup, try `docker run -it --entrypoint sh <image>` to poke around inside without running the broken app.

---

## 7. Cleanup — Reclaim Disk Space

Docker eats disk fast. Old images, stopped containers, dangling volumes pile up.

| Command | Removes |
|---|---|
| `docker container prune` | All stopped containers |
| `docker image prune` | Dangling images (untagged) |
| `docker image prune -a` | **All** images not used by any container |
| `docker volume prune` | Volumes not used by any container |
| `docker network prune` | Unused networks |
| `docker system prune` | Containers + networks + dangling images |
| `docker system prune -a --volumes` | **Nuclear option** — removes everything unused |

Check disk usage:
```bash
docker system df           # summary
docker system df -v        # verbose per-item breakdown
```

---

## 8. Practice Exercises

Do these in order. Don't just read — type every command.

1. **Hello world:** `docker run hello-world`. Then `docker ps -a` — find the exited container. Remove it with `docker rm`.
2. **Run nginx on port 8080:** `docker run -d --name web -p 8080:80 nginx`. Open `http://localhost:8080` in a browser. Check `docker logs web`.
3. **Exec inside:** `docker exec -it web sh`. Run `ls /usr/share/nginx/html`. Edit `index.html` with `echo hi > /usr/share/nginx/html/index.html`. Refresh browser.
4. **Inspect:** `docker inspect web | less`. Find its IP address, mounts, and env vars.
5. **Two containers, same image:** run `docker run -d --name web2 -p 8081:80 nginx`. Confirm both work. Notice they share image layers (check `docker system df`).
6. **Env vars:** `docker run -d --name pg -e POSTGRES_PASSWORD=secret postgres:16`. Then `docker exec -it pg psql -U postgres` and run `\l`.
7. **Cleanup:** stop all containers and clean everything with `docker system prune -a`.

---

## 9. Interview Answers (Phase 2)

**Q: What's the difference between `docker run` and `docker start`?**
> `docker run` creates a **new** container from an image and starts it (it's `docker create` + `docker start`). `docker start` starts an **existing** stopped container — same writable layer, same config, same data.

**Q: What happens when a container's main process exits?**
> The container stops (moves to Exited state). Its filesystem is preserved until you `docker rm` it. A container is alive only as long as its **PID 1** (the main process defined by CMD/ENTRYPOINT) is running.

**Q: Difference between `docker stop` and `docker kill`?**
> `stop` sends SIGTERM for a graceful shutdown, waits 10s (default), then SIGKILL. `kill` sends SIGKILL immediately — the process has no chance to clean up.

**Q: How would you debug a container that keeps crashing on startup?**
> Check `docker logs <name>` first. If it dies too fast to inspect, override the entrypoint with a shell: `docker run -it --entrypoint sh <image>`. From inside, run the intended command manually and watch it fail. Also check `docker inspect` for exit code and OOMKilled status.

**Q: What does `-p 8080:80` actually do?**
> It creates a port-forwarding rule (via iptables/nat) so traffic hitting the **host** on port 8080 is forwarded to port 80 **inside** the container's network namespace. Without `-p`, the container is reachable only from other containers on the same Docker network, not from outside the host.

**Q: What's the difference between `-p` and `EXPOSE`?**
> `EXPOSE` in a Dockerfile is **documentation only** — it declares which ports the image intends to use. It does **not** publish them. `-p` at `docker run` time is what actually opens the host port.

---

## Mental Checkpoints Before Phase 3

Ask yourself:
- Can I explain the container lifecycle (image → created → running → stopped → removed)?
- Do I know why `docker ps` doesn't show stopped containers but `docker ps -a` does?
- Can I run nginx on host port 8080, exec into it, and clean up — from memory?
- Do I know the difference between `-p` and `EXPOSE`?
- Do I know what `-it` actually does?

If yes → ready for **Phase 3 (Dockerfile — building your own images)**.

---

# Phase 3 — Building Images (Dockerfile)

**Goal:** Stop running other people's images. Build your own — correctly, small, cacheable, and secure.

---

## 1. What is a Dockerfile?

A **Dockerfile** is a plain text recipe that tells Docker how to build an image. Each line is an instruction that produces a **layer**.

Minimal example (a Node.js app):
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
EXPOSE 3000
CMD ["node", "server.js"]
```

Build and run:
```bash
docker build -t myapp:v1 .        # . = build context (current dir)
docker run -d -p 3000:3000 myapp:v1
```

That `.` at the end matters — it tells Docker the **build context** (which files it can see). More on this in section 7.

---

## 2. The Build Process (Mental Model)

When you run `docker build`:

1. Docker CLI **packages the build context** (all files in `.`, minus `.dockerignore`) and sends it to the daemon.
2. Daemon reads the Dockerfile line by line.
3. For each instruction, it **creates a new layer** on top of the previous one.
4. Each layer is cached by a hash. If the instruction and its inputs haven't changed, Docker reuses the cache instead of re-running.
5. The final layer stack becomes your image, tagged with `-t myapp:v1`.

**Key insight:** Layers are immutable. Once created, they never change. This is what makes caching work — and what makes bad instruction order painfully slow.

---

## 3. Dockerfile Instructions Reference

The ones you'll actually use:

| Instruction | Purpose | Example |
|---|---|---|
| `FROM` | Base image to build on | `FROM python:3.12-slim` |
| `WORKDIR` | Set working directory (like `cd`) | `WORKDIR /app` |
| `COPY` | Copy files from build context into image | `COPY . /app` |
| `ADD` | Like COPY, but also handles URLs and tar extraction | `ADD file.tar.gz /app` |
| `RUN` | Execute a command **during build** (installs, compiles) | `RUN apt-get update && apt-get install -y curl` |
| `ENV` | Set environment variable (persists in container) | `ENV NODE_ENV=production` |
| `ARG` | Build-time variable (does NOT persist in container) | `ARG VERSION=1.0` |
| `EXPOSE` | Document which port the app listens on | `EXPOSE 8080` |
| `CMD` | Default command to run when container starts | `CMD ["node", "server.js"]` |
| `ENTRYPOINT` | The executable; args from CMD are appended | `ENTRYPOINT ["python"]` |
| `USER` | Switch to a non-root user | `USER appuser` |
| `HEALTHCHECK` | How Docker checks the container is alive | `HEALTHCHECK CMD curl -f http://localhost/ \|\| exit 1` |
| `VOLUME` | Declare a mount point for external data | `VOLUME /data` |
| `LABEL` | Metadata (author, version, description) | `LABEL maintainer="me@x.com"` |

**Rule of thumb:**
- Anything happening **at build time** → `RUN`, `COPY`, `ARG`
- Anything happening **at runtime** → `CMD`, `ENTRYPOINT`, `ENV`, `EXPOSE`

---

## 4. CMD vs ENTRYPOINT (Classic Interview Trap)

Both define what runs when the container starts. The difference is how they interact with arguments.

### CMD — Default command, easily overridden
```dockerfile
CMD ["node", "server.js"]
```
- `docker run myapp` → runs `node server.js`
- `docker run myapp echo hi` → runs `echo hi` (**overrides** CMD entirely)

### ENTRYPOINT — The executable, arguments get appended
```dockerfile
ENTRYPOINT ["node"]
```
- `docker run myapp server.js` → runs `node server.js`
- `docker run myapp --version` → runs `node --version`

### The Common Pattern — Use Both Together
```dockerfile
ENTRYPOINT ["node"]
CMD ["server.js"]
```
- `docker run myapp` → runs `node server.js` (CMD supplies default args)
- `docker run myapp app.js` → runs `node app.js` (args override CMD, not ENTRYPOINT)

**Mental model:**
- `ENTRYPOINT` = **what** you always run (the tool)
- `CMD` = **default arguments** (can be overridden)

### Shell form vs Exec form
Always prefer **exec form** (JSON array):
```dockerfile
CMD ["node", "server.js"]        # exec form — PID 1 is `node` directly
CMD node server.js               # shell form — PID 1 is `/bin/sh -c ...`
```
Shell form wraps in `sh -c`, which **swallows signals** — your app won't receive SIGTERM properly, leading to ugly shutdowns.

---

## 5. COPY vs ADD

Both copy files into the image. Use `COPY` 95% of the time.

| Feature | COPY | ADD |
|---|---|---|
| Copy local files | ✅ | ✅ |
| Extract local tar archives | ❌ | ✅ (auto-extracts) |
| Fetch from URL | ❌ | ✅ (but don't — use `RUN curl` instead) |
| Predictable | ✅ | ❌ (magic behavior) |

**Rule:** Use `COPY` for local files. Only use `ADD` when you genuinely need tar auto-extraction. For URLs, use `RUN wget/curl` (gives you a chance to `rm` the download in the same layer, keeping size down).

---

## 6. Layer Caching — The #1 Build Speedup

Every instruction creates a layer. Docker caches layers by hash. **Order instructions from least-changing to most-changing.**

### Bad order (rebuilds npm install every time you edit code):
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY . .                # ← code changes constantly, invalidates cache
RUN npm ci              # ← now this re-runs on every code change ☹️
CMD ["node", "server.js"]
```

### Good order (npm install cached until deps change):
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./   # ← rarely changes
RUN npm ci              # ← cached! only re-runs if package.json changed
COPY . .                # ← code changes go here, in the LAST layer
CMD ["node", "server.js"]
```

**Rule:** Copy **only what you need for the next step**. Copy dependencies first, install them, then copy the rest of the code.

### Combine RUN instructions to reduce layers
Each `RUN` is a new layer. Chain related commands with `&&`:
```dockerfile
# Bad — 3 layers, apt cache stays in image
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

# Good — 1 layer, apt cache removed in same layer
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
```

Why does the "bad" version keep the apt cache? Because the `rm` runs in a **new layer** — the previous layer still holds the files. Union filesystems can hide files but can't shrink lower layers.

---

## 7. Build Context and .dockerignore

The **build context** = everything in the directory you pass to `docker build`. It's tarred up and sent to the daemon.

Problem: if your project directory has `node_modules/` (500 MB), `.git/` (200 MB), and log files, every build ships all of that to the daemon — slow, and any of it can accidentally get `COPY .`ed into the image.

Solution: **`.dockerignore`** (works like `.gitignore`):
```
node_modules
.git
.env
*.log
dist
coverage
.DS_Store
```

Always create a `.dockerignore`. It makes builds faster **and** prevents secret files (like `.env`) from leaking into images.

---

## 8. Multi-Stage Builds

The single biggest technique for small production images.

**Problem:** to build a Go/Node/Java app, you need compilers, dev dependencies, build tools. But at runtime, you only need the compiled artifact. Shipping the build tools bloats the image and expands the attack surface.

**Solution:** use multiple `FROM` statements. Build in one stage, copy only the artifact into a clean final stage.

### Go example
```dockerfile
# ---- Build stage ----
FROM golang:1.22 AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /app ./cmd/server

# ---- Runtime stage ----
FROM alpine:3.19
COPY --from=builder /app /app
EXPOSE 8080
CMD ["/app"]
```

Result: build stage might be 800 MB (Go toolchain), but the final image is ~15 MB (Alpine + one binary). Only the final stage ships.

### Node example
```dockerfile
# ---- Build stage ----
FROM node:20 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package*.json ./
RUN npm ci --omit=dev
EXPOSE 3000
CMD ["node", "dist/server.js"]
```

Notice `--from=builder` — copies from a specific named stage.

---

## 9. Base Image Choices

The base image is your foundation. Bigger base = bigger image + more security patches to worry about.

| Base | Size | When to use |
|---|---|---|
| `ubuntu:22.04` | ~80 MB | Familiar, lots of packages — use for dev/debugging |
| `debian:12-slim` | ~75 MB | Slimmer Debian; good middle ground |
| `alpine:3.19` | ~7 MB | Tiny, uses musl libc (may break some binaries) |
| `-slim` variants (`python:3.12-slim`, `node:20-slim`) | ~50-150 MB | Trimmed official images; safe default |
| `-alpine` variants | ~30-100 MB | Smallest official images; watch for musl issues |
| `gcr.io/distroless/*` | ~20 MB | No shell, no package manager — most secure. Hard to debug. |
| `scratch` | 0 MB | Empty. Only for static binaries (Go). Ultimate minimum. |

**Watch out for Alpine:** it uses `musl` libc instead of `glibc`. Some Python wheels, Node native modules, and prebuilt binaries won't work → falls back to source compilation, which is slow. If a build gets weirdly slow or fails on Alpine, try `-slim` instead.

**Distroless** for production: no shell means no `exec -it sh` — you debug by rebuilding with a shell-having base. Trade-off: much smaller attack surface.

---

## 10. A Complete Real-World Example

A production-quality Dockerfile for a Node.js app:

```dockerfile
# ---- Build stage ----
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM node:20-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -S app && adduser -S app -G app

# Copy only what runtime needs
COPY --from=builder --chown=app:app /app/dist ./dist
COPY --from=builder --chown=app:app /app/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

USER app

ENV NODE_ENV=production \
    PORT=3000

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:3000/health || exit 1

CMD ["node", "dist/server.js"]
```

What this does right:
- **Multi-stage** → no dev tools in final image
- **Alpine base** → small
- **Non-root user** → security
- **`npm ci --omit=dev`** → skip devDependencies
- **`npm cache clean`** → shrink layer
- **`HEALTHCHECK`** → Docker/orchestrator knows if app is alive
- **Exec form CMD** → proper signal handling
- **`--chown` on COPY** → files owned by `app`, not root

---

## 11. Practice Exercises

1. **Dockerize a "hello world" script:** Create a `hello.py` that prints "hi". Write a Dockerfile using `python:3.12-slim`. Build with `-t hello:v1`, run it.
2. **Test caching:** In the Node example, edit a source file and rebuild. Notice `npm ci` is cached. Now edit `package.json` and rebuild — see it re-run.
3. **Multi-stage size comparison:** Write two Dockerfiles for the same Go app — one single-stage using `golang:1.22`, one multi-stage ending in `alpine`. Compare with `docker images` — the multi-stage should be ~50x smaller.
4. **Base image experiment:** Build the same Node app on `node:20`, `node:20-slim`, and `node:20-alpine`. Compare sizes.
5. **Break the cache on purpose:** In a Dockerfile, put `COPY . .` before `RUN npm ci`. Time a rebuild after a code change. Then fix the order and time it again.
6. **Non-root user:** Take any Dockerfile, add a `USER` directive. Verify with `docker exec -it <name> whoami`.
7. **Inspect layers:** `docker history myapp:v1` — see each layer's size and command. Find the biggest layer.

---

## 12. Interview Answers (Phase 3)

**Q: What's the difference between CMD and ENTRYPOINT?**
> `ENTRYPOINT` defines the executable that always runs. `CMD` provides default arguments that can be overridden at `docker run` time. The common pattern is `ENTRYPOINT ["node"]` + `CMD ["server.js"]` — the tool is fixed, the args are defaults. If you use only `CMD`, the whole thing is overridden by any command passed to `docker run`.

**Q: Why should COPY come after RUN npm install?**
> Layer caching. Docker caches each layer by hash. If you `COPY . .` first, any code change invalidates the cache for all subsequent instructions — including the slow `npm install`. Copying just `package.json`, running install, then copying the rest keeps the install layer cached across code edits.

**Q: What are multi-stage builds and why use them?**
> Multi-stage lets you use multiple `FROM` statements in one Dockerfile. You build in one stage with all the compilers and dev deps, then `COPY --from=builder` only the compiled artifact into a clean final stage. Result: much smaller production images with less attack surface — a Go app can go from 800 MB to 15 MB.

**Q: How do you reduce Docker image size?**
> Multi-stage builds, small base images (`-slim`, `-alpine`, distroless), combine `RUN` instructions to avoid extra layers, clean package manager caches in the same layer they were created, `.dockerignore` to keep junk out of the build context, and copy only what runtime needs.

**Q: Why should you run containers as non-root?**
> If an attacker escapes your app (RCE, container escape bug), they inherit the container's user. If that's root, they may be able to escalate to root on the host. Running as a non-root user via `USER` limits the blast radius. Kubernetes environments often enforce this via Pod Security Standards.

**Q: What's the difference between `RUN`, `CMD`, and `ENTRYPOINT`?**
> `RUN` executes at **build time** — its output is baked into a layer (e.g., installing packages). `CMD` and `ENTRYPOINT` execute at **runtime** — they define what process starts when the container runs. `RUN` shapes the image; `CMD`/`ENTRYPOINT` shape the container.

**Q: What is the build context?**
> The set of files sent from your machine to the Docker daemon when you run `docker build .`. Everything in that directory (minus `.dockerignore` patterns) is packaged and shipped. `COPY` can only copy files from the build context — not from anywhere on your filesystem.

---

## Mental Checkpoints Before Phase 4

Ask yourself:
- Can I write a Dockerfile for a Node/Python/Go app from scratch?
- Do I know why COPY order affects build speed?
- Can I explain CMD vs ENTRYPOINT with an example?
- Do I know when to use multi-stage builds?
- Can I pick a base image and defend the choice?
- Do I know why running as non-root matters?

If yes → ready for **Phase 4 (Data & Networking)**.

---

# Phase 4 — Data & Networking

**Goal:** Understand how containers store data that outlives them, and how they talk to each other and the outside world.

---

## 1. Why This Phase Matters

Containers are **ephemeral by design**. Delete the container → its filesystem is gone. That's fine for stateless apps, disastrous for databases.

Two hard questions Phase 4 answers:
1. **Where does my data live?** (Storage: volumes, bind mounts, tmpfs)
2. **How do containers reach each other and the internet?** (Networking: bridge, host, custom networks, DNS)

---

## 2. Container Filesystem — The Default Behavior

Recall from Phase 1: a container has a **writable layer on top of the image's read-only layers** (OverlayFS).

- Writes inside the container go to this writable layer.
- When you `docker rm` the container, the writable layer is **deleted**.
- If you want data to survive → you must mount external storage.

**Rule of thumb:** anything the container writes that you care about → put it in a volume or bind mount.

---

## 3. Storage Options — Three Ways to Persist Data

| Type | Where it lives | Managed by | Use case |
|---|---|---|---|
| **Volume** | `/var/lib/docker/volumes/...` (Docker-managed) | Docker | ✅ Default choice for prod data (DBs, uploads) |
| **Bind mount** | Any path on the host you choose | You (host filesystem) | ✅ Dev — live-reload source code from host |
| **tmpfs** | Host RAM (never touches disk) | Kernel | Secrets, scratch data — gone when container stops |

Visual:
```
┌────────────────── Container ──────────────────┐
│  App writes to /data                          │
│                                               │
│  /data ──► [ Volume    → Docker-managed dir ] │
│         or [ Bind mount → any host path     ] │
│         or [ tmpfs      → RAM               ] │
└───────────────────────────────────────────────┘
```

---

## 4. Volumes Deep Dive

**Volumes are the recommended way to persist data.** Docker manages them — you don't need to know or care where on the host they live.

### Commands
```bash
docker volume create mydata           # create explicitly
docker volume ls                      # list all volumes
docker volume inspect mydata          # see mountpoint, driver
docker volume rm mydata               # delete (only if unused)
docker volume prune                   # delete all unused volumes
```

### Using a volume
```bash
# Named volume (created on the fly if missing)
docker run -d --name pg \
  -v pgdata:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=secret \
  postgres:16
```
Now Postgres writes to `pgdata`, which lives outside the container. Delete the container, recreate it with the same `-v pgdata:...`, and your data is still there.

### Anonymous vs named volumes
- **Named:** `-v pgdata:/var/lib/postgresql/data` → easy to reference
- **Anonymous:** `-v /var/lib/postgresql/data` → Docker generates a random name, hard to reuse. Avoid.

### Modern `--mount` syntax (more explicit, preferred in production)
```bash
docker run --mount type=volume,source=pgdata,target=/var/lib/postgresql/data postgres:16
```

### Why volumes over bind mounts for prod?
- Host-path-independent (works the same on dev laptop and prod server)
- Docker manages permissions
- Backed up / migrated more cleanly
- Support volume drivers (NFS, cloud disks)

---

## 5. Bind Mounts Deep Dive

Mount a **specific host path** into the container. Great for development.

```bash
docker run -d --name web \
  -v $(pwd):/app \
  -p 3000:3000 \
  node:20 \
  node /app/server.js
```

Now edits to files in `$(pwd)` on the host are **instantly visible inside the container** → live reload without rebuilding the image.

### When to use bind mounts
- ✅ **Dev:** mount source code for fast iteration
- ✅ Mount config files (`nginx.conf`, `.env`)
- ❌ **Prod data:** avoid — couples your app to a specific host path

### Gotchas
- **Permissions:** the host's UID/GID may not match the container's user → "permission denied" errors. Fix with `--user $(id -u):$(id -g)` or `chown` in the Dockerfile.
- **Overwrites the container's dir:** if the container image had files at `/app` and you bind-mount host `$(pwd)` there, the image's `/app` is hidden — your host dir takes over. Bit them if the host dir is empty and the app expects files.

---

## 6. tmpfs Mounts

In-memory only. Never written to disk. Data disappears when container stops.

```bash
docker run --tmpfs /tmp:size=100M nginx
```

Use cases: caches, temporary files, secrets you don't want ever hitting disk.

---

## 7. Docker Networking — The Big Picture

When Docker installs, it creates a virtual network stack on your host:
- A **bridge** interface called `docker0` (a virtual switch)
- Each container gets its own **network namespace** (recall Phase 1) with its own IP
- Docker uses **iptables** rules to route traffic

```
        Internet
           │
    ┌──────▼──────┐
    │  Host eth0  │
    └──────┬──────┘
           │  iptables NAT (for -p)
    ┌──────▼──────┐
    │   docker0   │  (bridge, 172.17.0.1)
    └──┬────┬────┬┘
       │    │    │
   ┌───▼┐ ┌─▼──┐ ┌▼───┐
   │c1  │ │c2  │ │c3  │  (each has veth pair, own IP)
   │.2  │ │.3  │ │.4  │
   └────┘ └────┘ └────┘
```

You mostly don't need to know the low-level plumbing — but knowing it exists explains why containers on the same network can `curl` each other by name.

---

## 8. Network Drivers (Modes)

Docker supports several network types. You'll mostly use `bridge`.

| Driver | What it does | When to use |
|---|---|---|
| **bridge** (default) | Private virtual network on the host | Default for single-host multi-container apps |
| **host** | Container shares the host's network stack directly (no isolation) | When you need max network performance or listen on host ports directly |
| **none** | No network at all | Fully isolated containers (batch jobs, security tests) |
| **overlay** | Multi-host network (works across a cluster) | Docker Swarm / multi-node setups |
| **macvlan** | Container gets a real MAC + IP on your physical LAN | When you need containers to appear as physical devices |

### Default bridge vs user-defined bridge
Docker creates a default bridge network. **Don't use it.** It has weaknesses:
- No automatic DNS between containers (containers can only reach each other by IP)
- All containers on it can see each other (no isolation between apps)

Instead, always create your own:
```bash
docker network create mynet
docker run -d --name db --network mynet postgres:16
docker run -d --name web --network mynet myapp
# Now `web` can reach `db` via hostname `db`
```

---

## 9. User-Defined Bridge Networks (The Right Default)

### Why they're better
- **Automatic DNS:** container name → IP resolution. `web` can `curl http://db:5432`.
- **Isolation:** networks are scoped — containers on network A can't see network B.
- **Attach/detach at runtime:** `docker network connect mynet <container>`.

### Commands
```bash
docker network create mynet                    # create
docker network ls                              # list all networks
docker network inspect mynet                   # see IPs, connected containers
docker network connect mynet mycontainer       # attach a container
docker network disconnect mynet mycontainer    # detach
docker network rm mynet                        # delete (must be empty)
```

**Rule:** For any multi-container app, create a network for it and put everything on that network.

---

## 10. Port Publishing (-p) vs Exposing

Two different concepts that people conflate.

### `EXPOSE` (Dockerfile) — Documentation
```dockerfile
EXPOSE 3000
```
Just declares: "this image intends to listen on port 3000". Does **not** open any port. Useful for readers of the Dockerfile and for `docker run -P` (see below).

### `-p host:container` (docker run) — Actually publishes
```bash
docker run -p 8080:3000 myapp
```
Docker adds an iptables NAT rule: host port 8080 → container port 3000. **Now** you can `curl http://localhost:8080`.

### `-P` — Publish all EXPOSEd ports to random host ports
```bash
docker run -P nginx
docker ps      # shows something like 0.0.0.0:32768->80/tcp
```
Useful for tests where you don't care about the host port.

### Binding to a specific host IP
```bash
-p 127.0.0.1:8080:3000    # only reachable from localhost
-p 0.0.0.0:8080:3000      # reachable from anywhere (default)
```

**Security tip:** in prod, binding to `127.0.0.1` and putting a reverse proxy (nginx/traefik) in front is safer than exposing services directly.

---

## 11. Container-to-Container Communication

On a user-defined bridge network, **the container name is the hostname**.

Example: `web` and `db` are on network `mynet`:
- From `web`: `curl http://db:5432` works
- From `db`: `curl http://web:3000` works

Docker runs an embedded DNS server that resolves container names → IPs on the same network.

### `--link` — Legacy, avoid
`--link` was the old way to connect containers. **Deprecated.** Use user-defined networks instead.

### Cross-network isolation
```bash
docker network create frontend
docker network create backend

docker run -d --name web --network frontend myweb
docker run -d --name api --network frontend --network backend myapi
docker run -d --name db --network backend postgres
```
- `web` ↔ `api` ✅ (both on `frontend`)
- `api` ↔ `db` ✅ (both on `backend`)
- `web` ↔ `db` ❌ (no shared network)

This is how you enforce that only the API can reach the DB, not the frontend.

---

## 12. A Complete Example — App + Postgres

Put it all together:
```bash
# 1. Create a network
docker network create appnet

# 2. Create a volume for DB data
docker volume create pgdata

# 3. Start Postgres, attached to network, using volume
docker run -d \
  --name db \
  --network appnet \
  -v pgdata:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=myapp \
  postgres:16

# 4. Start your app, connecting to `db` by hostname
docker run -d \
  --name web \
  --network appnet \
  -p 8080:3000 \
  -e DATABASE_URL=postgres://postgres:secret@db:5432/myapp \
  myapp:v1
```

What this achieves:
- `web` reaches `db` by hostname (DNS via user-defined bridge)
- DB data survives container deletion (`pgdata` volume)
- Outside world reaches `web` on host port 8080 (`-p`)
- DB is **not** exposed to the outside world (no `-p` on the `db` container) — reachable only from other containers on `appnet`

---

## 13. Practice Exercises

1. **Volume persistence test:** Run Postgres with a named volume. Insert some data. `docker rm -f` the container. Start a new Postgres pointing to the same volume. Confirm data is there.
2. **Bind mount live reload:** Create a simple Node/Python file that serves a message. Run it with `-v $(pwd):/app`. Edit the file on the host, restart the container, see the change.
3. **Custom network + DNS:** Create `mynet`. Run two containers on it. From one, `ping` the other by name. Then try from a container **not** on `mynet` → should fail.
4. **Port mapping variations:** Run nginx three ways: `-p 8080:80`, `-p 127.0.0.1:8080:80`, `-P`. Compare `docker ps` output. Try to reach each from another machine on your LAN.
5. **Network isolation:** Create `frontend` and `backend` networks. Set up `web` → `api` → `db` where `web` can't reach `db` directly.
6. **Cleanup:** `docker volume ls`, `docker network ls`. Prune everything unused.
7. **Inspect:** After running the App + Postgres example, run `docker network inspect appnet` — find the IPs Docker assigned. Then `docker exec -it web sh` and try `nslookup db`.

---

## 14. Interview Answers (Phase 4)

**Q: What's the difference between a volume and a bind mount?**
> A **volume** is Docker-managed storage — Docker picks the host location (`/var/lib/docker/volumes/...`) and handles it for you. A **bind mount** maps a specific host path into the container. Volumes are portable and recommended for production data. Bind mounts are useful in development for live-reloading source code from the host.

**Q: What happens to data written inside a container when the container is deleted?**
> Everything written to the writable layer is **lost**. Only data written to a mounted volume or bind mount survives. This is why databases must always use volumes.

**Q: What's the difference between EXPOSE and -p?**
> `EXPOSE` is metadata in the Dockerfile — it documents which ports the image intends to use but doesn't publish anything. `-p host:container` at `docker run` time is what actually creates the iptables NAT rule that forwards a host port to the container. You need `-p` for the outside world to reach the container.

**Q: How do two containers on the same host talk to each other?**
> Put them on the **same user-defined bridge network**. Docker runs an embedded DNS server, so each container can reach the others by container name (e.g., `curl http://db:5432`). The default bridge network doesn't have DNS between containers — always create your own.

**Q: Why avoid the default bridge network?**
> No DNS between containers (must use IPs which change), no isolation from other containers on the default bridge, no clean way to scope which apps can see each other. User-defined bridge networks fix all three.

**Q: What's an overlay network?**
> A network that spans multiple Docker hosts. Used in Docker Swarm and other multi-node setups so containers running on different physical machines can talk as if on the same network. For single-host setups, you don't need it.

**Q: How would you make a Postgres database persist across container restarts?**
> Mount a named volume at `/var/lib/postgresql/data`: `docker run -v pgdata:/var/lib/postgresql/data postgres`. Even if you delete and recreate the container, as long as you re-mount `pgdata`, the data is there.

**Q: Container A needs to talk to container B, but not to container C. How?**
> Put A and B on one user-defined network, and A and C (or B and C) on separate networks. A container can be attached to multiple networks — this is how you enforce that only certain services can reach certain others (e.g., web can reach API, API can reach DB, but web can't reach DB directly).

---

## Mental Checkpoints Before Phase 5

Ask yourself:
- Do I know why container filesystem changes are lost by default?
- Can I explain volume vs bind mount vs tmpfs and when to use each?
- Can I set up two containers that talk to each other by name?
- Do I know the difference between EXPOSE and -p?
- Can I set up network-level isolation between services?
- Can I run a stateful service (Postgres) with data that survives container deletion?

If yes → ready for **Phase 5 (Docker Compose — multi-container apps declaratively)**.

---

# Phase 5 — Docker Compose

**Goal:** Replace long `docker run` chains with a single YAML file that describes your entire multi-container application. Bring the whole stack up with one command.

---

## 1. Why Compose Exists

Recall the Phase 4 example: to run a web app + Postgres, you needed 4 commands (create network, create volume, run db, run web) — and remembering all the flags is error-prone.

Compose lets you declare the same thing in a `docker-compose.yml` file, then:
```bash
docker compose up -d
```
Starts everything. `docker compose down` tears it all down. Reproducible, version-controlled, shareable.

**Compose is for:**
- ✅ Local development (spin up your app + all its dependencies)
- ✅ CI pipelines (integration tests with real services)
- ✅ Small single-host deployments
- ❌ Large-scale production across many machines → use Kubernetes

### `docker compose` vs `docker-compose`
- `docker compose` (space, modern V2) — built into Docker CLI, written in Go
- `docker-compose` (hyphen, legacy V1) — separate Python tool, deprecated

Use the space version. All examples here use it.

---

## 2. Anatomy of a docker-compose.yml

Simplest possible file:
```yaml
services:
  web:
    image: nginx
    ports:
      - "8080:80"
```

Run:
```bash
docker compose up -d
curl http://localhost:8080
docker compose down
```

Realistic file with 3 services:
```yaml
services:
  web:
    build: .                    # build from Dockerfile in current dir
    ports:
      - "3000:3000"
    environment:
      DATABASE_URL: postgres://postgres:secret@db:5432/myapp
      REDIS_URL: redis://cache:6379
    depends_on:
      - db
      - cache

  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: myapp
    volumes:
      - pgdata:/var/lib/postgresql/data

  cache:
    image: redis:7-alpine

volumes:
  pgdata:
```

Compose automatically creates:
- A **network** (named `<projectname>_default`) with all services on it
- **DNS entries** so `web` can reach `db` and `cache` by service name
- The `pgdata` **volume**

No need to write `docker network create` or `docker volume create`.

---

## 3. Service Configuration Reference

The keys you'll actually use:

| Key | Purpose | Example |
|---|---|---|
| `image` | Use a prebuilt image | `image: postgres:16` |
| `build` | Build from a Dockerfile | `build: .` or `build: {context: ., dockerfile: Dockerfile.dev}` |
| `container_name` | Override auto-generated name | `container_name: my-db` |
| `ports` | Publish ports (like `-p`) | `- "8080:80"` |
| `environment` | Env vars | `KEY: value` (map) or `- KEY=value` (list) |
| `env_file` | Load env from file | `env_file: .env` |
| `volumes` | Mount volumes/bind mounts | `- pgdata:/var/lib/postgresql/data` |
| `networks` | Attach to specific networks | `- backend` |
| `depends_on` | Startup order | `- db` |
| `restart` | Restart policy | `unless-stopped` |
| `command` | Override CMD | `command: node server.js` |
| `entrypoint` | Override ENTRYPOINT | `entrypoint: /entrypoint.sh` |
| `healthcheck` | Container health probe | see section 8 |
| `deploy.resources.limits` | CPU/memory limits | see below |
| `profiles` | Optional services | `profiles: ["debug"]` |
| `user` | Run as UID:GID | `user: "1000:1000"` |

**Resource limits example:**
```yaml
services:
  web:
    image: myapp
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 256M
```

---

## 4. Everyday Compose Commands

| Command | What it does |
|---|---|
| `docker compose up` | Start all services (foreground, streams logs) |
| `docker compose up -d` | Start in detached mode |
| `docker compose up --build` | Rebuild images before starting |
| `docker compose down` | Stop and remove containers + networks (keeps volumes) |
| `docker compose down -v` | Also remove volumes (destroys data) |
| `docker compose ps` | List running services |
| `docker compose logs` | Tail all service logs |
| `docker compose logs -f web` | Follow logs for one service |
| `docker compose exec web sh` | Shell into a running service |
| `docker compose run --rm web npm test` | Run a one-off command |
| `docker compose restart web` | Restart one service |
| `docker compose stop` | Stop without removing |
| `docker compose build` | Build images without starting |
| `docker compose config` | Validate and show the resolved config |
| `docker compose pull` | Pull latest images for all services |

**Difference: `exec` vs `run`**
- `exec` → runs a command in an **already running** container
- `run` → creates a **new** container from the image just for this command (useful for tests, migrations)

---

## 5. Environment Variables and .env Files

Compose supports variable substitution in the YAML.

**`.env` file (in same directory as `docker-compose.yml`):**
```
POSTGRES_PASSWORD=supersecret
APP_PORT=3000
IMAGE_TAG=v1.2.3
```

**`docker-compose.yml`:**
```yaml
services:
  web:
    image: myapp:${IMAGE_TAG}
    ports:
      - "${APP_PORT}:3000"
  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

Compose auto-loads `.env` from the project directory. Any `${VAR}` in the YAML is replaced at parse time.

**Two very different env concepts — don't confuse them:**
- `.env` (top-level) → variables for **Compose to substitute into the YAML**
- `env_file:` inside a service → variables **passed to the container's environment**

**Security:** always add `.env` to `.gitignore` and `.dockerignore`. Commit a `.env.example` with placeholder values instead.

---

## 6. Volumes and Networks in Compose

### Volumes
```yaml
services:
  db:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data       # named volume
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql  # bind mount

volumes:
  pgdata:                                      # declare the volume
```

Named volumes must be declared in the top-level `volumes:` block. Bind mounts don't need declaration.

### Networks
By default, Compose puts all services on a single network. To create multiple networks (e.g., for isolation):

```yaml
services:
  web:
    image: myweb
    networks:
      - frontend
  api:
    image: myapi
    networks:
      - frontend
      - backend
  db:
    image: postgres:16
    networks:
      - backend

networks:
  frontend:
  backend:
```

Result: `web` ↔ `api` (frontend), `api` ↔ `db` (backend), but `web` cannot reach `db`.

---

## 7. depends_on and Startup Ordering (The Classic Trap)

`depends_on` controls **start order**, not **readiness**.

```yaml
services:
  web:
    build: .
    depends_on:
      - db
  db:
    image: postgres:16
```

Compose starts `db` before `web`. **But** — `db` container being *started* doesn't mean Postgres is *accepting connections*. Postgres takes a few seconds to initialize. Your `web` app may crash on startup trying to connect to a not-yet-ready DB.

### The fix — depends_on with healthcheck condition
```yaml
services:
  web:
    build: .
    depends_on:
      db:
        condition: service_healthy
  db:
    image: postgres:16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5
```
Now Compose waits until Postgres's healthcheck passes before starting `web`.

**Even better:** make your app retry the DB connection on startup. Healthchecks help, but resilience to transient failures should be built into the app anyway.

---

## 8. Healthchecks

A healthcheck runs periodically inside the container to determine if it's alive.

```yaml
services:
  web:
    image: myapp
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/health"]
      interval: 30s      # how often to check
      timeout: 10s       # max time per check
      retries: 3         # failures before marked unhealthy
      start_period: 40s  # grace period at startup (failures don't count)
```

`docker ps` will show `(healthy)` or `(unhealthy)` in the status column.

Test forms:
- `["CMD", "curl", "-f", "http://localhost/"]` — exec form, no shell
- `["CMD-SHELL", "curl -f http://localhost/ || exit 1"]` — shell form, supports `||`, `&&`, pipes

---

## 9. Profiles and Overrides

### Profiles — Optional services
Tag services that shouldn't start by default:
```yaml
services:
  web:
    image: myapp
  db:
    image: postgres:16
  debug-tools:
    image: nicolaka/netshoot
    profiles: ["debug"]
```
`docker compose up` → only starts `web` and `db`.
`docker compose --profile debug up` → also starts `debug-tools`.

Useful for: debug tools, seed-data loaders, optional monitoring stacks.

### Overrides — dev vs prod variants
Compose auto-merges `docker-compose.yml` + `docker-compose.override.yml` when both exist.

**`docker-compose.yml` (base, prod-like):**
```yaml
services:
  web:
    image: myapp:v1
    restart: unless-stopped
```

**`docker-compose.override.yml` (dev-only, gitignored or not):**
```yaml
services:
  web:
    build: .                    # build locally instead of using image
    volumes:
      - .:/app                  # live-reload source
    environment:
      NODE_ENV: development
    command: npm run dev
```

Or use explicit files:
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Common convention: `docker-compose.yml` (shared), `docker-compose.override.yml` (dev), `docker-compose.prod.yml` (prod).

---

## 10. Build vs Image

Two ways to source a service's image:

**Prebuilt (from a registry):**
```yaml
services:
  db:
    image: postgres:16
```

**Build from a Dockerfile:**
```yaml
services:
  web:
    build: .                    # Dockerfile in current dir
```

**Both — build locally but tag it:**
```yaml
services:
  web:
    build: .
    image: myapp:local          # tag the built image
```

**Advanced build config:**
```yaml
services:
  web:
    build:
      context: ./web            # path sent as build context
      dockerfile: Dockerfile.prod
      args:
        VERSION: 1.2.3          # build-time ARG values
      target: production        # for multi-stage: pick a stage
```

---

## 11. A Complete Real-World Stack

Web app + Postgres + Redis + Nginx reverse proxy:

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      web:
        condition: service_healthy
    restart: unless-stopped

  web:
    build: .
    environment:
      DATABASE_URL: postgres://postgres:${DB_PASSWORD}@db:5432/${DB_NAME}
      REDIS_URL: redis://cache:6379
      NODE_ENV: production
    depends_on:
      db:
        condition: service_healthy
      cache:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 20s
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: ${DB_NAME}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  cache:
    image: redis:7-alpine
    volumes:
      - redisdata:/data
    restart: unless-stopped

volumes:
  pgdata:
  redisdata:
```

With a `.env`:
```
DB_PASSWORD=changeme
DB_NAME=myapp
```

Start it: `docker compose up -d`. One command brings up the whole app.

What this stack does right:
- Reverse proxy → clean single entry point on port 80
- DB and cache **not exposed** to host → only reachable from other services
- Healthchecks + `condition: service_healthy` → correct startup order
- Named volumes → data survives restarts
- Restart policies → recovery from crashes
- Env vars from `.env` → no secrets in YAML

---

## 12. Practice Exercises

1. **Compose your Phase 4 setup:** Rewrite the App + Postgres example from Phase 4 as a `docker-compose.yml`. Verify `docker compose up -d` gives the same result.
2. **Add a service:** Add Redis to your Compose file. Confirm the app can reach it as `redis:6379`.
3. **Break startup ordering:** Remove healthchecks and `condition: service_healthy`. Watch your app crash on startup because DB isn't ready. Add them back.
4. **Dev vs prod override:** Create `docker-compose.override.yml` that adds a bind mount for source code. Run once with override, once without (`docker compose -f docker-compose.yml up`) and observe the difference.
5. **Profiles:** Add a `debug` profile with `nicolaka/netshoot`. Confirm it doesn't start by default; start it with `--profile debug`.
6. **Config validation:** Break something in the YAML on purpose (bad indentation). Run `docker compose config` — see the error.
7. **One-off commands:** `docker compose run --rm web sh -c "npm test"`. Confirm the container is gone after.
8. **Full cleanup:** `docker compose down -v --rmi all`. Then `docker compose up -d` to rebuild from scratch.

---

## 13. Interview Answers (Phase 5)

**Q: What is Docker Compose and why use it?**
> Compose is a tool for defining multi-container applications in a single YAML file. Instead of typing long `docker run` commands and remembering flag combinations, you declare services, volumes, networks, and dependencies in `docker-compose.yml`, then `docker compose up` brings the whole stack up. It's ideal for local dev, integration testing, and small single-host deployments.

**Q: What's the difference between `depends_on` and a healthcheck-based startup condition?**
> Plain `depends_on: [db]` only controls **start order** — Compose starts `db` before `web`, but doesn't wait for `db` to be ready. `depends_on.db.condition: service_healthy` combined with a healthcheck on `db` waits until the healthcheck passes before starting `web`. Without this, apps often crash on startup connecting to a not-yet-ready dependency.

**Q: How does Compose handle networking between services?**
> Compose creates a default network for the project and attaches every service to it. Services can reach each other by service name (e.g., `web` → `http://db:5432`) thanks to Docker's built-in DNS. You can define multiple networks in the YAML for isolation — e.g., putting DB on a `backend` network that the frontend can't reach.

**Q: What's the difference between `docker compose exec` and `docker compose run`?**
> `exec` runs a command in an **already running** container. `run` creates a **new** container from the image just for that command (like `docker run`). Use `exec` for shells into live services; use `run --rm` for one-off tasks like tests or migrations.

**Q: How do you handle dev vs prod configuration in Compose?**
> Use override files. `docker-compose.yml` holds shared config; `docker-compose.override.yml` (auto-loaded) holds dev-only tweaks like bind mounts and dev commands. For prod, use `docker-compose -f docker-compose.yml -f docker-compose.prod.yml up`. Environment-specific values come from `.env` files, not hardcoded in YAML.

**Q: Where should you use Compose vs Kubernetes?**
> Compose is great for local dev, CI, and small single-host deployments (one server, a handful of services). Kubernetes takes over when you need multi-node scheduling, self-healing across machines, auto-scaling, rolling updates, and production-grade orchestration. Many teams use Compose in dev and Kubernetes in prod, with the Compose file serving as living documentation.

**Q: What happens to volumes when you run `docker compose down`?**
> Named volumes **persist** — `down` removes containers and networks but keeps data. To also delete volumes, use `docker compose down -v` (destroys the DB data, so be careful). This is the difference between "restart the stack" and "wipe the stack".

---

## Mental Checkpoints Before Phase 6

Ask yourself:
- Can I convert a set of `docker run` commands into a Compose file?
- Do I know why `depends_on` alone isn't enough, and when to add healthchecks?
- Can I set up multiple networks in Compose for service isolation?
- Do I know the difference between `.env` (substituted into YAML) and `env_file` (passed to containers)?
- Can I use overrides to have different dev and prod configs from the same base?
- Can I bring up a 4-service stack (proxy + app + db + cache) with one command?

If yes → ready for **Phase 6 (Production Concerns — security, secrets, logging, monitoring)**.

---

# Phase 6 — Production Concerns

**Goal:** Bridge the gap between "it works on my laptop" and "it runs safely and reliably in production". This is where senior engineers earn their money.

---

## 1. The Dev-to-Prod Gap

Dev containers are permissive: root user, all secrets in `.env`, chatty logs, unlimited RAM, `:latest` tag. Prod is the opposite: least privilege, secrets in a vault, structured logs, resource limits, immutable versioned tags.

Every production Docker checklist boils down to answering these five questions:

1. **Is the image minimal?** (small, no build tools, no attack surface)
2. **Is it running with least privilege?** (non-root, dropped capabilities, read-only FS)
3. **Are secrets kept out of the image?** (mounted at runtime, not baked in)
4. **Can I observe it?** (logs, metrics, healthchecks)
5. **Can I reproduce and roll back?** (versioned tags, image digests, CI-built)

The rest of Phase 6 walks each one.

---

## 2. Image Optimization

Smaller images = faster pulls, less disk, smaller attack surface, faster autoscaling. All the techniques you already know from Phase 3 apply, tuned for prod:

- **Multi-stage builds** — never ship compilers, dev deps, or test tools to prod
- **Small base images** — `alpine`, `-slim`, or `distroless` (favor distroless for prod)
- **Combine RUN + clean in same layer** — apt/apk caches deleted where they were created
- **`.dockerignore`** — prevents `.git`, `node_modules`, `.env`, IDE junk from bloating context
- **Use `--no-install-recommends`** on apt to skip optional packages
- **`npm ci --omit=dev`** / `pip install --no-cache-dir` / equivalents

**Sanity check:** run `docker history <image>` on your prod image and look for the biggest layers. If any layer is > 100 MB and you're not sure why, dig in.

**Rule of thumb sizes:**
- Go / Rust static binary → 5–20 MB
- Node / Python (alpine, prod deps only) → 100–200 MB
- Anything > 500 MB in prod → investigate

---

## 3. Security — Run as Non-Root

By default, container processes run as **root inside the container**. If an attacker gets code execution and Docker has any misconfiguration (bad kernel version, wrong mount, capability, etc.), root-in-container can become root-on-host.

**Always add a `USER` directive:**
```dockerfile
FROM node:20-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --chown=app:app . .
USER app                        # everything below runs as `app`
CMD ["node", "server.js"]
```

Verify: `docker exec -it <name> whoami` → should print `app`, not `root`.

**Gotcha:** if your app needs to bind port < 1024 (like 80), it can't as non-root. Fix: bind to 3000/8080 in the container and let the host `-p 80:8080` map it — the host's port binding happens **before** the container's user check.

Kubernetes environments (Pod Security Standards) increasingly **reject** containers running as root — so make this a habit now.

---

## 4. Security — Image Scanning

Every image inherits vulnerabilities from its base image and dependencies. Scan before you ship.

### Tools
| Tool | Notes |
|---|---|
| `docker scout` | Built into Docker Desktop; fast |
| **Trivy** (Aqua) | Open source, industry favorite, scans OS packages + language deps |
| Snyk | Commercial, deep dep analysis |
| Grype (Anchore) | Open source, good for CI |

### Example — Trivy
```bash
trivy image myapp:v1
```
Output: CVEs by severity (CRITICAL, HIGH, MEDIUM, LOW), fix versions if available.

### CI pattern
Fail the build on unfixed HIGH/CRITICAL CVEs:
```bash
trivy image --exit-code 1 --severity HIGH,CRITICAL myapp:${GIT_SHA}
```

### Reduce noise
- Choose slim/distroless bases → fewer packages → fewer CVEs
- Rebuild periodically to pick up base-image patches (even without code changes)
- Pin your base image to a specific digest for reproducibility: `FROM node:20-alpine@sha256:abc...`

---

## 5. Security — Image Signing and Provenance

How do you know the image you pulled is the one you built?

- **Docker Content Trust (DCT)** — legacy Notary-based signing
- **Cosign (Sigstore)** — modern, keyless (uses OIDC), the current industry direction
- **SBOM (Software Bill of Materials)** — machine-readable inventory of what's in the image; tools: Syft, Docker BuildKit's `--attest`
- **SLSA / Provenance attestations** — cryptographic proof of how an image was built

You don't need this on day one — but in a regulated org or a mature CI pipeline, signed images and SBOMs are becoming table stakes.

Basic Cosign flow:
```bash
cosign sign myregistry/myapp:v1
cosign verify myregistry/myapp:v1 --certificate-identity=... --certificate-oidc-issuer=...
```

---

## 6. Security — Runtime Hardening

Even with a good image, harden how it runs:

### Read-only root filesystem
```bash
docker run --read-only --tmpfs /tmp myapp
```
The image's filesystem becomes immutable. If the app needs to write, mount specific writable areas (`/tmp`, `/var/run`) via tmpfs or volumes.

### Drop Linux capabilities
By default, containers get a lot of capabilities. Drop them all, add back only what you need:
```bash
docker run --cap-drop=ALL --cap-add=NET_BIND_SERVICE myapp
```

### Prevent privilege escalation
```bash
docker run --security-opt no-new-privileges myapp
```
Blocks the container from gaining more privileges via setuid binaries.

### Never mount the Docker socket into containers
```bash
# DANGEROUS — do not do this in prod
-v /var/run/docker.sock:/var/run/docker.sock
```
Anything with the Docker socket can create privileged containers on the host = full root. If you truly need it (CI runners), understand the risk and isolate the host.

### Compose equivalents
```yaml
services:
  web:
    image: myapp
    user: "1000:1000"
    read_only: true
    tmpfs:
      - /tmp
    cap_drop: ["ALL"]
    cap_add: ["NET_BIND_SERVICE"]
    security_opt:
      - "no-new-privileges:true"
```

---

## 7. Secrets Management

**Never bake secrets into the image.** Anyone who can pull the image can extract them (`docker history`, `docker save`, layer inspection).

### Bad ideas
- `ENV DB_PASSWORD=xxx` in Dockerfile → baked in forever
- `COPY .env /app/.env` → baked in
- Passing via `--build-arg` for a secret → still visible in build history

### Good options (from simple to serious)

**Runtime env vars (simplest):**
```bash
docker run -e DB_PASSWORD="$DB_PASSWORD" myapp
```
Or `env_file:` in Compose (with `.env` gitignored). Fine for small setups, but env vars are visible via `docker inspect` and process lists on the host.

**Docker Secrets (Swarm / Compose in Swarm mode):**
```yaml
services:
  web:
    image: myapp
    secrets:
      - db_password
secrets:
  db_password:
    file: ./secrets/db_password.txt
```
Mounted as a file inside the container (`/run/secrets/db_password`) — not visible in env.

**BuildKit build secrets (for build-time secrets like private repo tokens):**
```dockerfile
# syntax=docker/dockerfile:1
RUN --mount=type=secret,id=npm_token \
    NPM_TOKEN=$(cat /run/secrets/npm_token) npm ci
```
```bash
DOCKER_BUILDKIT=1 docker build --secret id=npm_token,src=./npm_token.txt .
```
The secret is available during that RUN only — not baked into any layer.

**External secret managers (real production):**
- HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager, Azure Key Vault
- App fetches secrets at startup via SDK/API
- In Kubernetes: External Secrets Operator, sealed-secrets, SPIFFE/SPIRE

Rule: **the image should be identical across environments; only injected secrets differ.**

---

## 8. Resource Limits and Restart Policies

Without limits, one container OOM can kill the host or starve others.

### Limits (cgroups)
```bash
docker run \
  --memory=512m \
  --memory-swap=512m \
  --cpus=1.5 \
  --pids-limit=200 \
  myapp
```

Compose:
```yaml
services:
  web:
    deploy:
      resources:
        limits:
          cpus: "1.5"
          memory: 512M
        reservations:      # soft guarantee
          memory: 256M
```

Rule: **always set memory limits in prod**. CPU limits are more nuanced — sometimes throttling hurts more than it helps. Start with memory only.

### Restart policies
| Policy | When to restart |
|---|---|
| `no` (default) | Never |
| `on-failure` | Only on non-zero exit |
| `on-failure:5` | Non-zero exit, up to 5 times |
| `always` | Restart no matter what, even on `docker stop` after daemon restart |
| `unless-stopped` | Restart always, **except** if you `docker stop`ped it manually |

**Use `unless-stopped` in prod.** Survives host reboots, respects manual stops.

---

## 9. Logging (12-Factor)

12-factor app principle: **logs are streams**. The app writes to stdout/stderr; something else routes them.

- ✅ App writes JSON logs to stdout
- ❌ App writes to `/var/log/myapp.log` inside the container

Docker captures stdout/stderr and hands them to a **log driver**.

### Log drivers
| Driver | Where logs go |
|---|---|
| `json-file` (default) | `/var/lib/docker/containers/<id>/*-json.log` on host |
| `local` | Local, but binary format, more efficient than json-file |
| `journald` | systemd journal |
| `syslog` | syslog server |
| `fluentd` | Fluentd/Fluent Bit collector |
| `gelf` | Graylog |
| `awslogs` | AWS CloudWatch |
| `gcplogs` | Google Cloud Logging |
| `none` | Disable capture |

Configure globally (`/etc/docker/daemon.json`):
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

**Common prod gotcha:** without `max-size` / `max-file`, `json-file` logs grow forever and fill the disk. Always set rotation limits.

### Structured logging
Emit JSON logs from your app so downstream tools (Loki, ELK, Datadog) can parse them:
```json
{"ts":"2026-09-13T10:00:00Z","level":"info","msg":"request","method":"GET","path":"/users","duration_ms":42}
```
Much easier to query than plain text.

---

## 10. Monitoring and Metrics

Three things you want visibility on:

**1. Container-level (Docker itself)**
- `docker stats` — live CPU/mem/net/IO per container
- **cAdvisor** — Google's container metrics collector; exposes Prometheus metrics
- **Prometheus + Grafana** — scrape cAdvisor + your app; dashboards + alerts

**2. Application-level (your code)**
- Expose `/metrics` in Prometheus format (histograms of latency, counters of requests, gauges of queue depth)
- Distributed tracing: OpenTelemetry → Jaeger / Tempo / Datadog

**3. Log aggregation**
- Loki + Grafana, ELK stack, or cloud-native (CloudWatch, GCP Logging, Datadog)

**Minimum viable prod observability:**
- App emits structured JSON logs → shipped to a log aggregator
- App exposes Prometheus metrics
- cAdvisor + node-exporter on each host → Prometheus → Grafana dashboards
- Alerts on: high error rate, high latency, OOM kills, restart loops, disk fill

---

## 11. Registries in Production

Public Docker Hub is fine for playing around; **don't ship prod from it**. It's rate-limited and images can disappear.

### Options
| Registry | Notes |
|---|---|
| **AWS ECR** | Deep AWS integration, IAM auth |
| **Google Artifact Registry / GCR** | GCP-native |
| **Azure Container Registry (ACR)** | Azure-native |
| **GitHub Container Registry (GHCR)** | Free, tied to GitHub Actions |
| **Harbor** | Self-hosted, adds RBAC, scanning, replication |
| **Docker Hub** (private repos) | Simple, has rate limits |

### Auth
```bash
docker login ghcr.io -u USER -p $GHCR_TOKEN
docker push ghcr.io/myorg/myapp:v1
```
In CI, use short-lived OIDC tokens (GitHub Actions → ECR via role assumption) instead of long-lived credentials.

### Pull-through cache
For high-throughput setups, run a caching mirror (Harbor, distribution) close to your compute so you're not pulling from the internet on every deploy.

---

## 12. Image Tagging Strategy

`:latest` is a trap in production. It's mutable — the image behind that tag can change without warning, breaking reproducibility and rollbacks.

### Good tagging patterns
Combine multiple tags on the same image:
- `myapp:1.4.2` — semver release
- `myapp:1.4` and `myapp:1` — floating semver aliases
- `myapp:a1b2c3d` — git short SHA (immutable, most useful for CI/rollback)
- `myapp:main` — the tip of a branch (dev environments only)
- `myapp:2026-09-13` — date-based

### Pinning by digest — the strongest guarantee
```
myapp@sha256:abcdef1234...
```
A digest is immutable — same digest = byte-identical image, always. Best for production deployments.

### Rules of thumb
- **Never** `:latest` in prod manifests / Kubernetes yaml
- Deploy by **git SHA** or **semver**, not by branch name
- Retain images for at least the lifetime of anything running them (needed for rollback)

---

## 13. CI/CD with Docker

The canonical pipeline:

```
git push
   │
   ▼
CI runs tests
   │
   ▼
CI builds image
   │       docker build -t registry/myapp:${GIT_SHA} .
   ▼
CI scans image (Trivy)
   │       fail if HIGH/CRITICAL
   ▼
CI pushes to registry
   │       docker push registry/myapp:${GIT_SHA}
   ▼
Deploy step
   │       kubectl set image ... or docker compose up on server
   ▼
Verify (healthcheck / smoke test)
   │
   ▼
Alert on failure, easy rollback (previous SHA still in registry)
```

### GitHub Actions snippet
```yaml
- uses: docker/setup-buildx-action@v3
- uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
- uses: docker/build-push-action@v6
  with:
    context: .
    push: true
    tags: |
      ghcr.io/${{ github.repository }}:${{ github.sha }}
      ghcr.io/${{ github.repository }}:latest
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

### BuildKit — use it
`docker buildx` / BuildKit gives you:
- Parallel stage builds
- Better caching (`--cache-from`, `--cache-to`)
- Multi-arch builds (`--platform linux/amd64,linux/arm64`) for M-series Macs + x86 servers
- Build secrets (section 7)

Enable: `DOCKER_BUILDKIT=1` (default in modern Docker).

---

## 14. Practice Exercises

1. **Shrink an image:** Take a Dockerfile from Phase 3. Add multi-stage, switch to alpine/distroless, run `docker history`. Aim for < 100 MB.
2. **Add non-root user:** Modify a Dockerfile to run as non-root. Verify with `docker exec whoami`. Confirm the app still works.
3. **Scan an image:** Install Trivy. Scan an old base image (e.g., `node:16`) — see the CVEs. Scan the modern equivalent (`node:20-alpine`) — compare.
4. **Read-only runtime:** Run your app with `--read-only`. Fix any errors by mounting tmpfs for writable paths.
5. **Enforce memory limit:** Run a small memory-hogging script in a container with `--memory=100m`. Watch it get OOM-killed. Check with `docker inspect` for `OOMKilled: true`.
6. **Log rotation:** Configure `/etc/docker/daemon.json` with `max-size` and `max-file`. Restart the daemon. Verify logs rotate.
7. **Tag by SHA:** Build an image tagged with `git rev-parse --short HEAD`. Push to a registry (GHCR or local). Deploy by that SHA.
8. **Secret injection:** Store a "password" in a file. Mount it via Docker Secrets (Swarm mode) or BuildKit `--secret`. Confirm it never appears in `docker history` or the final image.

---

## 15. Interview Answers (Phase 6)

**Q: How do you keep production Docker images small and secure?**
> Multi-stage builds so build tools never ship. Small base images (alpine, distroless, or `-slim`). Combine RUN with cleanup in the same layer. `.dockerignore` to keep junk out. Run as a non-root user via `USER`. Scan with Trivy in CI and fail on high-severity CVEs. Rebuild periodically to pick up base-image patches. Pin base images by digest for reproducibility.

**Q: How should secrets be handled in Docker?**
> Never bake them into the image — they'd be visible via `docker history` or layer inspection. Inject at runtime instead: environment variables for simple cases, Docker Secrets (mounted as files at `/run/secrets/`) for Swarm, BuildKit `--mount=type=secret` for build-time secrets, and an external secrets manager (Vault, AWS Secrets Manager) for real production. The image is identical across environments; only injected secrets differ.

**Q: Why not use `:latest` in production?**
> `latest` is a mutable tag — the image behind it can change silently. That breaks reproducibility (two deploys of "latest" might be different images) and makes rollback ambiguous. Use immutable tags: semver (`1.4.2`) or git SHA (`a1b2c3d`), or pin by digest (`@sha256:...`) for the strongest guarantee.

**Q: How do Docker logs work and what should you configure in prod?**
> Container stdout/stderr is captured by the log driver — `json-file` by default. In production, set `max-size` and `max-file` in `/etc/docker/daemon.json` to prevent logs from filling the disk. For real observability, ship logs to an aggregator (Loki, ELK, CloudWatch) via a log driver like `fluentd`. Apps should emit structured JSON to stdout, never write log files inside the container.

**Q: How do you set resource limits and why?**
> `--memory` and `--cpus` on `docker run`, or the `deploy.resources.limits` block in Compose. They're enforced by cgroups. Always set memory limits in production — without them, one runaway container can OOM the host and take down every other container on it. CPU limits are more nuanced (throttling can hurt latency) — set memory first, add CPU limits only when needed.

**Q: What's the right restart policy for a production service?**
> `unless-stopped`. It restarts the container on crash and after the host reboots, but respects your manual `docker stop`. `always` restarts even after manual stops, which usually isn't what you want. `on-failure` is fine for batch jobs but stops after non-failure exits.

**Q: What are the security risks of running containers as root?**
> If an attacker gets code execution inside the container, they inherit its user. Combined with any kernel bug, capability, or mount misconfiguration, root-in-container can escalate to root-on-host. Also, some kubernetes security policies (Pod Security Standards `restricted`) outright reject root containers. Fix: add a non-root user in the Dockerfile with `USER`, and drop capabilities with `--cap-drop=ALL --cap-add=<only what you need>`.

**Q: Why is mounting `/var/run/docker.sock` into a container dangerous?**
> The Docker socket controls the daemon. Anything with access to it can create privileged containers, mount the host filesystem, and effectively become root on the host. Never do it unless the container is trusted infrastructure (like a CI runner on a dedicated host), and even then, understand the blast radius.

**Q: How does BuildKit improve Docker builds?**
> Parallel stage execution, better and more granular cache (mount caches for package managers), build secrets that don't end up in layers, and multi-arch builds via `buildx`. It's default in modern Docker — you're already using it if you're on a recent version.

---

## Mental Checkpoints Before Phase 7

Ask yourself:
- Can I take a dev Dockerfile and turn it into a production one (non-root, minimal, healthchecked)?
- Do I know why `:latest` is dangerous and what to use instead?
- Can I keep secrets out of the image at every stage (build and runtime)?
- Do I know how to set memory limits and pick a restart policy?
- Can I explain how logs flow from the container to an aggregator?
- Do I know the basics of image scanning and why to run it in CI?

If yes → ready for **Phase 7 (Orchestration — where Docker fits in a bigger world with Swarm and Kubernetes)**.

---

# Phase 7 — Orchestration

**Goal:** Understand where Docker fits in the bigger picture. Docker gets your app into a container; **orchestrators** run those containers across many machines, keep them healthy, scale them, and update them without downtime.

You don't need to be a Kubernetes expert to know Docker — but understanding how they relate is essential for interviews and real-world work.

---

## 1. Why Orchestration Exists

Compose is great for one host. But production usually needs:

- **Many machines** — spread containers across a fleet for capacity + fault tolerance
- **Self-healing** — if a container crashes or a node dies, something restarts it, maybe on another host
- **Rolling updates** — replace v1 with v2 without downtime
- **Auto-scaling** — add/remove replicas based on load
- **Service discovery** — new replicas need to be findable automatically
- **Load balancing** — traffic distributed across all replicas
- **Secrets and config** — managed centrally, not by SSHing to each host

Docker CLI + Compose can't do this at scale. That's what orchestrators are for. The two main ones: **Docker Swarm** and **Kubernetes**.

---

## 2. What an Orchestrator Actually Does

Whatever the tool, the core loop is the same:

```
You declare:     "I want 5 replicas of image myapp:v2, using 512M RAM each"
                       │
                       ▼
Orchestrator:    Decides which nodes have capacity
                 Schedules containers there
                 Monitors them
                 If one dies → replaces it
                 If load spikes → scales up (if configured)
                 If you deploy v3 → rolls out gradually, rolls back on failure
```

It's a **declarative** system: you describe desired state, the orchestrator reconciles reality to match it.

---

## 3. Docker Swarm — Quick Intro

Swarm is Docker's built-in orchestrator. Lightweight, easy to learn, but has largely **lost the market to Kubernetes**. Still exists, still works — mostly seen in smaller shops or legacy setups.

### Concepts
- **Node** — a machine in the swarm (manager or worker)
- **Service** — a definition (image, replicas, config); Swarm's equivalent of "the desired state"
- **Task** — one running container that fulfills part of a service
- **Stack** — a group of services (basically a Compose file deployed to Swarm)

### Basic commands
```bash
docker swarm init                          # turn this node into a manager
docker swarm join --token <tok> <ip>       # join as a worker
docker service create --name web --replicas 3 -p 80:80 nginx
docker service ls
docker service scale web=5
docker stack deploy -c docker-compose.yml myapp   # deploy a compose file
```

### Why Swarm lost
- Kubernetes matched then surpassed Swarm's features
- Cloud providers (EKS/GKE/AKS) invested heavily in Kubernetes
- Community mindshare, tools, docs — all K8s

**Interview answer:** know Swarm exists and understand its concepts (it's still an easy on-ramp to orchestration ideas), but expect real jobs to use Kubernetes.

---

## 4. Kubernetes — The Landscape

Kubernetes (K8s) is the industry-standard orchestrator. Originally from Google (based on their internal system Borg), now maintained by the CNCF.

### High-level architecture
```
┌───────────────── Control Plane ─────────────────┐
│                                                 │
│  API Server ← you and every component talk here │
│  Scheduler  ← decides where pods go             │
│  Controller Manager ← reconciles desired state  │
│  etcd ← cluster's key-value store (source of    │
│         truth for all objects)                  │
└─────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────── Worker Nodes ─────────────────────┐
│                                                  │
│  kubelet ← runs on each node, manages pods       │
│  kube-proxy ← handles networking / iptables      │
│  container runtime (containerd) ← runs containers│
└──────────────────────────────────────────────────┘
```

You interact via `kubectl`, which talks to the API server. Every controller (Deployment, Service, etc.) is running a reconciliation loop against the API.

---

## 5. Kubernetes Core Objects (Minimum Vocabulary)

You need to recognize these terms even at Docker-focused interviews:

| Object | What it is |
|---|---|
| **Pod** | The smallest deployable unit. One or more tightly-coupled containers sharing network + storage. **This is what actually runs your container(s).** |
| **Deployment** | Declares "N replicas of this pod template". Handles rolling updates and rollbacks. Most apps run as a Deployment. |
| **ReplicaSet** | Owned by a Deployment. Ensures N pods are running. You rarely touch it directly. |
| **StatefulSet** | Like Deployment, but pods have stable identities + persistent storage. For databases, brokers, etc. |
| **DaemonSet** | Runs one pod per node. For log collectors, node agents. |
| **Job / CronJob** | Run-to-completion tasks (one-shot or scheduled). |
| **Service** | Stable virtual IP + DNS name that load-balances across a set of pods. Solves "pods come and go, IPs change". |
| **Ingress** | HTTP/S routing rules into the cluster (path/host → Service). |
| **ConfigMap** | Non-secret config injected into pods as env vars or files. |
| **Secret** | Sensitive config (passwords, tokens). Base64-encoded (not encrypted by default!). |
| **PersistentVolume / PersistentVolumeClaim** | Storage abstraction. PVC = "I want 10 GB"; PV = actual disk. |
| **Namespace** | Logical partition of the cluster (dev/prod, per team). |

Minimal example — deploy nginx:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels: { app: web }
  template:
    metadata:
      labels: { app: web }
    spec:
      containers:
        - name: nginx
          image: nginx:1.25
          ports:
            - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: web
spec:
  selector: { app: web }
  ports:
    - port: 80
      targetPort: 80
  type: ClusterIP
```
Apply: `kubectl apply -f deploy.yaml`. K8s creates 3 pods running nginx, plus a Service so other pods can reach them at `http://web`.

---

## 6. Pods Wrap Containers (The Key Mental Shift)

In Docker: the container is the unit.
In Kubernetes: the **Pod** is the unit.

A Pod is a wrapper around **one or more containers** that share:
- The same **network namespace** (same IP, they can `localhost` each other)
- The same **volumes**
- The same **lifecycle** (started together, killed together)

Most pods have just one container. Multi-container pods are for tightly-coupled helpers:
- **Sidecar** — a logging agent, service mesh proxy (Envoy), reloader
- **Init container** — runs before the main container starts (migrations, config generation)

Your Docker image doesn't change. Kubernetes just wraps it in a Pod and runs it.

---

## 7. How K8s Actually Runs a Container

Trace what happens when you `kubectl apply` a Deployment:

1. **kubectl** sends YAML to the **API server**
2. API server writes desired state to **etcd**
3. **Deployment controller** notices, creates a ReplicaSet
4. **ReplicaSet controller** creates 3 Pod objects (still just objects in etcd)
5. **Scheduler** picks nodes for each Pod based on resources, affinities, taints
6. On each chosen node, **kubelet** sees "I'm supposed to run pod X"
7. Kubelet asks the **container runtime** (containerd) to pull the image and start containers
8. Containerd uses **runc** to create namespaces + cgroups — same Linux primitives from Phase 1
9. **kube-proxy** updates iptables so the Service IP load-balances to the new pods

The bottom of this stack is **identical to Docker**. K8s just adds scheduling, healing, service discovery, and updates on top.

---

## 8. Container Runtimes and OCI

The container ecosystem is now standardized around **OCI (Open Container Initiative)**:

- **OCI Image Spec** — defines what an image looks like (layers, manifest, config)
- **OCI Runtime Spec** — defines how to run a container from an unpacked image

Docker-built images are OCI-compliant. Any OCI runtime can run them.

Common runtimes:
- **containerd** — extracted from Docker, now the standard runtime in K8s
- **CRI-O** — built specifically for Kubernetes
- **runc** — the low-level tool that both containerd and CRI-O call to actually create containers
- **gVisor** (Google) — user-space kernel for extra isolation
- **Kata Containers** — runs each container in a lightweight VM

The full stack:
```
Docker CLI    Kubernetes (kubelet)
    │              │
  dockerd     CRI (interface)
    │              │
containerd  ← ─ ─ ─┘
    │
   runc
    │
Linux kernel (namespaces, cgroups)
```

---

## 9. Why K8s Deprecated Docker (Dockershim)

You'll get asked this in interviews.

**Short version:** Kubernetes deprecated the **Docker daemon** as a runtime in v1.20 (announcement) and removed support in v1.24 (2022). **Docker-built images still work fine** in K8s — nothing changed for image builders.

**Long version:** K8s introduced the **CRI (Container Runtime Interface)** so it could talk to any runtime uniformly. Docker predates CRI and doesn't implement it — K8s used an adapter called **dockershim** to translate. Dockershim was extra code, extra latency, and extra maintenance burden. K8s deprecated it and now talks directly to CRI-compliant runtimes (containerd, CRI-O).

**What changed in practice:**
- If you run K8s: your cluster now uses containerd directly (usually invisible — your cloud provider swapped it for you).
- If you build images with `docker build`: **nothing changed**. Your images are OCI-compliant and run everywhere.
- If you were running Docker on your K8s nodes: you don't need it anymore (unless something else on the node needed Docker).

**The tl;dr line:** "K8s deprecated the Docker daemon as a runtime; it didn't deprecate Docker images. containerd (which Docker itself uses under the hood) is now the runtime."

---

## 10. Compose → Kubernetes Mapping

Rough translation of concepts (not exact, but useful mental map):

| Compose | Kubernetes |
|---|---|
| `service` | Deployment (or StatefulSet for stateful workloads) |
| Container replica count (`deploy.replicas`) | `spec.replicas` in Deployment |
| `ports:` | Service (`ClusterIP`) + Ingress for external access |
| `environment:` | env vars on the container spec, often sourced from ConfigMap/Secret |
| `env_file:` | ConfigMap (non-secret) / Secret (sensitive) |
| Named volume | PersistentVolumeClaim + PersistentVolume |
| Bind mount | hostPath volume (rare, discouraged in prod) |
| `networks:` | Namespaces + NetworkPolicies for isolation |
| `depends_on:` | Init containers, or app-level retry logic + readiness probes |
| `healthcheck:` | `livenessProbe` and `readinessProbe` on the container |
| `restart: unless-stopped` | Implicit — K8s always restarts failed pods (`restartPolicy: Always`) |
| `docker compose up -d` | `kubectl apply -f manifests/` |

Tools like **Kompose** can convert `docker-compose.yml` → K8s manifests as a starting point, though production manifests usually need more.

---

## 11. When to Use What

| Scenario | Use |
|---|---|
| Local dev with a few services | Docker + Compose |
| CI integration tests | Docker + Compose |
| Single-host production deployment | Compose (with restart policies, healthchecks, log rotation) |
| Small self-hosted cluster (3–10 nodes, one team) | Docker Swarm or lightweight K8s (k3s) |
| Multi-team, multi-app production at scale | Kubernetes (managed: EKS, GKE, AKS) |
| Serverless containers | AWS Fargate, Google Cloud Run, Azure Container Instances |
| Edge / IoT | k3s, Podman |

**Golden rule:** don't reach for Kubernetes if Compose fits. K8s adds significant operational complexity — clusters, upgrades, RBAC, networking policies, storage classes. Use it when you need what it provides, not because it's fashionable.

---

## 12. Practice Exercises

You don't need a real cluster — install `kind` or `minikube` locally.

1. **Install kind / minikube:** `brew install kind` (Mac) or `brew install minikube`. Create a cluster: `kind create cluster`.
2. **Deploy nginx:** Write the Deployment + Service YAML from section 5. Apply it. `kubectl get pods`, `kubectl get svc`. Port-forward: `kubectl port-forward svc/web 8080:80`. Curl it.
3. **Scale it:** `kubectl scale deploy/web --replicas=5`. Watch pods appear. Then scale to 1.
4. **Break a pod, watch it heal:** `kubectl delete pod <one-of-the-pods>`. See a replacement appear within seconds — that's the ReplicaSet controller doing its job.
5. **Rolling update:** Change the image to `nginx:1.26`. `kubectl apply -f`. Watch `kubectl rollout status`. Then `kubectl rollout undo` to roll back.
6. **Compose to K8s:** Take a small `docker-compose.yml` from Phase 5. Try `kompose convert` — read the generated YAML.
7. **Inspect the runtime:** On the kind node (`docker exec -it kind-control-plane sh`), run `crictl ps` — that's containerd's CLI, showing the actual containers K8s is running via CRI.

---

## 13. Interview Answers (Phase 7)

**Q: Why is Kubernetes needed if I already have Docker?**
> Docker builds and runs containers on **one host**. Kubernetes runs containers across a **fleet of hosts**, and adds scheduling, self-healing (restarts failed pods, reschedules them on other nodes), rolling updates + rollbacks, auto-scaling, service discovery, load balancing, and centralized config/secrets. Docker Compose handles small single-host setups; K8s handles production at scale.

**Q: Did Kubernetes drop Docker? What does that mean for my images?**
> K8s deprecated the **Docker daemon** as its container runtime (removed in v1.24). It did **not** deprecate Docker images. Docker builds OCI-compliant images, and K8s now talks directly to CRI-compliant runtimes like containerd — which happens to be what Docker uses under the hood anyway. Nothing changed for people building images.

**Q: What's the difference between a container and a pod?**
> A container is a running process with its own namespaces and cgroups. A **Pod** is Kubernetes' scheduling unit — a wrapper around one or more containers that share network (same IP), storage, and lifecycle. Most pods have one container; multi-container pods are for tightly-coupled sidecars (logging agents, proxies) or init containers (migrations before the app starts).

**Q: What is OCI and why does it matter?**
> The **Open Container Initiative** standardized container image and runtime formats. Any OCI-compliant image runs on any OCI-compliant runtime. That's why images you build with `docker build` run in Kubernetes with containerd, and why the ecosystem could swap out the Docker daemon without breaking anyone's images.

**Q: How would you approach migrating a Docker Compose app to Kubernetes?**
> Start by mapping services → Deployments, published ports → Services (+ Ingress for external), env vars → ConfigMaps/Secrets, named volumes → PersistentVolumeClaims, healthchecks → readiness/liveness probes. Kompose can generate a first draft. Then add production concerns: resource requests/limits, pod disruption budgets, HPA for autoscaling, network policies for isolation, and proper secret management (external secrets operator or vault integration).

**Q: When would you use Docker Swarm over Kubernetes?**
> Swarm is much simpler to set up and operate — a few commands to have a cluster. It's a reasonable choice for small teams with modest scale who want orchestration without K8s's complexity. But mindshare, tooling, cloud provider support, and job market are all K8s now, so unless you have a specific reason to pick Swarm, most teams choose K8s (or managed K8s like EKS/GKE/AKS).

**Q: What's containerd?**
> A container runtime originally extracted from Docker. It handles image transfer, container lifecycle, and calls `runc` to actually create containers via kernel primitives. It's the runtime Docker itself uses internally, and it's the runtime Kubernetes talks to directly now (via CRI). If you're on modern K8s, containerd is what's running your pods.

---

## Mental Checkpoints Before Phase 8

Ask yourself:
- Can I explain what an orchestrator does and why Compose isn't enough for large-scale prod?
- Do I know what a Pod is and how it wraps containers?
- Can I explain the K8s-deprecated-Docker story without confusion?
- Do I know what OCI is and why standardization matters?
- Can I map Compose concepts onto their K8s equivalents?
- Can I make a call between Compose, Swarm, and Kubernetes for a given scenario?

If yes → ready for **Phase 8 (Deep Internals — for senior interviews and true understanding)**.

---

# Phase 8 — Deep Internals

**Goal:** Move from "I use Docker" to "I understand what Docker actually does under the hood." This is what separates senior engineers from users. Every concept here has been mentioned earlier — Phase 8 goes deeper.

---

## 1. Why This Phase Matters

At senior interviews (staff, principal, platform, SRE), you'll be asked "how does it actually work?" — not "how do you use it?" Knowing internals also makes you:

- Better at **debugging** ("why does my image weigh 2 GB when the code is 5 MB?")
- Better at **security reasoning** ("what does a container escape actually mean?")
- Better at **performance work** ("why is my BuildKit cache missing?")
- Confident when reading source code, blog posts, or CVE advisories

---

## 2. Image Layers and Content-Addressable Storage

Every layer in a Docker image is stored on disk as a directory of files, addressed by the **SHA256 hash** of its contents. This is "content-addressable storage" (CAS) — same content, same hash, deduplicated automatically.

### Where images live (Linux)
```
/var/lib/docker/
├── image/overlay2/
│   ├── imagedb/    ← image configs (JSON)
│   ├── layerdb/    ← metadata about layers
│   └── repositories.json  ← name:tag → digest mapping
└── overlay2/
    └── <layer-sha>/
        ├── diff/    ← the actual files in this layer
        ├── link     ← short alias for the layer
        ├── lower    ← chain of parent layers
        └── work/    ← OverlayFS scratch
```

### Why CAS matters
- **Deduplication:** two images sharing a base layer store it on disk **once**
- **Verifiability:** you can prove an image hasn't been tampered with by checking the digest
- **Reproducibility:** `myimage@sha256:abc...` refers to byte-identical content, forever

### Layer sharing in action
```bash
docker pull node:20-alpine       # ~50 MB
docker pull node:20-alpine-slim  # only pulls the layers not shared with above
docker system df -v              # shows what's shared vs unique
```

---

## 3. Image Manifest and Config

An image on disk is not a single blob. It's:

1. **Manifest** — JSON that lists the layers (by digest) and points to the config
2. **Config** — JSON with metadata (CMD, ENV, WORKDIR, exposed ports, architecture, history)
3. **Layers** — the actual tar.gz blobs of filesystem diffs

Inspect a manifest from a registry:
```bash
docker manifest inspect nginx:1.25
```

Inspect the config Docker knows locally:
```bash
docker inspect nginx:1.25
```

### Multi-arch images
The `nginx:1.25` reference often points to a **manifest list** (a.k.a. fat manifest) — one entry per architecture (amd64, arm64). When you pull, Docker picks the one matching your host. This is why the same `docker pull nginx` works identically on your M-series Mac and on an x86 Linux server.

---

## 4. OverlayFS Deep Dive

Docker's default storage driver on Linux. OverlayFS lets you stack read-only "lower" layers with a writable "upper" layer, presenting them as a single merged filesystem.

### The four directories
```
lowerdir  ← read-only image layers (stacked)
upperdir  ← container's writable layer
workdir   ← internal scratch (OverlayFS bookkeeping)
merged    ← what the container actually sees
```

### Read/write semantics
- **Read** from a file → OverlayFS walks the layer stack top-down; returns the first hit
- **Write** to an existing file → OverlayFS **copies the file up** from a lower layer to upperdir, then modifies (this is **copy-on-write**)
- **Delete** a file that exists in a lower layer → OverlayFS creates a **whiteout** (a special marker) in upperdir; the merged view hides the file
- **Delete** a whole directory → OverlayFS uses an **opaque directory** marker

### Why this matters
- Multiple containers from the same image **share** all lower layers on disk
- Only their unique writes take space (writable upper layer)
- Deleting a file in a container doesn't shrink the image — the file still exists in the lower layer, just hidden. This is why `RUN rm cache-stuff` in a **later** layer doesn't reduce image size — you have to `rm` in the **same layer** where the file was created.

### See it live
```bash
docker run -d --name test alpine sleep 3600
docker inspect test | grep -A5 GraphDriver
# Shows LowerDir, UpperDir, WorkDir, MergedDir paths
```

---

## 5. Namespaces Deep Dive

From Phase 1: namespaces isolate what a process can see. Time to go deeper.

### List a container's namespaces
```bash
docker run -d --name test nginx
PID=$(docker inspect -f '{{.State.Pid}}' test)
ls -l /proc/$PID/ns/
# Symlinks like: net -> 'net:[4026532...]'  ← the namespace ID
```

Every container process is inside a set of namespaces. Two processes in the same namespace see the same view of that resource.

### The 8 namespace types (Linux)
| Namespace | Isolates | Docker uses |
|---|---|---|
| **mnt** | Filesystem mounts | ✅ |
| **pid** | Process IDs | ✅ |
| **net** | Network stack | ✅ |
| **ipc** | System V IPC + POSIX message queues | ✅ |
| **uts** | Hostname + domainname | ✅ |
| **user** | User + group IDs (UID/GID mapping) | Opt-in / rootless |
| **cgroup** | cgroup hierarchy view | ✅ (newer kernels) |
| **time** | System time (rare) | ❌ |

### The PID 1 problem
Inside a container, your app runs as **PID 1**. Linux gives PID 1 special responsibilities:
- Reap zombie children
- Handle signals correctly (SIGTERM, SIGINT)

If your app doesn't do these things (most don't), you get:
- **Zombie processes** piling up
- Signals ignored → `docker stop` hangs and kills after 10s

**Fixes:**
- Use `docker run --init` (adds a minimal init process, `tini`, as PID 1)
- Or add `tini` to your image: `ENTRYPOINT ["/tini", "--"]`
- Or write your app to handle signals properly

---

## 6. cgroups Deep Dive

cgroups (control groups) limit and account for resource use per process group. Docker puts each container in its own set of cgroups.

### cgroups v1 vs v2
- **v1:** separate hierarchies per controller (cpu, memory, blkio, etc.). Legacy, complex.
- **v2:** unified hierarchy (one tree, all controllers). Modern default on new distros. Cleaner API.

Check your kernel: `cat /sys/fs/cgroup/cgroup.controllers` (empty means v1).

### See a container's cgroup
```bash
docker run -d --name test --memory=256m nginx
cat /sys/fs/cgroup/memory/docker/<container-id>/memory.limit_in_bytes  # v1
# or v2:
cat /sys/fs/cgroup/system.slice/docker-<id>.scope/memory.max
```

### What Docker limits map to
| Docker flag | cgroup control |
|---|---|
| `--memory=512m` | `memory.max` (v2) — hard limit; hit it → OOM kill |
| `--memory-swap=1g` | `memory.swap.max` — swap allowed |
| `--memory-reservation=256m` | soft target under memory pressure |
| `--cpus=1.5` | `cpu.max` — quota per period (150% of a core) |
| `--cpu-shares=512` | `cpu.weight` — relative weight vs other containers |
| `--pids-limit=200` | `pids.max` — cap process count (fork-bomb protection) |
| `--blkio-weight=500` | I/O priority |

### OOM behavior
When a container hits its memory limit, the kernel's OOM killer picks a process (usually your app) and sends SIGKILL. Check with:
```bash
docker inspect test | grep OOMKilled
```

---

## 7. The Container Lifecycle at Syscall Level

What actually happens when you `docker run nginx`? Walk the full stack.

```
1. docker CLI     → POST /containers/create + /start via Unix socket
2. dockerd        → validate, call containerd via gRPC
3. containerd     → pull image (if missing), unpack layers, prepare OverlayFS mount
4. containerd     → invoke runc with an OCI runtime bundle (config.json + rootfs)
5. runc           → clone(2) with CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWNET|... 
                  → the new process is in fresh namespaces
                  → set up cgroups via /sys/fs/cgroup/... writes
                  → pivot_root(2) into the container's rootfs (like chroot on steroids)
                  → set capabilities, seccomp filter, apparmor/selinux label
                  → execve(2) the container's entrypoint (nginx)
6. nginx (PID 1)  → running inside the fully isolated environment
```

Under the hood it's just **`clone` + `unshare` + `mount` + `pivot_root` + `execve`** — Linux syscalls that have existed for years. Docker is orchestration around these.

You can approximate a "container" by hand with `unshare`:
```bash
sudo unshare --pid --net --mount --uts --ipc --fork bash
# You're now in fresh namespaces — try `ps aux` (only sees your shell)
```

---

## 8. BuildKit Internals

The old Docker builder was a linear sequence of steps. **BuildKit** replaced it (default in modern Docker) with a **DAG (directed acyclic graph)** of operations.

### What changes
- **Parallel stage execution** — independent stages of a multi-stage Dockerfile build concurrently
- **Fine-grained caching** — each op's inputs are hashed; unchanged ops are cache hits
- **Cache mounts** — persistent caches across builds (huge for package managers)
- **Build secrets** — mounted into a single RUN, never persisted
- **SSH forwarding** — for private repo access without leaking keys
- **Frontend/backend split** — different Dockerfile "frontends" possible

### Cache mount example
```dockerfile
# syntax=docker/dockerfile:1
FROM node:20
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci
```
The npm cache persists across builds. Without this, every `npm ci` re-downloads everything into a new layer.

### Cache import/export (for CI)
```bash
docker buildx build \
  --cache-to type=registry,ref=myrepo/myapp:buildcache,mode=max \
  --cache-from type=registry,ref=myrepo/myapp:buildcache \
  -t myapp:v1 .
```
CI shares cache via the registry — much faster than cold builds.

### Multi-arch builds
```bash
docker buildx create --use
docker buildx build --platform linux/amd64,linux/arm64 -t myrepo/myapp:v1 --push .
```
BuildKit uses QEMU emulation (or native builders per arch via a remote buildx node) to produce one manifest list with images for both architectures.

---

## 9. Rootless Docker

Traditionally the Docker daemon runs as root, and containers by default run as root. **Rootless mode** runs everything as a normal user via **user namespaces**.

### How it works
Linux user namespaces let a process appear as UID 0 inside the namespace while being an unprivileged UID (e.g., 1000) on the host. The kernel maps UIDs via `/etc/subuid` and `/etc/subgid`.

Install rootless:
```bash
curl -fsSL https://get.docker.com/rootless | sh
export PATH=$HOME/bin:$PATH
export DOCKER_HOST=unix:///run/user/$UID/docker.sock
docker run hello-world
```

### Tradeoffs
- ✅ **Security:** daemon compromise doesn't = host root
- ✅ **Multi-tenant** hosts (shared dev servers, CI)
- ❌ Some features restricted (no privileged containers, some network modes)
- ❌ Slower networking (uses slirp4netns userspace networking)
- ❌ Ports < 1024 need extra config

Podman (Red Hat) is rootless by default and daemonless — worth knowing as an alternative.

---

## 10. Docker Desktop on Mac and Windows (The Hidden VM)

Containers require a Linux kernel. Macs and Windows don't have one. So how does Docker Desktop work?

**It runs a hidden Linux VM.** Every container you run on your Mac is actually running inside that VM's Linux kernel.

- **Mac (M-series):** uses macOS's Virtualization.framework to run a small Linux VM (LinuxKit)
- **Mac (Intel):** used HyperKit historically
- **Windows:** uses WSL2 (which is itself a Linux VM under Hyper-V), or Hyper-V directly

### Implications
- Container filesystem is inside the VM, not on your Mac's disk directly → bind mounts (`-v $(pwd):/app`) require file syncing across the VM boundary → can be **slow** for big trees. On Mac, look up VirtioFS / gRPC FUSE settings.
- Container network is inside the VM → `-p 8080:80` publishes on the VM, and Docker Desktop forwards to your Mac's `localhost:8080`.
- `docker system df` disk shown is inside the VM.

### Native Linux is different
On native Linux, Docker runs directly on the host kernel — no VM, no file-sync overhead. This is why Linux devs sometimes wonder why Mac users complain about performance.

---

## 11. Networking Under the Hood

More detail than Phase 4.

### The bridge network
When Docker installs, it creates:
- **`docker0`** — a virtual Ethernet switch (Linux bridge) on the host
- An iptables rule set for NAT and forwarding

For each container:
- Docker creates a **veth pair** (two virtual Ethernet interfaces)
- One end (`vethXXX`) goes on the host, attached to `docker0`
- The other end (`eth0`) goes inside the container's network namespace
- Container gets an IP (e.g., `172.17.0.2`) via Docker's embedded DHCP-like assignment

Traffic flow, `-p 8080:80`:
```
Client → host eth0:8080 
       → iptables DNAT rule (host:8080 → 172.17.0.2:80)
       → docker0 bridge
       → vethXXX
       → container eth0
       → nginx
```

Return traffic is SNAT'd back.

### Embedded DNS
On user-defined bridge networks, Docker runs a mini DNS server (bound at `127.0.0.11` inside each container). Container names resolve to their IPs via this. That's why `curl http://db:5432` works.

### iptables inspection (on the host)
```bash
sudo iptables -t nat -L DOCKER
sudo iptables -L DOCKER-USER
```
Every `-p` mapping shows up as a DNAT rule. Every user-defined bridge adds forwarding rules.

---

## 12. Common Debugging Techniques

Advanced but everyday tools:

### Enter a container's namespaces from the host
```bash
PID=$(docker inspect -f '{{.State.Pid}}' <container>)
sudo nsenter -t $PID -n -p ss -tulpn     # net namespace: see listening ports
sudo nsenter -t $PID -m ls /             # mount namespace: see container's rootfs from host
```

### Inspect image layers
```bash
docker save myapp:v1 -o myapp.tar
mkdir myapp && tar -xf myapp.tar -C myapp
ls myapp                  # manifest.json + one dir per layer
```

### Bytes-level image explorer
```bash
brew install dive
dive myapp:v1
# Interactive TUI: browse each layer's file changes, spot bloat
```

### Trace syscalls inside a container
```bash
docker run --cap-add=SYS_PTRACE --pid=container:<name> nicolaka/netshoot \
  strace -f -p 1
```

### Network debugging
```bash
# ephemeral toolbox on any container's network
docker run --rm -it --network container:<name> nicolaka/netshoot
# then: curl, dig, nslookup, tcpdump, ip a, ss -tulpn ...
```

### When a container won't start
```bash
docker logs <name>                              # first stop
docker inspect <name> | jq .State               # exit code, OOMKilled, error
docker run -it --entrypoint sh <image>          # poke around without starting the app
```

---

## 13. Practice Exercises

1. **Inspect a layer:** `docker pull nginx`. `docker inspect nginx` → find the layer digests. Look in `/var/lib/docker/overlay2/` (Linux) — find those layers on disk.
2. **See CoW in action:** Run `docker run -d --name a alpine sleep 3600`. `docker exec a touch /bigfile && dd if=/dev/zero of=/bigfile bs=1M count=100`. Compare `docker system df` before/after — the container's writable layer grew.
3. **Namespace tour:** Start a container. `ls -l /proc/$(docker inspect -f '{{.State.Pid}}' <name>)/ns/`. Note the namespace IDs. Start a second container — different IDs.
4. **PID 1 signal test:** Run a shell-form CMD (`CMD node server.js`) vs exec-form (`CMD ["node","server.js"]`). Time `docker stop` on each. The shell-form one takes 10s (signal not forwarded). Fix with `--init` or exec form.
5. **BuildKit cache mount:** Add a `--mount=type=cache,target=/root/.npm` to a Node Dockerfile. Time cold vs warm builds.
6. **Explore layers with dive:** `dive myapp:v1`. Find the layer that added the most bytes. Refactor the Dockerfile to shrink it.
7. **Manual container:** `sudo unshare --pid --net --mount --uts --fork bash`. Confirm isolation with `ps aux`, `hostname newname`, `ip a`. You just made a mini-container by hand.
8. **iptables tour:** After running `docker run -p 8080:80 nginx`, `sudo iptables -t nat -L DOCKER` — find the DNAT rule.

---

## 14. Senior Interview Answers (Phase 8)

**Q: What happens when I run `docker run nginx`?**
> The CLI POSTs to dockerd, which asks containerd to prepare the image (pulling if needed, unpacking layers into an OverlayFS mount). containerd invokes runc with an OCI bundle. runc uses `clone` with namespace flags to create a new process in fresh namespaces, sets up cgroups via `/sys/fs/cgroup` writes, `pivot_root`s into the container rootfs, applies capabilities/seccomp/AppArmor, and `execve`s nginx. That process is now PID 1 inside the container. kube-proxy or Docker's iptables rules handle the network side.

**Q: Why does `RUN rm large-file` in a later layer not shrink the image?**
> OverlayFS layers are immutable once created. Deleting a file in a later layer creates a **whiteout marker** that hides the file in the merged view, but the file still exists in the earlier layer — it's just invisible. To actually shrink the image, delete the file in the same `RUN` where it was created, so it never becomes part of any committed layer.

**Q: What's copy-on-write in the context of Docker?**
> All read-only image layers are shared across containers. When a container writes to a file, OverlayFS copies the file up from the lower (read-only) layer to the container's writable upper layer, then modifies the copy. This means many containers from the same image share layers on disk — only their unique writes take space.

**Q: How is a Docker container different from a chroot?**
> chroot changes the apparent root directory of a process — filesystem isolation only. A container adds **process (PID)**, **network**, **mount**, **hostname**, **IPC**, and often **user** namespaces on top, plus **cgroups** for resource limits, plus security features like capabilities, seccomp, and AppArmor/SELinux. A chroot is one small piece of what a container is.

**Q: What's the PID 1 problem?**
> Inside a container, your app is PID 1. Linux gives PID 1 special responsibilities — reaping zombies and forwarding signals to children. Most apps don't do these correctly. Result: zombies pile up, and `docker stop` doesn't cleanly shut the app down because SIGTERM is ignored, so Docker waits 10 s and SIGKILLs. Fix: use `docker run --init` (adds `tini` as PID 1) or bundle `tini` in the image.

**Q: What is BuildKit and why is it better than the classic builder?**
> BuildKit compiles a Dockerfile into a DAG of operations rather than a linear sequence, letting independent multi-stage builds run in parallel. It has fine-grained per-op caching, cache mounts that persist between builds (great for package managers), build secrets that never end up in layers, SSH forwarding, and multi-arch builds via `buildx`. It's the default in modern Docker.

**Q: How does Docker Desktop run containers on macOS?**
> There's no Linux kernel on macOS, so Docker Desktop runs a small hidden Linux VM (via Apple's Virtualization.framework on M-series Macs). Every container actually runs inside that VM. That's why bind mounts across the VM boundary can be slow, and why `-p 8080:80` publishes to the VM which Docker Desktop then forwards to your Mac's localhost.

**Q: What's rootless Docker and why use it?**
> Rootless runs the Docker daemon and containers as a normal user via user namespaces — UID 0 inside the container maps to your unprivileged UID on the host. Big security win: a container escape doesn't grant host root. Tradeoffs: some features are unavailable (privileged containers, some network modes), and networking is slower because it uses userspace slirp4netns. Podman is a related tool that's rootless-by-default.

**Q: Explain content-addressable storage in Docker.**
> Every layer and every image config is stored by the SHA256 hash of its content. Same content = same hash = stored once. This gives you automatic deduplication across images that share layers, verifiability (an image can't be silently tampered with — its digest would change), and reproducibility (pinning `myimage@sha256:abc...` guarantees byte-identical content forever).

**Q: How does DNS work between containers?**
> On a user-defined bridge network, Docker runs an embedded DNS server bound at `127.0.0.11` inside each container. When one container looks up another by name, that request goes to `127.0.0.11`, which returns the target container's IP on the shared network. This is why `curl http://db:5432` works between containers on the same user-defined network but not on the default bridge (which lacks embedded DNS).

---

## Final Wrap-Up

You've now gone from "what is a container?" to "here's what runc does when you `docker run`". You can:

- Explain Docker to a junior developer or in a whiteboard interview
- Write clean, small, secure production Dockerfiles
- Design and run multi-container apps with Compose
- Reason about performance, security, and operational tradeoffs
- Understand where Docker fits in the K8s / OCI ecosystem
- Debug problems at the layer, namespace, and syscall level

### What to do next
- **Practice regularly** — rebuild the exercises without notes
- **Read one CVE advisory** for Docker or containerd — see internals mentioned in the wild
- **Learn Kubernetes basics** properly (Phase 7 is a starting point, not the finish line)
- **Explore alternatives** — Podman (rootless, daemonless), containerd directly (`nerdctl`, `ctr`), gVisor, Kata Containers
- **Watch a real production incident postmortem** involving containers — the deep-internals knowledge pays off here

### The absolute-minimum interview cheat sheet
1. Container vs VM — shared kernel, namespaces + cgroups + OverlayFS
2. CMD vs ENTRYPOINT — one is default args, other is the executable
3. COPY order — dependencies before source for caching
4. Multi-stage builds — separate build from runtime
5. Volumes vs bind mounts — Docker-managed vs host path
6. `-p` vs EXPOSE — publish vs document
7. depends_on + healthchecks — order vs readiness
8. Non-root user, image scanning, secrets injection at runtime
9. Immutable tags (SHA/semver), never `:latest` in prod
10. K8s deprecated the Docker daemon, not Docker images
11. OverlayFS copy-on-write, PID 1 signal handling, cgroups memory limits
12. What actually happens on `docker run` — clone + unshare + pivot_root + execve

Master these, and you're deeply prepared for any Docker interview from junior through staff level.

---

# Appendix — Docker for Java Developers

Everything in Phases 1–8 applies to Java. This appendix covers what changes for Java: **how to build the image**, **JVM sizing inside containers**, and **which tools to prefer**.

---

## A1. The Java Container Landscape (What's Different)

- **Artifact:** a JAR (Spring Boot fat JAR) or WAR — a single deployable unit. Perfect for containers.
- **Runtime:** you need a JRE/JDK inside the container. Choice of JDK vendor and version matters (size, security, GC behavior).
- **Startup cost:** JVM startup is slow relative to Go/Node. Affects scaling and cold starts.
- **Memory model:** JVM has its own heap (`-Xmx`) plus off-heap (metaspace, direct buffers, threads). Container memory limits must account for both.
- **Build tools:** Maven or Gradle. Their caches (`~/.m2`, `~/.gradle/caches`) are huge — need to leverage Docker layer caching or BuildKit cache mounts.

---

## A2. Base Image Choices for Java

| Image | Size | Notes |
|---|---|---|
| `eclipse-temurin:21-jre` | ~230 MB | **Recommended default.** Eclipse Adoptium (formerly AdoptOpenJDK). Well-maintained, free, no Oracle license issues. |
| `eclipse-temurin:21-jre-alpine` | ~180 MB | Alpine variant. Uses `musl` — most Java works fine but native libs may need `-jdk-alpine` or extra deps. |
| `amazoncorretto:21` | ~450 MB (JDK) | AWS's OpenJDK build. LTS support from Amazon. |
| `bellsoft/liberica-openjdk-alpine:21` | ~180 MB | Liberica by BellSoft. Also does Alpine + musl builds. |
| `gcr.io/distroless/java21-debian12` | ~200 MB | No shell, no package manager. Most secure, hardest to debug. Great for prod. |
| `openjdk:21` | ~470 MB | The old OpenJDK image; **now deprecated**. Don't use for new work. |
| `sapmachine:21` | ~450 MB | SAP's OpenJDK. |

**Rules:**
- **JRE, not JDK, at runtime.** JDK is for building; JRE is for running (~200 MB smaller). Distroless doesn't ship a shell — it only has what the JVM needs.
- **Temurin** is the safe default. **Distroless** if you want maximum security in prod.
- Match major version to your app's target (`21` for LTS today).

---

## A3. The Wrong Way — What Most Beginners Write

```dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN mvn clean package
CMD ["java", "-jar", "target/myapp.jar"]
```

**Every problem you can imagine:**
- Uses JDK for runtime (fat)
- Copies everything including `.git`, `target/`, IDE files (bloats context + image)
- No dep caching → `mvn package` re-downloads all deps on every code change
- Runs as root
- Runs `mvn` at build time inside Docker → slow, no reuse of local Maven cache
- No JVM tuning for containers

Let's fix each of these.

---

## A4. Multi-Stage Build (The Right Way — Maven)

```dockerfile
# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Copy Maven wrapper + pom first — deps cached separately from source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Now copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app

COPY --from=builder --chown=app:app /build/target/*.jar app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Why this works:**
- `pom.xml` copied first → `dependency:go-offline` downloads deps into a cached layer. If only source changes, deps aren't re-fetched.
- Uses **Maven wrapper** (`./mvnw`) — no need to install Maven in the image.
- **Multi-stage:** JDK + Maven cache stay in build stage; only the JAR ships to runtime.
- **JRE-alpine** runtime (~180 MB).
- **Non-root user.**

---

## A5. Multi-Stage Build (Gradle)

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder --chown=app:app /build/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`--no-daemon` avoids leaving Gradle daemons running in the build stage. `bootJar` is Spring Boot's task; for plain Gradle use `jar` or `shadowJar`.

---

## A6. BuildKit Cache Mount — Even Faster

If you rebuild often, cache the entire Maven/Gradle local repo across builds:

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B
```

The `~/.m2` cache persists between builds — first build downloads deps, subsequent ones don't. This does NOT end up in the final image (mount is build-time only).

For Gradle: `--mount=type=cache,target=/root/.gradle`.

---

## A7. Spring Boot Layered JARs (Best Practice for Spring)

Spring Boot 2.3+ can split its fat JAR into **layers** — dependencies, spring-boot-loader, snapshot-deps, and application classes — as separate directories. Since deps rarely change but app code changes constantly, this gives you much better Docker layer caching.

**Enable in `pom.xml`:**
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layers>
            <enabled>true</enabled>
        </layers>
    </configuration>
</plugin>
```

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B
# Explode the layered JAR
RUN java -Djarmode=layertools -jar target/*.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app

# Copy each layer as a separate Docker layer
COPY --from=builder --chown=app:app /build/dependencies/ ./
COPY --from=builder --chown=app:app /build/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Now changing one Java class only invalidates the `application` layer (~a few MB), not the ~150 MB dependencies layer. Rebuilds and image pushes get dramatically faster.

---

## A8. JVM in a Container — The Critical Gotchas

The single biggest source of Java-in-Docker pain. Read carefully.

### The old memory problem (fixed in JDK 10+)
Old JVMs didn't respect cgroup memory limits — they'd see the **host's** total RAM and try to allocate accordingly, then get OOM-killed by the container. **JDK 10+ (and backports to 8u191) fixed this** — modern JVMs are "container-aware".

Modern Java (11+, 17, 21) automatically reads cgroup limits. But you should still tune explicitly.

### Right way to set heap
Instead of hardcoded `-Xmx512m`, use `-XX:MaxRAMPercentage`:
```bash
java -XX:MaxRAMPercentage=75.0 -jar app.jar
```
This tells the JVM "take 75% of whatever memory the container has". Now the same image works whether the container has 512 MB or 4 GB — no rebuild.

**In Docker/Compose:**
```yaml
services:
  web:
    image: myapp
    deploy:
      resources:
        limits:
          memory: 1G
    environment:
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"
```

`JAVA_TOOL_OPTIONS` is a special env var the JVM automatically picks up — no code or Dockerfile changes needed.

### Why not 100%?
The JVM heap is not the only memory user. You also need:
- **Metaspace** (~100–200 MB for a real Spring Boot app)
- **Thread stacks** (~1 MB per thread; a Spring app easily has 50–200 threads)
- **Direct byte buffers** (Netty, database drivers)
- **JIT compiler code cache**
- **GC overhead**

Rule of thumb: heap = 50–75% of container memory for smaller apps; up to 80% for larger heaps where the fixed costs are a smaller %.

### CPU-aware settings
Modern JVMs also read cgroup CPU limits and size their thread pools (GC threads, ForkJoinPool) accordingly. Old JVMs didn't. On JDK 11+ this is automatic. Set `--cpus=2` and the JVM sees 2 processors.

### GC choice
- **G1GC** — default since JDK 9. Good all-round choice.
- **ZGC** (`-XX:+UseZGC`) — ultra-low pause GC, JDK 15+. Great for latency-sensitive apps with heaps > 4 GB.
- **Parallel GC** — throughput-focused, fine for batch jobs.
- **Serial GC** (`-XX:+UseSerialGC`) — best for tiny (< 256 MB) containers; less overhead than G1.

---

## A9. Complete Production Spring Boot Dockerfile

```dockerfile
# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder --chown=app:app /build/dependencies/ ./
COPY --from=builder --chown=app:app /build/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/application/ ./

USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Key production choices:
- Layered JAR for cache efficiency
- Non-root user
- `MaxRAMPercentage` — container-size-agnostic
- `ExitOnOutOfMemoryError` — kill fast on OOM, let orchestrator restart (fail-fast beats trying to limp along)
- Healthcheck against Spring Boot Actuator (`/actuator/health` — enable `spring-boot-starter-actuator`)
- Long `start-period` for slow JVM startup

---

## A10. Alternative Build Tools

You don't always need to write a Dockerfile for Java. Several tools generate optimized images from your build:

### Spring Boot Buildpacks (built-in)
```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=myapp:v1
```
Uses **Cloud Native Buildpacks (Paketo)** under the hood. Produces layered, non-root, security-hardened images with **no Dockerfile needed**. Great default.

### Jib (Google)
```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>3.4.4</version>
    <configuration>
        <to>
            <image>gcr.io/myproject/myapp</image>
        </to>
    </configuration>
</plugin>
```
```bash
./mvnw compile jib:build         # pushes directly to registry
./mvnw compile jib:dockerBuild   # builds locally
```
**No Docker daemon required.** No Dockerfile. Distroless base by default. Layers your app correctly. Fast rebuilds.

### When to use what
- **Learning + full control** → write your own Dockerfile
- **Fast production path** → Buildpacks (Spring native) or Jib
- **Tight CI without Docker daemon** → Jib

Interviewers rarely ask about these, but knowing they exist marks you as senior.

---

## A11. Docker Compose for a Java Microservice

Spring Boot app + Postgres + Redis:

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/myapp
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: cache
      SPRING_PROFILES_ACTIVE: prod
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75.0"
    depends_on:
      db:
        condition: service_healthy
      cache:
        condition: service_started
    deploy:
      resources:
        limits:
          memory: 1G
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 40s
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: myapp
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:7-alpine

volumes:
  pgdata:
```

Notes:
- Spring's env-var naming maps directly to properties: `SPRING_DATASOURCE_URL` → `spring.datasource.url`. No config-file changes needed.
- Long `start_period` — JVMs are slow to start.
- Actuator's `/health` endpoint gives you a real healthcheck for free.

---

## A12. Dev Workflow — Live Reload with Docker

Rebuilding the image for every code change is painful. Two solutions:

### Option A — Spring Boot DevTools + bind mount
Bind-mount the build output. DevTools watches for changes and restarts the app inside the container.

```yaml
services:
  app:
    image: eclipse-temurin:21-jdk
    working_dir: /app
    volumes:
      - .:/app
      - m2:/root/.m2
    ports:
      - "8080:8080"
    command: ./mvnw spring-boot:run
volumes:
  m2:
```

### Option B — Spring Boot Docker Compose module (Spring 3.1+)
Spring Boot can auto-start Compose services when your app starts locally:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <scope>runtime</scope>
</dependency>
```
Now `./mvnw spring-boot:run` locally auto-boots `db` and `cache` from your `compose.yaml` — no more "start Docker, then start app" dance.

### Option C — Testcontainers (integration testing)
Not really dev-run, but critical to know: **Testcontainers** spins up real Docker containers (Postgres, Kafka, Redis) inside JUnit tests. Beats mocks for integration tests.
```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
```
Every Java shop with mature testing uses this. Master it.

---

## A13. Native Images with GraalVM (Advanced)

Compile your Spring Boot app to a **native binary** with GraalVM. Result: ~50 ms startup instead of ~5 s, and ~50 MB image instead of ~250 MB.

```bash
./mvnw -Pnative native:compile
./mvnw -Pnative spring-boot:build-image
```

**Tradeoffs:**
- No JIT → peak throughput can be lower for long-running workloads
- Reflection / dynamic proxies need extra config (Spring AOT handles most of it)
- Longer build times
- Ideal for serverless / short-lived / autoscaling scenarios where cold-start matters

Not for every app, but worth knowing exists — increasingly common in modern Spring shops.

---

## A14. Practice Path (Java-Specific)

1. **Dockerize an existing Spring Boot app** with the "wrong way" Dockerfile from A3. Time the build. Measure the image size.
2. **Rewrite it** using multi-stage + layered JAR + non-root (A9). Compare image size and rebuild time after a single-class change.
3. **Set memory correctly:** run it with `--memory=512m` and no JVM options. Watch it OOM. Add `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` and confirm the JVM heap sizes correctly (check with `jcmd 1 VM.info` inside the container).
4. **Try Jib:** add the jib-maven-plugin, run `mvn compile jib:dockerBuild`. Compare image to your handwritten one.
5. **Try Buildpacks:** `./mvnw spring-boot:build-image`. Inspect the resulting image with `dive`.
6. **Compose stack:** wire your app to Postgres via Compose. Use healthchecks + startup ordering.
7. **Testcontainers:** write one integration test that starts a real Postgres in a container.
8. **BuildKit cache mount:** add `--mount=type=cache,target=/root/.m2`. Compare cold vs warm builds.

---

## A15. Java-in-Docker Interview Questions

**Q: How do you package a Spring Boot app into a Docker image?**
> Multi-stage Dockerfile: build stage on `eclipse-temurin:21-jdk` runs `mvnw package`; runtime stage on `eclipse-temurin:21-jre-alpine` copies the JAR and runs it. For better caching, use Spring Boot's layered JAR feature and copy each layer separately, so a code change doesn't invalidate the (large, rarely-changing) dependencies layer. Alternatively, use Spring Boot's built-in buildpacks (`mvn spring-boot:build-image`) or Jib for a Dockerfile-free approach.

**Q: Why not use `openjdk:21` as your base image?**
> The `openjdk` image is deprecated. Use `eclipse-temurin` (Eclipse Adoptium, formerly AdoptOpenJDK) — well-maintained, permissively licensed, and available in JDK + JRE + Alpine variants. For production, consider distroless (`gcr.io/distroless/java21`) for smaller size and reduced attack surface.

**Q: How do you set JVM memory in a container?**
> Use `-XX:MaxRAMPercentage=75.0` instead of hardcoded `-Xmx`. Modern JVMs (10+) read cgroup memory limits and size the heap as a percentage of container memory. This means the same image works whether the container has 512 MB or 4 GB. Never allocate 100% — the JVM needs metaspace, thread stacks, direct buffers, and JIT code cache outside the heap.

**Q: What's the "container-aware JVM" thing?**
> Before JDK 10, JVMs saw the host's total memory and CPU, not the container's cgroup limits. Result: JVMs allocated based on host size, then got OOM-killed by the container. JDK 10+ (and 8u191+ backport) reads cgroup limits directly. On modern Java, `--cpus=2` gives the JVM 2 CPUs for GC threads, thread pools, etc.

**Q: Why use Spring Boot's layered JARs?**
> A Spring Boot fat JAR is a single ~150 MB file. If you `COPY` it into a Docker layer, any code change invalidates the whole layer — you re-push 150 MB per commit. Layered JARs split the fat JAR into dependencies, spring-boot-loader, snapshot-deps, and application classes. Each becomes its own Docker layer, so changing one class only rebuilds the small `application` layer.

**Q: When would you use Jib or buildpacks over a Dockerfile?**
> Jib and buildpacks generate optimized, secure, layered images with no Dockerfile needed. They enforce good defaults (non-root, layered, small base). Use them when you want speed + safety and don't need full control. Write a Dockerfile when you need custom base images, special build steps, or your team requires it. Jib is Dockerfile-free and needs no Docker daemon — great for CI environments where Docker-in-Docker is painful.

**Q: How do you speed up Docker builds for Java apps?**
> Copy `pom.xml` before `src/` and run `dependency:go-offline` first — deps become a cached layer. Use Spring Boot layered JARs so code changes don't invalidate dep layers. Use BuildKit cache mounts (`--mount=type=cache,target=/root/.m2`) so the Maven repo persists across builds. In CI, use `--cache-from` / `--cache-to` to share cache via the registry. Consider Jib for even faster builds.

**Q: What's Testcontainers and why does it matter?**
> A Java library that starts real Docker containers (Postgres, Kafka, Redis, etc.) from JUnit tests. Instead of mocking your DB, tests hit a real Postgres in a throwaway container. Catches migration issues, SQL syntax errors, real-world edge cases that mocks miss. Industry standard for integration tests in Java shops.

---

## Recommended Path for Java Developers

1. Do Phases 1–5 of the main guide (foundations, hands-on, Dockerfile, networking, Compose)
2. Come back to this appendix and dockerize one of your Spring Boot apps
3. Do Phase 6 (production) — apply everything to your Java image (non-root, MaxRAMPercentage, healthcheck against Actuator, layered JAR)
4. Learn Testcontainers deeply — it's job-relevant
5. Try Jib and buildpacks — know the tradeoffs
6. Optionally: GraalVM native images

That gives you Java-Docker mastery for staff-level interviews and real production work.
