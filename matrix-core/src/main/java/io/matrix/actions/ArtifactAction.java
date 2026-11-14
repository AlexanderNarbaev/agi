package io.matrix.actions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * B3 — the action-contract adapter over {@link ArtifactWriter}.
 *
 * <p>The writer is the boundary; this adapter makes it executable through the project's
 * existing {@link ActionRegistry} contract, so the bounded file action is registered,
 * listed, pre-checked and post-checked exactly like every other action. It adds no
 * authority: the only executor it wraps is the writer's one file.</p>
 *
 * <p>Input keys: {@code actionId}, {@code fileName}, {@code content} (a {@code byte[]}).
 * The output is the writer's {@link ArtifactWriter.Result}. A refusal is reported as an
 * action failure with the disposition's name, so a caller cannot mistake it for success.</p>
 */
public final class ArtifactAction {

    /** The name the bounded file action is registered under. */
    public static final String NAME = "report.artifact.write";

    /** The contract version of the action. */
    public static final int VERSION = 1;

    private ArtifactAction() {
    }

    /**
     * The action, over one authorized writer.
     *
     * @param writer the bounded writer; its root is the only directory the action may write
     * @return the action, ready for {@link ActionRegistry#register(ActionRegistry.Action)}
     */
    public static ActionRegistry.Action of(ArtifactWriter writer) {
        return new ActionRegistry.Action(
                NAME,
                new ActionRegistry.IoSchema(
                        Map.of("actionId", String.class, "fileName", String.class,
                                "content", byte[].class),
                        ArtifactWriter.Result.class),
                List.of("actionId", "fileName", "content"),
                List.of("result"),
                input -> {
                    String actionId = String.valueOf(input.get("actionId"));
                    String fileName = String.valueOf(input.get("fileName"));
                    Object content = input.get("content");
                    byte[] bytes;
                    if (content instanceof byte[] array) {
                        bytes = array;
                    } else if (content instanceof String text) {
                        bytes = text.getBytes(StandardCharsets.UTF_8);
                    } else {
                        return ActionRegistry.ActionResult.failure(NAME,
                                "the content must be bytes or a string");
                    }
                    ArtifactWriter.Result result = writer.write(actionId, fileName, bytes);
                    if (!result.written()) {
                        // the Action contract wraps whatever the executor returns into a
                        // success, so a refusal is raised and the contract records it as a
                        // failure with this message
                        throw new IllegalStateException(result.disposition() + ": " + result.reason());
                    }
                    return result;
                });
    }
}
