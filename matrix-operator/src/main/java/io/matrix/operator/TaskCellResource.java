package io.matrix.operator;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * TaskCell CRD (DESIGN-07 §3/DESIGN-12): an ephemeral task instance that is
 * spawned for one job and dies when its budget is exhausted.
 */
@Group("matrix.io")
@Version("v1alpha1")
public class TaskCellResource extends CustomResource<TaskCellSpec, MatrixClusterStatus>
        implements io.fabric8.kubernetes.api.model.Namespaced {

    public static TaskCellResource create(String name, String namespace, TaskCellSpec spec) {
        TaskCellResource cr = new TaskCellResource();
        ObjectMeta meta = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build();
        cr.setMetadata(meta);
        cr.setSpec(spec);
        return cr;
    }
}
