package io.matrix.operator;

/**
 * Desired state of a {@link SignalModuleResource}: a versioned
 * thought⇄media converter (DESIGN-06 contract).
 */
public class SignalModuleSpec {

    private String moduleName;
    private String version;
    private String mediaType;
    private boolean frozen;

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
}
