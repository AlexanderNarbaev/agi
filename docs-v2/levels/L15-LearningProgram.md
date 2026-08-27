# L15 — Learning Program

**Status:** normative · **Layer:** 15 (learning) · **Date:** 

## 1. Purpose
L15 lowers the entry barrier, transfers architectural
understanding, grows a pool of educators, and embeds the ethical
filter in every learning path so graduates reason about
consequences, not only syntax.
## 2. Principles
Four rules drive content. (i) Openness — base material is free
online; paid courses add depth, instructor feedback, and
certification, not access. (ii) Practicality — every module
ships with runnable code, tests, and visualisation; learning is
by doing. (iii) Inclusivity — three entry levels: visual sandbox
for non-programmers, API and source for experienced developers,
mathematical and philosophical essays for researchers.
(iv) Ethical awareness — each course includes a module on the
ethical filter and the four prohibitions.
## 3. Learner Tracks
Five tracks with measurable outcomes.

| Track | Who | Outcome | Time |
|---|---|---|---|
| Explorer | Curious newcomer | 5-minute concept grasp; tries sandbox | 15 min |
| Beginner developer | Knows basic programming | Writes and runs a first BirUnit | 1–2 weeks |
| Developer | Has experience | Becomes contributor; possibly maintainer | 8 weeks |
| Researcher | Studies theory | Reads formal models, contributes proofs | Self-paced |
| Educator | Wants to teach others | Certified, runs courses and hackathons | After Developer track plus pedagogy |

Beginner milestones: install and run a test instance; build an
BirUnit; ship a Gridworld agent (Pilot P1); land a first
pull request. Developer milestones: architectural walk-through
of L1–L8; mutation operators and fitness functions; lab work on
Pekko, Kafka, and HADES; publish and import an FNL through
Noosphere. Researcher milestones: formal MPDT model and its
relation to Boolean algebra; attack the open convergence
problem; model-check the ethical filter in TLA+. Educator
milestones: pass the Developer track; pedagogy module on
dialogue, facilitation, and assessment; certification with an
ethics case study.
## 4. Formats
Five channels. (a) Web Playground — WASM-compiled core in the
browser; create neurons, watch the genetic algorithm evolve,
visualise decision trees; goal is first five minutes of
interaction without installation. (b) Video course — 5–15 minute
episodes on philosophy and ethics, first neuron, genetic
algorithm, clusters, mediator and drivers, ethical filter,
distributed system, and Noosphere. (c) Jupyter notebooks —
interactive tutorials with runnable code, no repository clone
required. (d) Hackathons — 24 to 48 hour events with themes
(FNL for digit recognition, Minecraft agent, ethical
vulnerability fix, smallest FNL for a task); prizes are
Noosphere credits and gallery publication. (e) Documentation —
Diátaxis layers: tutorials, how-to guides, reference,
explanation.
## 5. Base Course — MPDT Neuron in 5 Minutes
Four steps. (1) Install in one minute: clone and run the build,
or open the browser sandbox. (2) Create a neuron in one minute
using the MPDT builder with two inputs and a random truth table;
call evaluate and print. (3) Train in two minutes with a genetic
trainer over 50 generations; print accuracy and confirm it
exceeds the published threshold on the tutorial dataset.
(4) Interpret in one minute by printing the trained neuron as a
decision tree.
## 6. Developer Deep Course — 8 Weeks
| Week | Topic | Practice |
|---|---|---|
| 1 | Philosophy, L0, install, first neuron | XOR neuron |
| 2 | L1 truth tables, decision trees, mutations | Mutation operators |
| 3 | L5 genetic algorithm, fitness | Train a Gridworld agent |
| 4 | L3 clusters, FNL, batched inference | 100-neuron cluster |
| 5 | L4 mediator, drivers, proactivity | Chat bot via D_social |
| 6 | L7 ethical filter, multimodal proxy | Block harmful commands |
| 7 | L2 and L6 Kafka, consensus, Noosphere | Exchange FNL between instances |
| 8 | L5 Cauldron, HADES, final project | Present and code-review |
## 7. Certification, Community, Roadmap
Three certification levels. Matrix Developer — completes the
deep course and a passing final project; permanent. Matrix
Architect — at least three accepted core pull requests plus an
oral exam; permanent. Matrix Educator — pedagogy module plus
two delivered workshops; valid 24 months. All levels include an
ethics case study; verification combines automated CI tests
with manual maintainer review and, for Architect and Educator,
an oral interview. Community: mentoring pairs experienced
participants with newcomers through weekly calls, pair
programming, and review of first pull requests; pair-programming
sessions stream or record for public access; Q&A lives in GitHub
Discussions for long threads, a chat channel for quick answers,
and a weekly Office Hours video call. Authoring: OBS Studio and
DaVinci Resolve for video; Mermaid and Excalidraw for diagrams;
Jupyter Book and mdBook for interactive tutorials; CheerpJ or
manual WASM for the in-browser sandbox. Roadmap: phases 0–1
quick start, notebooks, first video; phase 2 base video course,
web sandbox, first hackathons; phase 3 deep course,
certification, mentoring; phase 4 and beyond full learning
platform, university partnerships.
Next: L16 covers physical interfaces — microcontrollers, ROS 2, FPGA, accelerators.