package io.matrix.economy;

import io.matrix.noosphere.CreditModel;
import io.matrix.noosphere.FnlPackage;

import java.util.ArrayList;
import java.util.List;

/**
 * Regenerative Economics coordinator — integrates credit model,
 * audit trail, spiral certification, and cooperative resource pool.
 *
 * <p>Principles:
 * <ul>
 * <li>Every credit operation is transparent and auditable</li>
 * <li>Certified FNLs earn bonus credits and priority access</li>
 * <li>Idle resources are shared, not wasted</li>
 * <li>Penalties fund the commons, not profit extraction</li>
 * </ul>
 *
 * <p>Ref: L8_Roadmap.md §3.9
 */
public class RegenerativeEconomics {

    private final CreditModel creditModel;
    private final AuditTrail auditTrail;
    private final SpiralCertification certification;
    private final CooperativePool pool;
    private final List<String> economyLog = new ArrayList<>();

    public RegenerativeEconomics() {
        this.creditModel = new CreditModel();
        this.auditTrail = new AuditTrail();
        this.certification = new SpiralCertification();
        this.pool = new CooperativePool();
    }

    /**
     * Full publication flow with certification bonus.
     */
    public double publishFnl(FnlPackage fnl) {
        double reward = creditModel.awardPublication(fnl.authorInstanceId(), fnl);

        double balance = creditModel.getCredits(fnl.authorInstanceId()).balance();
        auditTrail.record(AuditTrail.TransactionType.PUBLICATION_REWARD,
                fnl.authorInstanceId(), reward, balance,
                "Published: " + fnl.name());

        economyLog.add("PUBLISH:" + fnl.name() + " reward=" + reward);
        return reward;
    }

    /**
     * Certifies an FNL and awards bonus credits.
     */
    public SpiralCertification.CertificationReport certify(String fnlId,
                                                              String authorId,
                                                              List<String> dataSources,
                                                              double energyFootprint,
                                                              boolean prohibitionsOk,
                                                              boolean culturalSensitive) {
        var report = certification.evaluate(fnlId, authorId, dataSources,
                energyFootprint, prohibitionsOk, culturalSensitive);

        if (report.isCertified()) {
            double bonus = report.level() == SpiralCertification.CertificationLevel.REGENERATIVE
                    ? 15.0 : 10.0;
            double balance = creditModel.getCredits(authorId).balance() + bonus;
            auditTrail.record(AuditTrail.TransactionType.CERTIFICATION_BONUS,
                    authorId, bonus, balance,
                    "Certified: " + fnlId + " level=" + report.level());
            economyLog.add("CERTIFY:" + fnlId + " level=" + report.level()
                    + " bonus=" + bonus);
        }

        return report;
    }

    /**
     * Contributes idle resources and earns credits.
     */
    public double contributeResources(String instanceId, double cpu,
                                        double memory, double storage) {
        double credits = pool.contribute(instanceId, cpu, memory, storage);

        double balance = creditModel.getCredits(instanceId).balance() + credits;
        auditTrail.record(AuditTrail.TransactionType.RESOURCE_CONTRIBUTION,
                instanceId, credits, balance,
                String.format("CPU=%.1f, MEM=%.1fGB, STOR=%.1fGB", cpu, memory, storage));

        economyLog.add("CONTRIBUTE:" + instanceId + " credits=" + credits);
        return credits;
    }

    /**
     * Attempts to download an FNL (charges credits).
     */
    public boolean downloadFnl(String consumerId, String fnlName) {
        boolean allowed = creditModel.chargeDownload(consumerId);

        if (allowed) {
            double balance = creditModel.getCredits(consumerId).balance();
            auditTrail.record(AuditTrail.TransactionType.DOWNLOAD_CHARGE,
                    consumerId, -1.0, balance,
                    "Downloaded: " + fnlName);
            economyLog.add("DOWNLOAD:" + consumerId + " → " + fnlName);
        } else {
            economyLog.add("DOWNLOAD:DENIED:" + consumerId + " insufficient credits");
        }

        return allowed;
    }

    public CreditModel creditModel() { return creditModel; }

    public AuditTrail auditTrail() { return auditTrail; }

    public SpiralCertification certification() { return certification; }

    public CooperativePool pool() { return pool; }

    public List<String> economyLog() { return List.copyOf(economyLog); }
}
