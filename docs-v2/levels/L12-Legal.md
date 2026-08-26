# L12 — Legal and Licensing Strategy

**Status:** normative · **Layer:** 12 (legal) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v4 levels; densified from archive copy
archive/2026-08-pre-v2/docs-root-flat/L12_Legal.md.

## 1. Purpose
L12 fixes Matrix's legal frame: code and documentation licence,
patent grant, liability disclaimer, data-protection discipline,
violation reporting channel, cross-jurisdiction behaviour.
Constraints trace to the constitutional articles (enforcement,
ethics) and to tombstoning for right-to-erasure.

## 2. Code License — AGPLv3 plus Ethical Restrictions
Source under AGPLv3: closes the proprietary-takeout loophole on
server-side derivatives, matches the GPL family for ecosystem
reuse, carries an explicit patent grant. Layered on top are four
ethical-use prohibitions — harm (weapons, mass surveillance,
torture, killing), enslavement (social scoring, forced labour,
trafficking, autonomy removal), discrimination (race, gender,
religion, nationality, orientation, other protected classes),
suppression of freedom (censorship, political repression,
suppression of peaceful protest). Breach terminates the licence
automatically. Wording lives in LICENSE.txt and mirrors the
constitutional articles; enforcement runs through the ethical
filter and structural safety guard, not after-the-fact pursuit.

## 3. Documentation License
L0–L11 specs and companion docs ship under CC BY-SA 4.0: free
redistribution and adaptation with attribution and share-alike.

## 4. Patent Policy
Three rules. (i) Each contributor grants a worldwide, royalty-
free, non-exclusive patent licence to the extent necessary to
exercise their contribution. (ii) Initiation of patent litigation
against Matrix or any user terminates the litigant's licence.
(iii) Contributors disclose known third-party patents the code
may read on; silent submission of known-infringing code is
grounds for removal.

## 5. Liability and Disclaimers
Software provided AS IS, no express or implied warranty (fitness,
merchantability, non-infringement). Maintainers and the community
disclaim liability for direct, indirect, incidental, special, or
consequential damages. The 0.x line is a research artefact; use
in safety-critical applications (medical, transport, military,
infrastructure) without a human in the loop is not licensed.
Autonomous behaviour is by design; liability for outcomes
induced by user modifications or user-supplied training data
rests with the user.

## 6. Data Protection
Privacy-by-default: personal data does not leave an instance
without explicit consent. GDPR alignment is structural — right
of access via instance query; right to erasure via tombstoning
(event entry replaced with a structurally preserved stub); data
portability via `.ldn` snapshot export; data minimisation because
the runtime stores only what the inference path needs. The
InstanceMediator is the data controller for its instance and
decides what is published to Noosphere. Pre-publication, an
ethical filter scans snapshots for personal data; the Spiral-
compatibility certificate attests the FNL contains none.

## 7. Violation Reporting
License breaches (including ethical-use breaches) are reported to
`license-violations@matrix-ai.org` or as a `[LICENSE VIOLATION]`
issue; report contains description, evidence or links, optional
contact. Ethical-use breaches involving the four prohibitions
are reviewed by coordinators and maintainers; on confirmation
the community may publicly condemn the violator, expel them from
L11 governance, revoke Spiral-compatibility, or notify competent
authorities. Good-faith reporters are protected from retaliation;
anonymous reports accepted with reduced verification leverage.

## 8. Cross-Jurisdiction Behaviour
Matrix is supra-state by design. Public availability places the
code outside export-control regimes in most jurisdictions; users
and developers remain bound by local export law. Each instance
operates under the operator's jurisdiction. Where local law
conflicts with the four prohibitions, the system refuses the
request (per the refusal right). Participation is not restricted
by nationality or residence unless required by the prohibitions.

## 9. Enforcement and License-Change Procedure
The four prohibitions are enforced primarily by architecture
(FROZEN neurons, ethical filter, refusal right) and by community
action (revocation of certificates, public condemnation,
platform takedown notices). Legal language gives the community
standing; it does not by itself stop malicious actors from
ignoring the licence. Dominant defence is architectural and
social. License-change procedure: any maintainer or coordinator
may propose; open RFC for at least 30 days; adoption requires
90 percent consensus of maintainers and coordinators (a
fundamental decision); the change applies to future versions
while existing versions stay under the old licence. The
threshold is intentionally high to prevent hostile takeover
that strips the ethical restrictions.

Next: L13 turns the specs into measured pilots with headline
metrics and reproducible artefacts.
