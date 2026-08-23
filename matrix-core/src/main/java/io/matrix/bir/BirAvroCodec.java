package io.matrix.bir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

/**
 * Avro codec for BIR artifacts (SPEC-002 FR-A1).
 *
 * <p>Schema: {@code /avro/bir_artifact.avsc} on the classpath (derived from
 * {@code src/main/avro/bir.avdl}; keep in sync). Uses the Avro generic API —
 * the same idiom as {@code io.matrix.neuron.TruthTable} — so no codegen
 * plugin is required.
 *
 * <p>Determinism: {@code createdAt} is supplied by the caller; the codec
 * itself never reads the wall clock. Round-trips are exact: the payload is
 * the form's canonical {@code toBytes()}, so a deserialized artifact is
 * BDD-equivalent to the original (verified by tests).
 */
public final class BirAvroCodec {

    public static final String SCHEMA_VERSION = "1";

    private static volatile Schema cachedSchema;

    private BirAvroCodec() {}

    private static Schema schema() {
        Schema s = cachedSchema;
        if (s == null) {
            synchronized (BirAvroCodec.class) {
                s = cachedSchema;
                if (s == null) {
                    try (InputStream is = BirAvroCodec.class.getResourceAsStream("/avro/bir_artifact.avsc")) {
                        if (is == null) {
                            throw new IllegalStateException("Avro schema not found: /avro/bir_artifact.avsc");
                        }
                        s = new Schema.Parser().parse(is);
                        cachedSchema = s;
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load BIR Avro schema", e);
                    }
                }
            }
        }
        return s;
    }

    /** Serialize a BIR artifact to Avro binary. */
    public static byte[] encode(String id, Bir bir, long createdAt) {
        if (!(bir instanceof BirForm form)) {
            throw new IllegalArgumentException("Not a BirForm: " + bir.getClass());
        }
        if (form instanceof ClauseSetForm cs) {
            // payload format assumes every clause uses exactly kWords per mask
            int kWords = (cs.inputBits() + 63) / 64;
            for (ClauseSetForm.Clause c : cs.clauses()) {
                if (c.pos.length != kWords || c.neg.length != kWords) {
                    throw new IllegalArgumentException(
                            "clause mask length " + c.pos.length + "/" + c.neg.length
                                    + " != kWords " + kWords + " — not encodable");
                }
            }
        }
        GenericRecord record = new GenericData.Record(schema());
        record.put("id", id);
        record.put("form", form.form());
        record.put("inputBits", form.inputBits());
        record.put("outputBits", form.outputBits());
        record.put("provenance", form.provenance());
        record.put("fidelity", form.fidelity());
        record.put("payload", ByteBuffer.wrap(form.toBytes()));
        record.put("contentHash", ByteBuffer.wrap(form.contentHash()));
        record.put("createdAt", createdAt);
        record.put("version", SCHEMA_VERSION);
        try (var out = new ByteArrayOutputStream()) {
            var encoder = EncoderFactory.get().binaryEncoder(out, null);
            DatumWriter<GenericRecord> writer = new org.apache.avro.generic.GenericDatumWriter<>(schema());
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Avro encode failed", e);
        }
    }

    /** Decoded artifact: the BIR form plus header metadata. */
    public record Decoded(String id, Bir bir, byte[] contentHash, long createdAt, String version) {}

    /** Deserialize an Avro-binary artifact. */
    public static Decoded decode(byte[] bytes) {
        GenericRecord record;
        try (var in = new ByteArrayInputStream(bytes)) {
            var decoder = DecoderFactory.get().binaryDecoder(in, null);
            DatumReader<GenericRecord> reader = new org.apache.avro.generic.GenericDatumReader<>(schema());
            record = reader.read(null, decoder);
        } catch (IOException e) {
            throw new IllegalArgumentException("Avro decode failed", e);
        }
        String formName = record.get("form").toString();
        int inputBits = (Integer) record.get("inputBits");
        String provenance = record.get("provenance").toString();
        double fidelity = (Double) record.get("fidelity");
        byte[] payload = bytes((ByteBuffer) record.get("payload"));
        byte[] contentHash = bytes((ByteBuffer) record.get("contentHash"));

        Bir bir = switch (formName) {
            case "tt" -> decodeTt(inputBits, payload, provenance, fidelity);
            case "clauseset" -> decodeClauseSet(inputBits, payload, provenance, fidelity);
            case "bdd" -> decodeBdd(inputBits, payload, provenance, fidelity);
            default -> throw new IllegalArgumentException("Unknown BIR form: " + formName);
        };
        return new Decoded(record.get("id").toString(), bir, contentHash,
                (Long) record.get("createdAt"), record.get("version").toString());
    }

    private static byte[] bytes(ByteBuffer buf) {
        ByteBuffer copy = buf.duplicate();
        byte[] out = new byte[copy.remaining()];
        copy.get(out);
        return out;
    }

    private static TtForm decodeTt(int inputBits, byte[] payload, String provenance, double fidelity) {
        var buf = ByteBuffer.wrap(payload);
        int k = buf.getInt();
        if (k != inputBits) throw new IllegalArgumentException("payload k=" + k + " != header inputBits=" + inputBits);
        int len = buf.getInt();
        long[] table = new long[len];
        for (int i = 0; i < len; i++) table[i] = buf.getLong();
        return fidelity < 1.0
                ? TtForm.lossy(k, table, provenance, fidelity)
                : new TtForm(k, table, provenance, fidelity);
    }

    private static ClauseSetForm decodeClauseSet(int inputBits, byte[] payload, String provenance,
                                                 double fidelity) {
        var buf = ByteBuffer.wrap(payload);
        int kWords = buf.getInt();
        int count = buf.getInt();
        List<ClauseSetForm.Clause> clauses = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            long[] pos = new long[kWords];
            long[] neg = new long[kWords];
            for (int w = 0; w < kWords; w++) pos[w] = buf.getLong();
            for (int w = 0; w < kWords; w++) neg[w] = buf.getLong();
            clauses.add(new ClauseSetForm.Clause(pos, neg));
        }
        return fidelity < 1.0
                ? ClauseSetForm.lossy(inputBits, clauses, provenance, fidelity)
                : new ClauseSetForm(inputBits, clauses, provenance, fidelity);
    }

    private static BddForm decodeBdd(int inputBits, byte[] payload, String provenance, double fidelity) {
        if (fidelity < 1.0) {
            throw new IllegalArgumentException(
                    "lossy BDD (fidelity < 1.0) is not supported by the codec: " + fidelity);
        }
        var buf = ByteBuffer.wrap(payload);
        int nodeCount = buf.getInt();
        int root = buf.getInt();
        // nodes 0 and 1 are the terminal sentinels — consume and validate them
        for (int i = 0; i < 2 && i < nodeCount; i++) {
            int var = buf.getInt();
            int low = buf.getInt();
            int high = buf.getInt();
            if (var != -1 || low != i || high != i) {
                throw new IllegalArgumentException("BDD payload: terminal node " + i + " corrupted");
            }
        }
        var builder = new BddForm.Builder();
        for (int i = 2; i < nodeCount; i++) {
            int var = buf.getInt();
            int low = buf.getInt();
            int high = buf.getInt();
            int id = builder.mk(var, low, high);
            if (id != i) {
                throw new IllegalArgumentException(
                        "BDD payload not in canonical mk-order: node " + i + " re-mapped to " + id);
            }
        }
        return builder.build(inputBits, provenance, root);
    }
}
