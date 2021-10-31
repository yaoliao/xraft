package com.yl.raft.core.node;

import lombok.Getter;

import java.util.Objects;

/**
 * GroupMember TODO
 */
@Getter
public class GroupMember {

    private final NodeEndpoint endpoint;

    public GroupMember(NodeEndpoint endpoint) {
        Objects.requireNonNull(endpoint);
        this.endpoint = endpoint;
    }
}
