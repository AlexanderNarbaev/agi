package io.matrix.pilots.edu;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EduAssessorTest {

    private final EduAssessor assessor = new EduAssessor();

    @Test
    void testHashPiiDeterministic() {
        String h1 = EduAssessor.hashPii("alice@school.edu");
        String h2 = EduAssessor.hashPii("alice@school.edu");
        assertEquals(h1, h2);
        assertTrue(h1.startsWith("stu_"));
    }

    @Test
    void testHashPiiDifferentInputs() {
        assertNotEquals(
            EduAssessor.hashPii("alice@school.edu"),
            EduAssessor.hashPii("bob@school.edu")
        );
    }

    @Test
    void testAssessEmptyResponses() {
        var result = assessor.assess("alice@school.edu", List.of());
        assertEquals(0, result.scores().size());
        assertEquals(0.0, result.overallMastery());
    }

    @Test
    void testAssessMixedResponses() {
        var responses = List.of(
            new EduAssessor.Response("algebra.linear.x", "x", true, 5000),
            new EduAssessor.Response("algebra.linear.y", "y", false, 8000),
            new EduAssessor.Response("algebra.linear.z", "z", true, 4000)
        );
        var result = assessor.assess("alice@school.edu", responses);
        assertTrue(result.hashedStudentId().startsWith("stu_"));
        assertEquals("alice@school.edu", "alice@school.edu");  // sanity
        assertEquals(1, result.scores().size());
        assertEquals(0.67, result.scores().get(0).mastery(), 0.01);
    }

    @Test
    void testRecommendNextStep() {
        var result = assessor.assess("u", List.of(
            new EduAssessor.Response("a.x", "", true, 0),
            new EduAssessor.Response("a.y", "", true, 0),
            new EduAssessor.Response("a.z", "", true, 0),
            new EduAssessor.Response("a.w", "", true, 0),
            new EduAssessor.Response("a.v", "", true, 0)
        ));
        assertEquals("Enrichment: introduce advanced topics", result.recommendedNextStep());
    }
}
