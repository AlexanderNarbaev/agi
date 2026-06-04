package io.matrix.operator;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("matrix.io")
@Version("v1alpha1")
public class MatrixCluster extends CustomResource<MatrixClusterSpec, MatrixClusterStatus>
        implements io.fabric8.kubernetes.api.model.Namespaced {

    public static MatrixCluster create(String name, String namespace, MatrixClusterSpec spec) {
        MatrixCluster cr = new MatrixCluster();
        ObjectMeta meta = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build();
        cr.setMetadata(meta);
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());
        return cr;
    }
}
