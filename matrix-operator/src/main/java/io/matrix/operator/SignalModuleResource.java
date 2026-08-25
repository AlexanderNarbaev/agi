package io.matrix.operator;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * SignalModule CRD (DESIGN-06/DESIGN-07 §7 registry): a versioned
 * thought⇄media converter module deployed into the mesh.
 */
@Group("matrix.io")
@Version("v1alpha1")
public class SignalModuleResource extends CustomResource<SignalModuleSpec, MatrixClusterStatus>
        implements io.fabric8.kubernetes.api.model.Namespaced {

    public static SignalModuleResource create(String name, String namespace, SignalModuleSpec spec) {
        SignalModuleResource cr = new SignalModuleResource();
        ObjectMeta meta = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build();
        cr.setMetadata(meta);
        cr.setSpec(spec);
        return cr;
    }
}
