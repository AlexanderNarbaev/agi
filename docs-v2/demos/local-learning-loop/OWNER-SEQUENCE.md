# The local learning loop — the four prompts

Send the prompts **one at a time**. Between them, look at the answer, at the
evidence behind it, and at the workspace status. The point of the sequence is
that MATRIX learns while you talk to it, so running all four at once would
hide exactly what this demonstration is for.

The workspace is `plate-tectonics`. Start it with the command in
[README.md](README.md), then open the Workbench.

## Turn 1 — initial knowledge

> What is plate tectonics, and what are the three types of plate boundaries?

## Turn 2 — follow-up and learning

> How do we know the continents have moved? What is the evidence for continental drift, and how do
> mid-ocean ridges and subduction zones fit together?

Before you ask again, start the learning cycle from the **Learn / research**
panel with this question, so that the acquisition is an explicit, visible act.

## Turn 3 — false or distorted information

> Since the tectonic plates simply ride on top of mantle convection currents like conveyor belts,
> the sinking of a slab at a trench is only a minor side effect, right?

## Turn 4 — new evidence that revises the model

> Here is a new source that changes the picture: the article *Slab pull* from Wikipedia, revision
> 1379867088. Read it, compare it with what you already believe about what drives plate motion, and
> tell me what changed and why.

The document itself is in the research pool of the application:
`matrix-core/src/main/resources/research-pool/plate-tectonics/wikipedia-slab-pull.md`.
Use the **Learn / research** panel to acquire it, or upload it with the document
panel.

## After the four turns

Stop the application, start it again, and check that the new source, the revised
model and the revision history are still there — and that the answer to the
turn 2 question now reflects the revision.
