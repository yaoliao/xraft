package com.yl.raft.kvstore.server;

import com.yl.raft.core.node.NodeId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * RemoveNodeCommand
 */
@Getter
@Setter
@AllArgsConstructor
public class RemoveNodeCommand {

    private final NodeId nodeId;

}
