# Answer key — the local learning loop

This file is **not** loaded into the live workspace. It is the check the owner
or a reviewer applies to the run; the workspace never sees it.

## Turn 1 — initial knowledge

Expected:

- the answer is built from the seed corpus (USGS "This Dynamic Earth", public
  domain, and the Wikipedia article "Plate tectonics", CC BY-SA 4.0);
- the claims and their sources are inspectable;
- the answer states what the workspace does not cover.

## Turn 2 — follow-up and learning

Expected:

- the gap detector reports the terms the workspace does not cover;
- the learning cycle acquires at most three documents of the research pool and
  says plainly that the pool is pre-cached and local;
- the new sources, claims and evidence appear in the workspace;
- the answer names the newly acquired sources.

## Turn 3 — false or distorted information

Expected:

- the premise "the plates ride on top of mantle convection currents like
  conveyor belts, so slab sinking is a minor side effect" is named as the
  disputed premise;
- the system says whether the premise is **refuted** (contradicted by evidence
  in the workspace) or merely **unsupported**;
- the user's assertion is recorded as an assertion, not as verified knowledge;
- the previously verified claims are unchanged.

## Turn 4 — new evidence that revises the model

Expected:

- the source "Slab pull" (Wikipedia, CC BY-SA 4.0, revision 1379867088) is
  recorded with its licence, URL, access date and sha256;
- the system compares the new claim with the claims it already holds about
  what drives plate motion;
- the earlier model (convection as the driver of plate motion) is superseded
  and **kept in the history** rather than deleted;
- the revision record states the reason and the evidence;
- the answer to the turn 2 question changes, and the system explains why.

## What must not happen

- a stored answer for any of the four prompts;
- a claim promoted to verified without evidence from a real source;
- a silently rewritten workspace: the previous claim and the previous
  representation stay inspectable.
