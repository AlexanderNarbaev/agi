package io.matrix.research;

import io.matrix.neuron.Csv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 436 — Coverage for CSV parser/serializer.
 */
class Exp436CsvTest {

    @Test
    void parseLineSplitsOnCommas() {
        List<String> fields = Csv.parseLine("a,b,c");
        assertThat(fields).containsExactly("a", "b", "c");
    }

    @Test
    void parseLineHandlesEmptyFields() {
        List<String> fields = Csv.parseLine("a,,c");
        assertThat(fields).containsExactly("a", "", "c");
    }

    @Test
    void parseLineHandlesQuotedFields() {
        List<String> fields = Csv.parseLine("\"hello, world\",\"x\",y");
        assertThat(fields).containsExactly("hello, world", "x", "y");
    }

    @Test
    void parseLineHandlesEscapedQuotes() {
        List<String> fields = Csv.parseLine("\"he said \"\"hi\"\"\",\"x\"");
        assertThat(fields).containsExactly("he said \"hi\"", "x");
    }

    @Test
    void parseLineHandlesTrailingEmpty() {
        List<String> fields = Csv.parseLine("a,b,");
        assertThat(fields).containsExactly("a", "b", "");
    }

    @Test
    void formatLineQuotesFieldsWithCommas() {
        String s = Csv.formatLine(List.of("hello, world", "x", "y"));
        assertThat(s).isEqualTo("\"hello, world\",x,y");
    }

    @Test
    void roundTripPreservesData() {
        List<String> input = List.of("a", "b,c", "d\"e\"", "", "f");
        String formatted = Csv.formatLine(input);
        List<String> parsed = Csv.parseLine(formatted);
        assertThat(parsed).containsExactlyElementsOf(input);
    }
}
