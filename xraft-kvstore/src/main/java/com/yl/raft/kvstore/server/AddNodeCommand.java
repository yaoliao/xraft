package com.yl.raft.kvstore.server;

import com.yl.raft.core.node.NodeEndpoint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * AddNodeCommand
 */
@Getter
@Setter
@AllArgsConstructor
public class AddNodeCommand {

    private final String nodeId;
    private final String host;
    private final int port;

    public NodeEndpoint toNodeEndpoint() {
        return new NodeEndpoint(nodeId, host, port);
    }
}
