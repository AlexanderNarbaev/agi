package io.matrix.sdk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AnalyzeRequest {

    @NotBlank
    @Size(max = 1_048_576)
    @JsonProperty("input")
    public String input;

    @JsonProperty("content_type")
    public String contentType = "text";

    @Size(max = 65_536)
    @JsonProperty("context")
    public String context;

    @JsonProperty("model")
    public String model = "default";

    public AnalyzeRequest() {}

    public AnalyzeRequest(String input) {
        this.input = input;
    }

    public static AnalyzeRequest text(String text) {
        AnalyzeRequest r = new AnalyzeRequest();
        r.input = text;
        r.contentType = "text";
        return r;
    }

    public static AnalyzeRequest audio(String base64Audio) {
        AnalyzeRequest r = new AnalyzeRequest();
        r.input = base64Audio;
        r.contentType = "audio";
        return r;
    }

    public static AnalyzeRequest image(String base64Image) {
        AnalyzeRequest r = new AnalyzeRequest();
        r.input = base64Image;
        r.contentType = "image";
        return r;
    }
}
