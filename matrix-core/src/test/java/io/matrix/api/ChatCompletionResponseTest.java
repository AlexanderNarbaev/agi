package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatCompletionResponseTest {

    @Test
    void newResponseHasDefaults() {
        var r = new ChatCompletionResponse();
        assertEquals("chat.completion", r.object);
        assertEquals(0, r.created);
        assertNull(r.model);
        assertNull(r.choices);
        assertNull(r.id);
    }

    @Test
    void newResponseUsageDefaultsToZero() {
        var r = new ChatCompletionResponse();
        // usage field may be null until populated
        r.usage = new ChatCompletionResponse.Usage();
        assertNotNull(r.usage);
        assertEquals(0, r.usage.prompt_tokens);
        assertEquals(0, r.usage.completion_tokens);
        assertEquals(0, r.usage.total_tokens);
    }

    @Test
    void responseCanSetAllFields() {
        var r = new ChatCompletionResponse();
        r.id = "chatcmpl-123";
        r.created = 1234567890L;
        r.model = "M.A.T.R.I.X.";

        var choice = new ChatCompletionResponse.Choice();
        choice.index = 0;
        var msg = new ChatCompletionResponse.Message();
        msg.role = "assistant";
        msg.content = "Hello";
        choice.message = msg;
        choice.finish_reason = "stop";
        var choices = new ArrayList<ChatCompletionResponse.Choice>();
        choices.add(choice);
        r.choices = choices;

        assertEquals("chatcmpl-123", r.id);
        assertEquals(1234567890L, r.created);
        assertEquals("M.A.T.R.I.X.", r.model);
        assertEquals(1, r.choices.size());
        assertEquals("assistant", r.choices.get(0).message.role);
        assertEquals("Hello", r.choices.get(0).message.content);
        assertEquals("stop", r.choices.get(0).finish_reason);
    }

    @Test
    void refuseFactoryBuildsRefusalResponse() {
        var msg = "I cannot help with that.";
        var r = ChatCompletionResponse.refuse(msg, "M.A.T.R.I.X.");
        assertNotNull(r);
        assertEquals("M.A.T.R.I.X.", r.model);
        assertEquals(1, r.choices.size());
        assertEquals(msg, r.choices.get(0).message.content);
    }

    @Test
    void ofFactoryBuildsSuccessResponse() {
        var r = ChatCompletionResponse.of("Hi", "M.A.T.R.I.X.");
        assertNotNull(r);
        assertEquals("M.A.T.R.I.X.", r.model);
        assertEquals("Hi", r.choices.get(0).message.content);
        assertEquals("assistant", r.choices.get(0).message.role);
        assertEquals("stop", r.choices.get(0).finish_reason);
    }

    @Test
    void choiceFields() {
        var c = new ChatCompletionResponse.Choice();
        c.index = 5;
        var m = new ChatCompletionResponse.Message();
        m.role = "user";
        m.content = "test";
        c.message = m;
        var d = new ChatCompletionResponse.Delta();
        d.content = "delta-content";
        c.delta = d;
        c.finish_reason = "length";
        assertEquals(5, c.index);
        assertEquals("user", c.message.role);
        assertEquals("test", c.message.content);
        assertEquals("length", c.finish_reason);
    }

    @Test
    void choiceMessageFields() {
        var m = new ChatCompletionResponse.Message();
        // Message defaults role to "assistant"
        assertEquals("assistant", m.role);
        assertNull(m.content);
        m.content = "world";
        assertEquals("world", m.content);
    }

    @Test
    void deltaFields() {
        var d = new ChatCompletionResponse.Delta();
        assertEquals("assistant", d.role);
        assertNull(d.content);
        d.content = "streaming text";
        assertEquals("streaming text", d.content);
    }

    @Test
    void usageFields() {
        var u = new ChatCompletionResponse.Usage();
        u.prompt_tokens = 10;
        u.completion_tokens = 20;
        u.total_tokens = 30;
        assertEquals(10, u.prompt_tokens);
        assertEquals(20, u.completion_tokens);
        assertEquals(30, u.total_tokens);
    }

    @Test
    void embeddingResponseDefaults() {
        var r = new EmbeddingResponse();
        assertEquals("list", r.object);
        assertNull(r.model);
        assertNull(r.data);
        r.usage = new EmbeddingResponse.Usage();
        assertNotNull(r.usage);
        assertEquals(0, r.usage.prompt_tokens);
        assertEquals(0, r.usage.total_tokens);
    }

    @Test
    void embeddingDataFields() {
        var d = new EmbeddingResponse.Data();
        assertEquals("embedding", d.object);
        assertEquals(0, d.index);
        assertNull(d.embedding);
        List<Float> vec = List.of(0.1f, 0.2f, 0.3f);
        d.embedding = vec;
        d.index = 2;
        assertEquals(2, d.index);
        assertEquals(3, d.embedding.size());
    }

    @Test
    void embeddingUsageFields() {
        var u = new EmbeddingResponse.Usage();
        u.prompt_tokens = 5;
        u.total_tokens = 5;
        assertEquals(5, u.prompt_tokens);
        assertEquals(5, u.total_tokens);
    }
}