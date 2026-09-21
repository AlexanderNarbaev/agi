package io.matrix.billing;

/**
 * WAVE T-07 — License Types.
 *
 * Determines what the user is permitted to do. Bound to a LicenseKey
 * signed by the MATRIX private key (Ed25519, verified by JDK 15+ APIs).
 */
public enum LicenseType {
    /** Open source: research, personal projects. Limited commercial use. */
    COMMUNITY("Community License", "Apache-2.0"),

    /** Paid commercial: full features, no redistribution. */
    COMMERCIAL("Commercial License", "Proprietary"),

    /** Enterprise: SLA, on-prem, custom contract. */
    ENTERPRISE("Enterprise License", "Proprietary"),

    /** Partner program: revenue share for builders. */
    PARTNER("Partner License", "Proprietary");

    public final String displayName;
    public final String legalFramework;

    LicenseType(String displayName, String legalFramework) {
        this.displayName = displayName;
        this.legalFramework = legalFramework;
    }
}
