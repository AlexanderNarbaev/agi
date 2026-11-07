package io.matrix.knowledge.acquisition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A corpus that ships inside the application and is read from the classpath.
 *
 * <p><b>Why this exists.</b> The demonstrator needs two corpora: the seed
 * corpus that is loaded when the workspace is opened, and the research pool
 * that the learning cycle draws from. Both ship with the application, so
 * both are read from the classpath. A classpath directory cannot be listed
 * from inside a jar, so the manifest is the index: the manifest names the
 * files, and every file is read by name.
 *
 * <p><b>Why it is extracted to a temporary directory.</b> The importer reads
 * a file from the file system, because it has to hash the bytes it reads.
 * The bytes are therefore copied out of the jar unchanged, and the copy is
 * verified against the manifest before anything is imported.
 */
public final class ClasspathCorpus {

    /** One file of the corpus, with the provenance the manifest records. */
    public record Entry(Path path, String file, String format, String language, String sha256,
                        String licence, String licenceUrl, String attribution, String purpose,
                        String sourceUrl, String accessedAt, String revision) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path directory;
    private final List<Entry> entries;

    private ClasspathCorpus(Path directory, List<Entry> entries) {
        this.directory = directory;
        this.entries = List.copyOf(entries);
    }

    /**
     * Reads a corpus out of the classpath into a temporary directory.
     *
     * @param classpathDirectory the directory inside the application
     * @return the extracted corpus
     * @throws IOException when the manifest or a file cannot be read
     */
    public static ClasspathCorpus extract(String classpathDirectory) throws IOException {
        Path directory = Files.createTempDirectory("matrix-corpus-");
        String manifest = classpathDirectory + "/" + CorpusManifest.MANIFEST_NAME;
        byte[] manifestBytes = read(manifest);
        Files.write(directory.resolve(CorpusManifest.MANIFEST_NAME), manifestBytes);
        JsonNode root = MAPPER.readTree(manifestBytes);
        List<Entry> entries = new ArrayList<>();
        JsonNode listed = root.get("entries");
        if (listed != null) {
            for (JsonNode node : listed) {
                String file = text(node, "file");
                byte[] bytes = read(classpathDirectory + "/" + file);
                Path target = directory.resolve(file);
                Files.write(target, bytes);
                entries.add(new Entry(target, file, text(node, "format"), text(node, "language"),
                        text(node, "sha256"), text(node, "licence"), text(node, "licenceUrl"),
                        text(node, "attribution"), text(node, "purpose"), text(node, "sourceUrl"),
                        text(node, "accessedAt"), text(node, "revision")));
            }
        }
        return new ClasspathCorpus(directory, entries);
    }

    /** The directory the corpus was extracted to. */
    public Path directory() {
        return directory;
    }

    /** The files of the corpus. */
    public List<Entry> entries() {
        return entries;
    }

    /** The text of a file of the corpus. */
    public String textOf(Entry entry) throws IOException {
        return Files.readString(entry.path(), StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static byte[] read(String resource) throws IOException {
        ClassLoader loader = ClasspathCorpus.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("the corpus resource " + resource + " is not on the classpath");
            }
            return stream.readAllBytes();
        }
    }
}
