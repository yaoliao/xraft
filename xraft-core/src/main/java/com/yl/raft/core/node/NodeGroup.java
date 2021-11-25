package com.yl.raft.core.node;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NodeGroup
 */
@Slf4j
public class NodeGroup {

    private final NodeId selfId;
    private Map<NodeId, GroupMember> memberMap;

    /**
     * Create group with single member(standalone).
     *
     * @param endpoint endpoint
     */
    NodeGroup(NodeEndpoint endpoint) {
        this(Collections.singleton(endpoint), endpoint.getId());
    }

    /**
     * Create group.
     *
     * @param endpoints endpoints
     * @param selfId    self id
     */
    NodeGroup(Collection<NodeEndpoint> endpoints, NodeId selfId) {
        this.memberMap = buildMemberMap(endpoints);
        this.selfId = selfId;
    }

    /**
     * Build member map from endpoints.
     *
     * @param endpoints endpoints
     * @return member map
     * @throws IllegalArgumentException if endpoints is empty
     */
    private Map<NodeId, GroupMember> buildMemberMap(Collection<NodeEndpoint> endpoints) {
        Map<NodeId, GroupMember> map = new HashMap<>();
        for (NodeEndpoint endpoint : endpoints) {
            map.put(endpoint.getId(), new GroupMember(endpoint));
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("endpoints is empty");
        }
        return map;
    }

    Set<NodeEndpoint> listEndpointOfMajorExceptSelf() {
        Set<NodeEndpoint> endpoints = new HashSet<>();
        for (GroupMember member : memberMap.values()) {
            if (!member.isMajor()) continue;
            if (!member.getEndpoint().getId().equals(selfId)) {
                endpoints.add(member.getEndpoint());
            }
        }
        return endpoints;
    }

    Set<NodeEndpoint> listEndpointOfMajor() {
        Set<NodeEndpoint> endpoints = new HashSet<>();
        for (GroupMember member : memberMap.values()) {
            if (member.isMajor()) {
                endpoints.add(member.getEndpoint());
            }
        }
        return endpoints;
    }

    Collection<GroupMember> listReplicationTarget() {
        return memberMap.values().stream().filter(m -> !m.getEndpoint().getId().equals(selfId)).collect(Collectors.toList());
    }

    @Nonnull
    GroupMember findMember(NodeId id) {
        GroupMember member = getMember(id);
        if (member == null) {
            throw new IllegalArgumentException("no such node " + id);
        }
        return member;
    }

    void resetReplicatingStates(int nextLogIndex) {
        for (GroupMember member : memberMap.values()) {
            if (!member.idEquals(selfId)) {
                member.setReplicatingState(new ReplicatingState(nextLogIndex));
            }
        }
    }

    @Nullable
    GroupMember getMember(NodeId id) {
        return memberMap.get(id);
    }

    public int getCount() {
        return memberMap.keySet().size();
    }

    int getMatchIndex() {
        List<NodeMatchIndex> matchIndices = new ArrayList<>();
        for (GroupMember member : memberMap.values()) {
            if (!member.idEquals(selfId)) {
                matchIndices.add(new NodeMatchIndex(member.getId(), member.getMatchIndex()));
            }
        }
        int count = matchIndices.size();
        if (count == 0) {
            throw new IllegalStateException("standalone or no major node");
        }
        Collections.sort(matchIndices);
        log.debug("match indices {}", matchIndices);
        return matchIndices.get(count / 2).getMatchIndex();
    }

    /**
     * 获取过半的 follower 的 matchIndex
     */
    int getMatchIndexOfMajor() {
        List<NodeMatchIndex> matchIndices = new ArrayList<>();
        for (GroupMember member : memberMap.values()) {
            if (member.isMajor()) {
                if (member.idEquals(selfId)) {
                    matchIndices.add(new NodeMatchIndex(selfId));
                } else {
                    matchIndices.add(new NodeMatchIndex(member.getId(), member.getMatchIndex()));
                }
            }
        }
        int count = matchIndices.size();
        if (count == 0) {
            throw new IllegalStateException("no major node");
        }
        if (count == 1 && matchIndices.get(0).nodeId == selfId) {
            throw new IllegalStateException("standalone");
        }
        Collections.sort(matchIndices);
        log.debug("match indices {}", matchIndices);
        int index = (count % 2 == 0 ? count / 2 - 1 : count / 2);
        return matchIndices.get(index).getMatchIndex();
    }

    boolean isStandalone() {
        return memberMap.size() == 1 && memberMap.containsKey(selfId);
    }

    public GroupMember findSelf() {
        return memberMap.get(selfId);
    }

    int getCountOfMajor() {
        return (int) memberMap.values().stream().filter(GroupMember::isMajor).count();
    }

    GroupMember addNode(NodeEndpoint endpoint, int nextIndex, int matchIndex, boolean major) {
        log.info("add node {} to group", endpoint.getId());
        ReplicatingState replicatingState = new ReplicatingState(nextIndex, matchIndex);
        GroupMember member = new GroupMember(endpoint, replicatingState, major);
        memberMap.put(endpoint.getId(), member);
        return member;
    }

    void updateNodes(Set<NodeEndpoint> endpoints) {
        memberMap = buildMemberMap(endpoints);
        log.info("group config changed -> {}", memberMap.keySet());
    }

    void removeNode(NodeId id) {
        log.info("node {} removed", id);
        memberMap.remove(id);
    }

    /**
     * Node match index.
     *
     * @see NodeGroup#getMatchIndexOfMajor()
     */
    private static class NodeMatchIndex implements Comparable<NodeMatchIndex> {

        private final NodeId nodeId;
        private final int matchIndex;
        private final boolean leader;

        NodeMatchIndex(NodeId nodeId) {
            this(nodeId, Integer.MAX_VALUE, true);
        }

        NodeMatchIndex(NodeId nodeId, int matchIndex) {
            this(nodeId, matchIndex, false);
        }

        private NodeMatchIndex(NodeId nodeId, int matchIndex, boolean leader) {
            this.nodeId = nodeId;
            this.matchIndex = matchIndex;
            this.leader = leader;
        }

        int getMatchIndex() {
            return matchIndex;
        }

        @Override
        public int compareTo(@Nonnull NodeMatchIndex o) {
            return Integer.compare(this.matchIndex, o.matchIndex);
        }

        @Override
        public String toString() {
            return "<" + nodeId + ", " + (leader ? "L" : matchIndex) + ">";
        }

    }

}
