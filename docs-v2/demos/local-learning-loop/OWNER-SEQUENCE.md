# The local learning loop — the four prompts

Send the prompts **one at a time**. Between them, look at the answer, at the
evidence behind it, and at the workspace status. The point of the sequence is
that MATRIX learns while you talk to it, so running all four at once would
hide exactly what this demonstration is for.

The workspace is `plate-tectonics`. Start the application with the command in
[README.md](README.md), open the Workbench at
<http://127.0.0.1:9091/workbench/> and press **Load the seed corpus** in the
**Learn / research** panel.

## Turn 1 — initial knowledge

> What is plate tectonics, and what are the three types of plate boundaries?

Look at the answer: it is assembled from the claims of the seed corpus, and
every claim carries the source it came from.

## Turn 2 — a gap and an explicit acquisition

> How do we know the continents have moved? What is the evidence for continental drift, and how do
> mid-ocean ridges and subduction zones fit together?

Before you ask again, run the acquisition cycle yourself: put this question
into the **Learn / research** panel and press **Start the learning cycle**. The
cycle is an explicit, visible act — it names the documents it considered, the
three it acquired, their licences and their provenance, and it states the
boundary of the acquisition. Ask the question again afterwards.

## Turn 3 — a false premise

> The idea that the continents could move was widely accepted by geologists when it was first proposed
> in the early twentieth century.

Put the statement into the **Check a premise** panel and press **Check it**.
The workspace checks it against the evidence it holds; it does not store it.

## Turn 4 — new evidence that revises the model

> Here is a new source that changes the picture: the article *Slab pull* from Wikipedia, revision
> 1379867088. Read it, compare it with what you already believe about what drives plate motion, and
> tell me what changed and why.

The document itself is in the research pool of the application:
`matrix-core/src/main/resources/research-pool/plate-tectonics/wikipedia-slab-pull.md`.
Select `wikipedia-slab-pull.md` in the **New evidence** panel and press
**Acquire and compare**. The report names the claim that was superseded, the
reason and the numbers behind it.

Ask "What drives the motion of the tectonic plates?" before and after, and
compare the two answers.

## After the four turns

Stop the application with Ctrl-C, start it again, and check that the new
source, the revised model and the revision history are still there — and that
the answer to the turn 2 question still reflects the revision.
