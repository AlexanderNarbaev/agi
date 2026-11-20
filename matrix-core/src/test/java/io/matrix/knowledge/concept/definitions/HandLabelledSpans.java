package io.matrix.knowledge.concept.definitions;

import java.util.List;

/**
 * EPISTEMIC-INTEGRITY P1 — the FROZEN HAND-LABELLED SET of the A1 measurement.
 *
 * <p><b>What this is.</b> The acceptance measure A1 is "the concept
 * identification precision / recall on a frozen hand-labelled set of &ge;150
 * subject spans". The research's baseline labelled 40 spans (12 of them
 * genuine referents, 30 %); this set extends the draw to
 * <b>150 spans</b> and freezes it, so that the number cannot move with the
 * implementation.</p>
 *
 * <p><b>PROVENANCE — how the 150 were drawn.</b> The concept ids of the seed
 * corpus (the two licensed documents of
 * {@code matrix-core/src/main/resources/corpus/plate-tectonics/}) are sorted,
 * shuffled with {@link java.util.Random} seeded <b>20261115</b> — the research's
 * own seed — and the first 150 are taken. The draw therefore EXTENDS the
 * research's 40-name draw: the first 40 of this set are the research's 40, and
 * an independent labelling of them must land on the same share.</p>
 *
 * <p><b>PROVENANCE — how they were labelled.</b> By hand, by the implementer of
 * this wave (2026-11-16), against the claim's own sentence, with the protocol
 * below. The labels are a judgement about REFERENCE, not about grammaticality,
 * and the protocol is stated so that another labeller can repeat it.</p>
 *
 * <p><b>THE LABELLING PROTOCOL.</b> A span is labelled {@code true} (it IS a
 * concept) when it denotes a specific entity, process, mechanism, person,
 * place, organisation, document or theory that its claim is about. It is
 * labelled {@code false} when it denotes none of those: a pronoun or another
 * function word; a clause or a fragment of a clause; a caption; a discourse
 * nominal or a generic class ("the question", "these concepts", "The
 * mechanism", "such volcanoes", "Scientists"); a generic nominal ("the
 * evidence for ..."); or a span whose head is cut off by the extractor's verb
 * list. A leading discourse adverbial ("For example,", "In 1912,") is stripped
 * before the head is judged; an appositive is part of the phrase.</p>
 *
 * <p><b>WHAT THE SET DOES NOT SAY.</b> It says nothing about the RECALL of the
 * identity policy: the draw is over the concepts that were produced, so the
 * referents that were MISSED are out of frame. The recall of the identity half
 * is owner-gated and is not measured here.</p>
 */
final class HandLabelledSpans {

    /**
     * One labelled span: its position in the draw, the span itself, and the
     * judgement.
     *
     * @param position the position in the draw (1-based)
     * @param span the subject span, as the importer cut it
     * @param concept whether the span denotes a genuine referent of its claim
     */
    record HandLabel(int position, String span, boolean concept) {
    }

    /** The seed of the draw, the research's seed. */
    static final long DRAW_SEED = 20261115L;

    /** How many spans the draw takes. */
    static final int DRAW_SIZE = 150;

    /** How many of the first 40 the research's own draw contained. */
    static final int BASELINE_SAMPLE_SIZE = 40;

    /** The frozen set, in the draw's order. */
    static final List<HandLabel> LABELS = List.of(
            new HandLabel(1, "The Earth's unchanging size implies that the crust must", false),
            new HandLabel(2, "Plate tectonics", true),
            new HandLabel(3, "At zones of continent-to-continent rifting, divergent boundaries may", false),
            new HandLabel(4, "Dissipation of heat from the mantle", true),
            new HandLabel(5, "These", false),
            new HandLabel(6, "Example of calculations to", false),
            new HandLabel(7, "The tracks of hotspots", true),
            new HandLabel(8, "Two- and three-dimensional imaging of Earth's interior (seismic tomography)", true),
            new HandLabel(9, "During the 1920s, 1930s and 1940s, the former reached important milestones proposing that convection currents might", false),
            new HandLabel(10, "Dates derived", false),
            new HandLabel(11, "While the mechanism of such an impressive thermal event", false),
            new HandLabel(12, "For example, the African plate", true),
            new HandLabel(13, "The consequences of plate movement", true),
            new HandLabel(14, "Geological Survey", true),
            new HandLabel(15, "Continental drift In the late 19th and early 20th centuries, geologists", false),
            new HandLabel(16, "The map also", false),
            new HandLabel(17, "Initially these concepts", false),
            new HandLabel(18, "Relative significance of each driving force mechanism The vector of a plate's motion", false),
            new HandLabel(19, "despite its acceptance, it", false),
            new HandLabel(20, "Most", false),
            new HandLabel(21, "their data failed a \"magnetic reversal test\", which", false),
            new HandLabel(22, "In this understanding, plate motion", true),
            new HandLabel(23, "Above: The collision between the Indian and Eurasian plates", false),
            new HandLabel(24, "The mechanism", false),
            new HandLabel(25, "Such volcanoes", false),
            new HandLabel(26, "This theory, called \"surge tectonics\",", false),
            new HandLabel(27, "Before uplift, the area where they stand", false),
            new HandLabel(28, "Convergence can", false),
            new HandLabel(29, "The motion of the tectonic plates", true),
            new HandLabel(30, "Such earthquakes", false),
            new HandLabel(31, "In terms of heat transfer, the lithosphere loses heat by conduction, whereas the asthenosphere also", false),
            new HandLabel(32, "The evidence for such an erstwhile joining of these continents", false),
            new HandLabel(33, "Earth's lithosphere, the rigid outer shell of the planet including the crust and upper mantle,", false),
            new HandLabel(34, "As a consequence, a powerful source generating plate motion", false),
            new HandLabel(35, "By the late 1920s, seismologists", false),
            new HandLabel(36, "Scientists", false),
            new HandLabel(37, "Convergent boundaries (destructive boundaries or active margins)", true),
            new HandLabel(38, "Cool oceanic lithosphere", true),
            new HandLabel(39, "They demonstrated though that the westward drift, seen only for the past 30 Ma,", false),
            new HandLabel(40, "A trench", true),
            new HandLabel(41, "Modern plate tectonics", true),
            new HandLabel(42, "For these mechanisms to", false),
            new HandLabel(43, "Since that time, many theories", false),
            new HandLabel(44, "At a seafloor spreading ridge, plates", false),
            new HandLabel(45, "Other mechanisms generating this gravitational secondary force", false),
            new HandLabel(46, "Driving forces related to Earth rotation Alfred Wegener,", false),
            new HandLabel(47, "The much improved data from the WWSSN instruments", false),
            new HandLabel(48, "One explanation for Venus's lack of plate tectonics", false),
            new HandLabel(49, "The centrifugal force, which", false),
            new HandLabel(50, "This division should not", false),
            new HandLabel(51, "Role of water The role of water", false),
            new HandLabel(52, "Picture two giant conveyor belts, facing each other but slowly moving in opposite directions as they", false),
            new HandLabel(53, "One of the major outcomes of these datasets", false),
            new HandLabel(54, "At continental collision zones, there", false),
            new HandLabel(55, "Jason Morgan", true),
            new HandLabel(56, "The greater density of old lithosphere relative to the underlying asthenosphere", true),
            new HandLabel(57, "Starting from the idea (also expressed by his forerunners) that the present continents once", false),
            new HandLabel(58, "To", false),
            new HandLabel(59, "These ideas find their roots in the early 1930s in the", false),
            new HandLabel(60, "Wegener", true),
            new HandLabel(61, "Emergence of plate tectonics and past plate motions The timing of the emergence of plate tectonics on Earth", false),
            new HandLabel(62, "There", false),
            new HandLabel(63, "The relative importance and interaction of other", false),
            new HandLabel(64, "The theory of plate tectonics", true),
            new HandLabel(65, "As oceanic lithosphere", true),
            new HandLabel(66, "Plate boundary zones -- broad belts in which boundaries", false),
            new HandLabel(67, "History of the theory Summary The development of the theory of plate tectonics", false),
            new HandLabel(68, "Types of plate boundaries Three types of plate boundaries exist, characterized by the way the plates", false),
            new HandLabel(69, "This hypothesis linked these patterns to geomagnetic reversals and", false),
            new HandLabel(70, "An alternative explanation, though,", false),
            new HandLabel(71, "based on abnormalities in plumb line deflection by the Andes in Peru, Pierre Bouguer", false),
            new HandLabel(72, "\"MORVEL plate velocity", false),
            new HandLabel(73, "In the 1990s, it", false),
            new HandLabel(74, "Key principles The outer layers of Earth", false),
            new HandLabel(75, "Many", false),
            new HandLabel(76, "Slab pull", true),
            new HandLabel(77, "Easter Island monolith [80 k] Evidence of past rates of plate movement also can", false),
            new HandLabel(78, "When the new crust", false),
            new HandLabel(79, "Reasoning in an opposite way, the continents might", false),
            new HandLabel(80, "The distinction between oceanic crust and continental crust", true),
            new HandLabel(81, "Lateral density variations in the mantle", true),
            new HandLabel(82, "Some volcanoes", false),
            new HandLabel(83, "Such density variations can", false),
            new HandLabel(84, "Despite much opposition, the view of continental drift gained", false),
            new HandLabel(85, "Plate-boundary zones Not all plate boundaries", false),
            new HandLabel(86, "Wegener's", false),
            new HandLabel(87, "As a", false),
            new HandLabel(88, "In reality, this question", false),
            new HandLabel(89, "This finding, though unexpected,", false),
            new HandLabel(90, "The magma that", false),
            new HandLabel(91, "The closure of ocean basins can", false),
            new HandLabel(92, "because plate motions", false),
            new HandLabel(93, "Possible tidal effect on plate tectonics Of the many forces discussed above, tidal force", false),
            new HandLabel(94, "As old oceanic crust", true),
            new HandLabel(95, "The diversity of geodynamic settings and the properties of each plate", true),
            new HandLabel(96, "Geologists", false),
            new HandLabel(97, "Defining plate boundaries Active plate boundaries", false),
            new HandLabel(98, "The San Andreas", true),
            new HandLabel(99, "Between 1975 and 1984, the displacements", false),
            new HandLabel(100, "The theories find resonance in the modern theories which envisage hot spots or mantle plumes which", false),
            new HandLabel(101, "The Himalayas, towering as high as 8,854 m above sea level,", false),
            new HandLabel(102, "They also found that the oceanic crust", false),
            new HandLabel(103, "This theory", false),
            new HandLabel(104, "The reference points (small squares)", false),
            new HandLabel(105, "Observations made of the magnetic field of Mars by the Mars Global Surveyor spacecraft in 1999", false),
            new HandLabel(106, "Most movement", false),
            new HandLabel(107, "As the ocean plate splits, the ridge", true),
            new HandLabel(108, "The three most commonly", false),
            new HandLabel(109, "This process", false),
            new HandLabel(110, "During the 2020s, new proposals", false),
            new HandLabel(111, "Other plate boundary zones", true),
            new HandLabel(112, "Hess therefore", false),
            new HandLabel(113, "Seafloor spreading over the past 100 to 200 million years", true),
            new HandLabel(114, "In 1912, the meteorologist Alfred Wegener", true),
            new HandLabel(115, "Iceland", true),
            new HandLabel(116, "Space-geodetic data", false),
            new HandLabel(117, "GPS Satellite and Ground Receiver [63 k] Current plate movement can", false),
            new HandLabel(118, "The processes that", false),
            new HandLabel(119, "They commonly offset the active spreading ridges, producing zig-zag plate margins, and", false),
            new HandLabel(120, "the crust tends to buckle and", false),
            new HandLabel(121, "This force", false),
            new HandLabel(122, "Subduction processes in oceanic-oceanic plate convergence also", false),
            new HandLabel(123, "Convergent boundaries -- where crust", false),
            new HandLabel(124, "Past plate boundaries within existing plates", true),
            new HandLabel(125, "It", false),
            new HandLabel(126, "Tectonic plates may", false),
            new HandLabel(127, "Early subduction zones appear to", false),
            new HandLabel(128, "This research", false),
            new HandLabel(129, "The increasingly felsic nature of preserved rocks between 3 and 2.5 billion years ago implies that subduction zones", true),
            new HandLabel(130, "Definition and refining of the theory After all these considerations, plate tectonics (or, as it", false),
            new HandLabel(131, "The latter", false),
            new HandLabel(132, "The trenches", true),
            new HandLabel(133, "Slab pull may", false),
            new HandLabel(134, "From that moment onwards, discussions", false),
            new HandLabel(135, "a few", false),
            new HandLabel(136, "although the theory still", false),
            new HandLabel(137, "Magnetic stripe patterns", true),
            new HandLabel(138, "As the name implies, volcanic island arcs, which closely parallel the trenches,", false),
            new HandLabel(139, "This earthquake, within the subduction zone between the Nazca Plate and the South American Plate,", false),
            new HandLabel(140, "Plate tectonics came to", false),
            new HandLabel(141, "A symposium on continental drift", true),
            new HandLabel(142, "The concept that mountains", false),
            new HandLabel(143, "In reality, it", false),
            new HandLabel(144, "Earth might", false),
            new HandLabel(145, "The division", false),
            new HandLabel(146, "Only four years after the maps with the \"zebra pattern\" of magnetic stripes", false),
            new HandLabel(147, "Two months later, Xavier Le Pichon", true),
            new HandLabel(148, "Icy moons Exoplanets On Earth-sized planets, plate tectonics", false),
            new HandLabel(149, "One method of dealing with this problem", false),
            new HandLabel(150, "the fact that the North American plate", false));

    private HandLabelledSpans() {
    }
}
