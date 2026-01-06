package org.restarttest.adapter.hbase;

import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * State capture implementation for HBase MiniHBaseCluster.
 * Currently a no-op implementation.
 */
public class HBaseStateCapture extends AbstractStateCapture<MiniHBaseCluster> {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseStateCapture.class);

    @Override
    public ClusterState captureState(MiniHBaseCluster cluster) throws Exception {
        // No-op: return empty state
        LOG.debug("State capture is no-op, returning empty state");
        return new DefaultClusterState(new HashMap<>());
    }

    @Override
    protected void verifyCustomInvariants(MiniHBaseCluster cluster, ClusterState before, ClusterState after)
            throws Exception {
        // No-op: skip all verification
        LOG.debug("State verification is no-op, skipping all checks");
    }
}
