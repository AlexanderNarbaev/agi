package io.matrix.scaling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClusterDeployerTest {

    @Test
    void testCreateDeployer() {
        ClusterDeployer deployer = new ClusterDeployer();
        assertNotNull(deployer);
    }

    @Test
    void testGenerateKubernetesPlan() {
        ClusterDeployer deployer = new ClusterDeployer();
        ClusterDeployer.ClusterConfig config = new ClusterDeployer.ClusterConfig(
            ClusterDeployer.Platform.KUBERNETES, 10, 64, 16, "matrix");
        ClusterDeployer.DeploymentPlan plan = deployer.generatePlan(config);

        assertEquals(10, plan.estimatedPods());
        assertTrue(plan.yamlManifests().get(0).contains("Deployment"));
        assertTrue(plan.commands().get(0).contains("helm install"));
    }

    @Test
    void testGenerateSparkPlan() {
        ClusterDeployer deployer = new ClusterDeployer();
        ClusterDeployer.ClusterConfig config = new ClusterDeployer.ClusterConfig(
            ClusterDeployer.Platform.SPARK, 100, 128, 32, "matrix");
        ClusterDeployer.DeploymentPlan plan = deployer.generatePlan(config);
        assertTrue(plan.commands().get(0).contains("spark-submit"));
    }

    @Test
    void testEstimateTotalMemory() {
        ClusterDeployer deployer = new ClusterDeployer();
        ClusterDeployer.ClusterConfig config = new ClusterDeployer.ClusterConfig(
            ClusterDeployer.Platform.KUBERNETES, 1000, 64, 16, "matrix");
        long mem = deployer.estimateTotalMemory(config);
        assertEquals(1000L * 64 * 1024 * 1024 * 1024, mem);
    }

    @Test
    void testHelmChartContainsResources() {
        ClusterDeployer deployer = new ClusterDeployer();
        ClusterDeployer.ClusterConfig config = new ClusterDeployer.ClusterConfig(
            ClusterDeployer.Platform.KUBERNETES, 5, 32, 8, "test");
        ClusterDeployer.DeploymentPlan plan = deployer.generatePlan(config);
        String chart = plan.yamlManifests().get(0);
        assertTrue(chart.contains("32Gi"));
        assertTrue(chart.contains("replicas: 5"));
    }
}
