package com.yl.raft.core.node;

import com.google.common.eventbus.Subscribe;
import com.yl.raft.core.log.AppendEntriesState;
import com.yl.raft.core.log.InstallSnapshotState;
import com.yl.raft.core.log.entry.AddNodeEntry;
import com.yl.raft.core.log.entry.EntryMeta;
import com.yl.raft.core.log.entry.RemoveNodeEntry;
import com.yl.raft.core.log.event.SnapshotGenerateEvent;
import com.yl.raft.core.log.event.SnapshotGeneratedEvent;
import com.yl.raft.core.log.snapshot.EntryInSnapshotException;
import com.yl.raft.core.log.statemachine.StateMachine;
import com.yl.raft.core.node.role.*;
import com.yl.raft.core.node.store.NodeStore;
import com.yl.raft.core.node.task.*;
import com.yl.raft.core.rpc.message.*;
import com.yl.raft.core.schedule.ElectionTimeout;
import com.yl.raft.core.schedule.LogReplicationTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * NodeImpl
 */
@Slf4j
@Getter
public class NodeImpl implements Node {

    private final NodeContext context;
    private volatile boolean started;
    private AbstractNodeRole role;

    private final NewNodeCatchUpTaskGroup newNodeCatchUpTaskGroup = new NewNodeCatchUpTaskGroup();
    private final NewNodeCatchUpTaskContext newNodeCatchUpTaskContext = new NewNodeCatchUpTaskContextImpl();
    private volatile GroupConfigChangeTaskReference currentGroupConfigChangeTaskReference
            = new FixedResultGroupConfigTaskReference(GroupConfigChangeTaskResult.OK);
    private volatile GroupConfigChangeTask currentGroupConfigChangeTask = GroupConfigChangeTask.NONE;
    private final GroupConfigChangeTaskContext groupConfigChangeTaskContext = new GroupConfigChangeTaskContextImpl();


    public NodeImpl(NodeContext context) {
        this.context = context;
    }

    @Override
    public synchronized void start() {
        if (started) {
            log.warn("node has been started");
            return;
        }

        // 注册事件处理
        context.getEventBus().register(this);

        // 初始化 rpc 连接
        context.getConnector().initialize();

        // 应用最新集群配置
        Set<NodeEndpoint> lastGroup = context.getLog().getLastGroup();
        context.getGroup().updateNodes(lastGroup);

        // 启动的时候节点是 Follower 角色
        // 1、将持久化的任期和投票信息重新赋值 2、启动选举超时定时器
        NodeStore store = context.getStore();
        FollowerNodeRole nodeRole = new FollowerNodeRole(store.getTerm(), store.getVotedFor(),
                null, 0, scheduleElectionTimeout());
        changeToRole(nodeRole);

        started = true;
    }

    @Override
    public void registerStateMachine(@Nonnull StateMachine stateMachine) {
        Objects.requireNonNull(stateMachine);
        context.getLog().setStateMachine(stateMachine);
    }

    @Override
    public void appendLog(@Nonnull byte[] commandBytes) {
        Objects.requireNonNull(commandBytes);
        ensureLeader();
        context.getTaskExecutor().submit(() -> {
            context.getLog().appendEntry(role.getTerm(), commandBytes);
            doReplicateLog();
        });
    }

    @Override
    public RoleNameAndLeaderId getRoleNameAndLeaderId() {
        return role.getNameAndLeaderId(context.getSelfId());
    }

    @Nonnull
    @Override
    public GroupConfigChangeTaskReference addNode(@Nonnull NodeEndpoint endpoint) {
        Objects.requireNonNull(endpoint);
        ensureLeader();

        if (context.getSelfId().equals(endpoint.getId())) {
            throw new IllegalArgumentException("new node cannot be self");
        }

        NewNodeCatchUpTask newNodeCatchUpTask = new NewNodeCatchUpTask(newNodeCatchUpTaskContext, endpoint, context.getConfig());

        if (!newNodeCatchUpTaskGroup.add(newNodeCatchUpTask)) {
            throw new IllegalArgumentException("node " + endpoint.getId() + " is adding");
        }

        NewNodeCatchUpTaskResult taskResult;
        try {
            // 开始向新加入的节点复制日志，该方法一直阻塞，直到复制完成或者超时为止
            taskResult = newNodeCatchUpTask.call();
            switch (taskResult.getState()) {
                case TIMEOUT:
                    return new FixedResultGroupConfigTaskReference(GroupConfigChangeTaskResult.TIMEOUT);
                case REPLICATION_FAILED:
                    new FixedResultGroupConfigTaskReference(GroupConfigChangeTaskResult.REPLICATION_FAILED);
            }
        } catch (Exception e) {
            if (!(e instanceof InterruptedException)) {
                log.warn("failed to catch up new node " + endpoint.getId(), e);
            }
            return new FixedResultGroupConfigTaskReference(GroupConfigChangeTaskResult.ERROR);
        }

        // 等待上一个任务完成
        GroupConfigChangeTaskResult result = awaitPreviousGroupConfigChangeTask();
        if (result != null) {
            return new FixedResultGroupConfigTaskReference(result);
        }

        synchronized (this) {

            if (currentGroupConfigChangeTask != GroupConfigChangeTask.NONE) {
                throw new IllegalStateException("group config change concurrently");
            }
            // AddNodeTask 会先添加一个 addNode 的日志条目，并将集群配置应用到本地，接着开始向 follower 节点复制日志，并且该任务开始阻塞，
            // 直到新添加的那个 addNode 的日志条目被 commit 之后，该任务才从阻塞中被唤醒
            currentGroupConfigChangeTask = new AddNodeTask(groupConfigChangeTaskContext, endpoint, taskResult);
            Future<GroupConfigChangeTaskResult> future = context.getGroupConfigChangeTaskExecutor().submit(currentGroupConfigChangeTask);
            currentGroupConfigChangeTaskReference = new FutureGroupConfigChangeTaskReference(future);
            return currentGroupConfigChangeTaskReference;
        }
    }

    @Nonnull
    @Override
    public GroupConfigChangeTaskReference removeNode(@Nonnull NodeId id) {
        Objects.requireNonNull(id);
        ensureLeader();

        GroupConfigChangeTaskResult result = awaitPreviousGroupConfigChangeTask();
        if (result != null) {
            return new FixedResultGroupConfigTaskReference(result);
        }

        synchronized (this) {
            if (currentGroupConfigChangeTask != GroupConfigChangeTask.NONE) {
                throw new IllegalStateException("group config change concurrently");
            }

            // 和添加节点时一样，先追加一条 RemoveNodeEntry 的日志，然后将集群配置应用到本地，接着向 follower 开始复制日志，并将该 task 阻塞，
            // 直到该移除节点的日志被 commit 之后，该任务才从阻塞中返回
            currentGroupConfigChangeTask = new RemoveNodeTask(groupConfigChangeTaskContext, id, context.getSelfId());
            Future<GroupConfigChangeTaskResult> future = context.getGroupConfigChangeTaskExecutor().submit(currentGroupConfigChangeTask);
            currentGroupConfigChangeTaskReference = new FutureGroupConfigChangeTaskReference(future);
            return currentGroupConfigChangeTaskReference;
        }
    }

    @Nullable
    private GroupConfigChangeTaskResult awaitPreviousGroupConfigChangeTask() {
        try {
            currentGroupConfigChangeTaskReference.awaitDone(context.getConfig().getPreviousGroupConfigChangeTimeout());
            return null;
        } catch (InterruptedException ignored) {
            return GroupConfigChangeTaskResult.ERROR;
        } catch (TimeoutException ignored) {
            log.info("previous cannot complete within timeout");
            return GroupConfigChangeTaskResult.TIMEOUT;
        }
    }

    /**
     * 判断是否是 leader
     */
    private void ensureLeader() {
        RoleNameAndLeaderId result = role.getNameAndLeaderId(context.getSelfId());
        if (result.getRoleName() == RoleName.LEADER) {
            return;
        }
        NodeEndpoint endpoint = result.getLeaderId() != null ? context.getGroup().findMember(result.getLeaderId()).getEndpoint() : null;
        throw new NotLeaderException(result.getRoleName(), endpoint);
    }

    private ElectionTimeout scheduleElectionTimeout() {
        return context.getScheduler().scheduleElectionTimeout(this::electionTimeout);
    }


    /**
     * 选举超时,执行超时逻辑
     * 需要从定时线程切换到主处理线程执行具体逻辑
     */
    public void electionTimeout() {
        context.getTaskExecutor().submit(this::doProcessElectionTimeout);
    }

    /**
     * 选举超时
     * 如果是 Follower ：变为 Candidate 发起投票
     * 如果是 Candidate ：重新发起投票
     */
    private void doProcessElectionTimeout() {
        // leader 节点不会有选举超时
        if (RoleName.LEADER.equals(role.getRoleName())) {
            log.warn("node {}, current role is leader, ignore election timeout", context.getSelfId());
            return;
        }

        // 任期加1
        int newTerm = role.getTerm() + 1;
        // 取消原定时任务
        role.cancelTimeOutOrTask();

        if (context.getGroup().isStandalone()) {
            // become leader
            log.info("become leader in standalone model, term {}", newTerm);
            resetReplicatingStates();
            changeToRole(new LeaderNodeRole(newTerm, scheduleLogReplicationTask()));
            context.getLog().appendEntry(newTerm); // no-op log
        } else {
            // 转变角色为 Candidate
            changeToRole(new CandidateNodeRole(newTerm, scheduleElectionTimeout()));

            // 向所有节点发送选举投票
            RequestVoteRpc requestVoteRpc = new RequestVoteRpc();
            requestVoteRpc.setTerm(newTerm);
            requestVoteRpc.setCandidateId(context.getSelfId());
            // 日志相关处理
            EntryMeta lastEntryMeta = context.getLog().getLastEntryMeta();
            requestVoteRpc.setLastLogIndex(lastEntryMeta.getIndex());
            requestVoteRpc.setLastLogTerm(lastEntryMeta.getTerm());
            // 发起投票
            context.getConnector().sendRequestVote(requestVoteRpc, context.getGroup().listEndpointOfMajorExceptSelf());
        }
    }


    /**
     * 收到投票请求
     *
     * @param rpcMessage rpcMessage
     */
    @Subscribe
    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
        log.debug("======= 收到投票请求 ========  msg:{}", rpcMessage.getRpc().toString());
        // 切换线程到主处理线程
        context.getTaskExecutor().submit(() -> {
            RequestVoteResult requestVoteResult = doProcessRequestVoteRpc(rpcMessage);
            if (requestVoteResult != null) {
                context.getConnector().replyRequestVote(requestVoteResult,
                        context.getGroup().findMember(rpcMessage.getSourceNodeId()).getEndpoint());
            }
        });
    }

    /**
     * 收到投票请求结果
     *
     * @param voteResult voteResult
     */
    @Subscribe
    public void onReceiveRequestVoteResult(RequestVoteResult voteResult) {
        // 切换线程到主处理线程
        context.getTaskExecutor().submit(() -> doProcessRequestVoteResult(voteResult));
    }

    /**
     * 收到日志复制请求
     *
     * @param appendEntriesRpcMessage appendEntriesRpcMessage
     */
    @Subscribe
    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage appendEntriesRpcMessage) {
        context.getTaskExecutor().submit(() -> context.getConnector().replyAppendEntries(
                doProcessAppendEntriesRpc(appendEntriesRpcMessage),
                context.getGroup().findMember(appendEntriesRpcMessage.getSourceNodeId()).getEndpoint()));
    }

    /**
     * 收到日志复制结果请求
     *
     * @param message AppendEntriesResultMessage
     */
    @Subscribe
    public void onReceiveAppendEntriesResult(AppendEntriesResultMessage message) {
        context.getTaskExecutor().submit(() -> doProcessAppendEntriesResult(message));
    }

    /**
     * 收到开始生成快照的事件，由状态机发出该事件（不管是 leader 还是 follower 都会收到该事件）
     */
    @Subscribe
    @Deprecated
    public void onGenerateSnapshot(SnapshotGenerateEvent event) {
        context.getTaskExecutor().submit(() -> {
            context.getLog().generateSnapshot(event.getLastIncludedIndex(), context.getGroup().listEndpointOfMajor());
        });
    }

    /**
     * 收到开始生成快照的事件，由状态机发出该事件（不管是 leader 还是 follower 都会收到该事件）
     */
    @Subscribe
    public void onGenerateSnapshot(SnapshotGeneratedEvent event) {
        context.getTaskExecutor().submit(() -> {
            context.getLog().snapshotGenerated(event.getLastIncludedIndex());
        });
    }

    /**
     * 收到追加快照请求（由 leader 发送过来）
     */
    @Subscribe
    public void onReceiveInstallSnapshotRpc(InstallSnapshotRpcMessage rpcMessage) {
        context.getTaskExecutor().submit(
                () -> context.getConnector().replyInstallSnapshot(doProcessInstallSnapshotRpc(rpcMessage), rpcMessage));
    }


    /**
     * Receive install snapshot result.
     *
     * @param resultMessage result message
     */
    @Subscribe
    public void onReceiveInstallSnapshotResult(InstallSnapshotResultMessage resultMessage) {
        context.getTaskExecutor().submit(
                () -> doProcessInstallSnapshotResult(resultMessage)
        );
    }

    private void doProcessInstallSnapshotResult(InstallSnapshotResultMessage resultMessage) {
        InstallSnapshotResult result = resultMessage.get();

        // step down if result's term is larger than current one
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, 0, true);
            return;
        }

        // check role
        if (role.getRoleName() != RoleName.LEADER) {
            log.warn("receive install snapshot result from node {} but current node is not leader, ignore", resultMessage.getSourceNodeId());
            return;
        }

        // 判断是否是 catch up 中的节点
        if (newNodeCatchUpTaskGroup.onReceiveInstallSnapshotResult(resultMessage, context.getLog().getNextIndex())) {
            return;
        }

        NodeId sourceNodeId = resultMessage.getSourceNodeId();
        GroupMember member = context.getGroup().getMember(sourceNodeId);
        if (member == null) {
            log.info("unexpected install snapshot result from node {}, node maybe removed", sourceNodeId);
            return;
        }

        InstallSnapshotRpc rpc = resultMessage.getRpc();
        if (rpc.isDone()) {
            // 快照复制完成，开始复制日志
            member.advanceReplicatingState(rpc.getLastIndex());
            doReplicateLog(member, context.getConfig().getMaxReplicationEntries());
        } else {

            // 继续传输快照
            InstallSnapshotRpc nextRpc = context.getLog().createInstallSnapshotRpc(role.getTerm(), context.getSelfId(),
                    rpc.getOffset() + rpc.getData().length, context.getConfig().getSnapshotDataLength());
            context.getConnector().sendInstallSnapshot(nextRpc, member.getEndpoint());
        }
    }

    private InstallSnapshotResult doProcessInstallSnapshotRpc(InstallSnapshotRpcMessage rpcMessage) {
        InstallSnapshotRpc rpc = rpcMessage.getRpc();

        // 对方任期小于自己
        if (rpc.getTerm() < role.getTerm()) {
            return new InstallSnapshotResult(role.getTerm(), rpcMessage.getRpc().getMessageId());
        }

        // 对方任期大于自己
        if (rpc.getTerm() > role.getTerm()) {
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), System.currentTimeMillis(), true);
        }

        InstallSnapshotState state = context.getLog().installSnapshot(rpc);

        // 快照安装完成，应用集群配置
        if (state.getStateName() == InstallSnapshotState.StateName.INSTALLED) {
            context.getGroup().updateNodes(state.getLastConfig());
        }
        return new InstallSnapshotResult(role.getTerm(), rpcMessage.getRpc().getMessageId());
    }

    /**
     * 具体处理日志追加结果响应
     *
     * @param message message
     */
    private void doProcessAppendEntriesResult(AppendEntriesResultMessage message) {
        AppendEntriesResult rpc = message.getRpc();

        // 变成 Follower 节点
        if (role.getTerm() < rpc.getTerm()) {
            becomeFollower(rpc.getTerm(), null, null, 0, true);
            return;
        }

        // 检查自己的角色
        if (role.getRoleName() != RoleName.LEADER) {
            log.warn("receive append entries result from node {} but current node is not leader, ignore", message.getSourceNodeId());
            return;
        }

        // 判断是否是 catch up 的节点返回的信息
        if (newNodeCatchUpTaskGroup.onReceiveAppendEntriesResult(message, context.getLog().getNextIndex())) {
            return;
        }

        // 日志相关处理
        NodeId sourceNodeId = message.getSourceNodeId();
        GroupMember member = context.getGroup().getMember(sourceNodeId);
        if (member == null) {
            log.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
            return;
        }
        if (rpc.isSuccess()) {
            // follower 追加日志成功，推进 leader 保存的 matchIndex 和 nextIndex
            if (member.advanceReplicatingState(message.getAppendEntriesRpc().getLastEntryIndex())) {
                // 推进 leader 的 commitIndex
                context.getLog().advanceCommitIndex(context.getGroup().getMatchIndexOfMajor(), role.getTerm());
                log.debug("advance leader commitIndex.  leader commit index: {}", context.getLog().getCommitIndex());
            }
        } else {
            // follower 追加日志失败，将 nextIndex - 1 然后等日志复制定时任务重新发送 AppendEntriesRPC
            if (!member.backOfNextIndex()) {
                log.warn("cannot back off next index more, node {}", sourceNodeId);
                member.stopReplicating();
                return;
            }
        }

        // replicate log to node immediately other than wait for next log replication
        //doReplicateLog(member, context.getConfig().getMaxReplicationEntries());
    }


    /**
     * 具体处理日志追加请求
     *
     * @param rpcMessage rpcMessage
     * @return
     */
    private AppendEntriesResult doProcessAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        AppendEntriesRpc rpc = rpcMessage.getRpc();

        // 请求的任期小于自己的,返回自己的任期
        if (role.getTerm() > rpc.getTerm()) {
            return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), false);
        }

        // 请求的任期大于自己的，变为 Follower，并追加日志
        if (role.getTerm() < rpc.getTerm()) {
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), System.currentTimeMillis(), true);
            return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
        }

        switch (role.getRoleName()) {
            case FOLLOWER:
                // 重置保存在本地的状态，重置选举超时计时器，并添加日志
                becomeFollower(role.getTerm(), ((FollowerNodeRole) role).getVotedFor(), rpc.getLeaderId(), System.currentTimeMillis(), true);
                return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), appendEntries(rpc));
            case CANDIDATE:
                // 说明已经选出了 leader 了，退化为 Follower，并添加日志
                becomeFollower(role.getTerm(), null, rpc.getLeaderId(), System.currentTimeMillis(), true);
                return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), appendEntries(rpc));
            case LEADER:
                log.warn("receive append entries rpc from another leader {}, ignore", rpc.getLeaderId());
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getRoleName() + "]");
        }

    }

    private boolean appendEntries(AppendEntriesRpc rpc) {
        // 追加 leader 日志
        AppendEntriesState state = context.getLog().appendEntriesFromLeader(rpc.getPrevLogIndex(), rpc.getPrevLogTerm(), rpc.getEntries());
        if (state.isSuccess()) {
            if (state.hasGroup()) {
                context.getGroup().updateNodes(state.getLatestGroup());
            }
            // 如果 leaderCommit > commitIndex， 设置本地 commitIndex 为 leaderCommit 和最新日志索引中 较小的一个。
            // If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
            context.getLog().advanceCommitIndex(Math.min(rpc.getLeaderCommit(), rpc.getLastEntryIndex()), rpc.getTerm());
            return true;
        }
        return false;
    }

    /**
     * 具体处理投票逻辑
     *
     * @param requestVoteRpcMessage rpcMessage
     * @return RequestVoteResult
     */
    private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage requestVoteRpcMessage) {
        RequestVoteRpc requestVoteRpc = requestVoteRpcMessage.getRpc();

        if (role.getRoleName() == RoleName.FOLLOWER &&
                (System.currentTimeMillis() - ((FollowerNodeRole) role).getLastHeartbeat() < context.getConfig().getMinElectionTimeout())) {
            return null;
        }

        // 候选人任期小于自己的任期返回 false
        // Reply false if term < currentTerm (§5.1)
        if (role.getTerm() > requestVoteRpc.getTerm()) {
            log.debug("term from rpc < current term, don't vote ({} < {})", requestVoteRpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }

        // 比较日志
        boolean logAfter = !context.getLog().isNewerThan(requestVoteRpc.getLastLogIndex(),
                requestVoteRpc.getLastLogTerm());

        // 任期数大于自己的任期，转变为 Follower。
        // 并且需要比较候选人的日志是否比自己的日志新，只有候选人的日志比自己的新的时候才投票
        // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
        if (role.getTerm() < requestVoteRpc.getTerm()) {
            // 变成 Follower 重置自己的任期，投票等信息
            becomeFollower(requestVoteRpc.getTerm(), logAfter ? requestVoteRpc.getCandidateId() : null, null, 0, true);
            return new RequestVoteResult(requestVoteRpc.getTerm(), logAfter);
        }

        // 任期一致
        switch (role.getRoleName()) {
            case LEADER: // 任期相同拒绝投票
            case CANDIDATE: // 已经投票给自己了，拒绝投票 (Candidate 只投票给自己)
                return new RequestVoteResult(role.getTerm(), false);
            case FOLLOWER:
                // 两种情况需要投票
                // 1、对方日志比自己新
                // 2、已经给对方投过票了
                FollowerNodeRole follower = (FollowerNodeRole) this.role;
                NodeId votedFor = follower.getVotedFor();

                if ((votedFor == null && logAfter) || Objects.equals(votedFor, requestVoteRpc.getCandidateId())) {

                    // 变成 Follower 重置自己的任期，投票等信息
                    becomeFollower(role.getTerm(), requestVoteRpc.getCandidateId(), null, 0, true);

                    return new RequestVoteResult(role.getTerm(), true);
                }

                return new RequestVoteResult(role.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + this.role.getRoleName() + "]");
        }

    }

    private void becomeFollower(int term, NodeId votedFor, NodeId leaderId, long lastHeartbeat, boolean scheduleElectionTimeout) {
        role.cancelTimeOutOrTask();
        if (leaderId != null && !leaderId.equals(role.getLeaderId(context.getSelfId()))) {
            log.info("current leader is {}, term {}", leaderId, term);
        }
        ElectionTimeout electionTimeout = scheduleElectionTimeout ? scheduleElectionTimeout() : ElectionTimeout.NONE;
        FollowerNodeRole followerNodeRole = new FollowerNodeRole(term, votedFor, leaderId, lastHeartbeat, electionTimeout);
        changeToRole(followerNodeRole);
    }


    /**
     * 具体处理投票结果逻辑
     *
     * @param voteResult voteResult
     */
    private void doProcessRequestVoteResult(RequestVoteResult voteResult) {
        // 任期大于自己则转变为 Follower
        if (role.getTerm() < voteResult.getTerm()) {
            becomeFollower(voteResult.getTerm(), null, null, 0, true);
            return;
        }

        // 如果自己不是 Candidate 了，说明改节点要们选举成功了，要们选举失败了，忽略请求
        if (!RoleName.CANDIDATE.equals(role.getRoleName())) {
            log.debug("receive request vote result and current role is not candidate, ignore");
            return;
        }

        // 对方没有给自己投票
        if (!voteResult.isVoteGranted()) {
            return;
        }

        CandidateNodeRole role = (CandidateNodeRole) this.role;
        // 当前获取选票数
        int currentVotesCount = role.getVotesCount() + 1;
        int count = context.getGroup().getCountOfMajor();
        log.debug("votes count {}, major node count {}", currentVotesCount, count);

        // 收到选举投票，取消选举超时任务
        this.role.cancelTimeOutOrTask();

        // 票数过半,成为 Leader
        if (currentVotesCount > count / 2) {
            log.info("become leader, term {}", role.getTerm());

            // 重置 nextIndex 和 matchIndex
            resetReplicatingStates();

            // 转变为 leader，启动日志复制定时任务
            changeToRole(new LeaderNodeRole(role.getTerm(), scheduleLogReplicationTask()));
            // no-op log
            context.getLog().appendEntry(role.getTerm());
            return;
        }

        // 票数未过半，记录任期数和新的票数。并重新开启选举超时任务
        changeToRole(new CandidateNodeRole(role.getTerm(), currentVotesCount, scheduleElectionTimeout()));
    }

    /**
     * 初始化 leader 的 nextIndex 和 matchIndex
     * nextIndex 初始值为 leader 最新一条日 志的索引 +1； matchIndex 初始值为 0
     */
    private void resetReplicatingStates() {
        context.getGroup().resetReplicatingStates(context.getLog().getNextIndex());
    }

    /**
     * 创建日志复制定时任务
     *
     * @return LogReplicationTask
     */
    private LogReplicationTask scheduleLogReplicationTask() {
        return context.getScheduler().scheduleLogReplicationTask(this::replicateLog);
    }

    public void replicateLog() {
        context.getTaskExecutor().submit((Runnable) this::doReplicateLog);
    }

    private void doReplicateLog() {
        if (context.getGroup().isStandalone()) {
            log.info("doReplicateLog in standalone model");
            context.getLog().advanceCommitIndex(context.getLog().getNextIndex() - 1, role.getTerm());
            return;
        }

        log.debug("replicate log");
        for (GroupMember member : context.getGroup().listReplicationTarget()) {
            if (member.shouldReplicate(context.getConfig().getLogReplicationReadTimeout())) {
                doReplicateLog(member, context.getConfig().getMaxReplicationEntries());
            }
        }
    }

    private void doReplicateLog(GroupMember member, int maxEntries) {
        member.replicateNow();
        try {
            AppendEntriesRpc appendEntriesRpc = context.getLog()
                    .createAppendEntriesRpc(role.getTerm(), context.getSelfId(), member.getNextIndex(), maxEntries);
            context.getConnector().sendAppendEntries(appendEntriesRpc, member.getEndpoint());
        } catch (EntryInSnapshotException e) {
            log.debug("log entry {} in snapshot, replicate with install snapshot RPC", member.getNextIndex());
            InstallSnapshotRpc rpc = context.getLog().createInstallSnapshotRpc(role.getTerm(), context.getSelfId(),
                    0, context.getConfig().getSnapshotDataLength());
            context.getConnector().sendInstallSnapshot(rpc, member.getEndpoint());
        }
    }

    @Override
    public synchronized void stop() throws InterruptedException {
        if (!this.started) {
            log.warn("node has been stopped");
            return;
        }

        context.getScheduler().stop();
        context.getConnector().close();
        context.getTaskExecutor().shutdown();

        this.started = false;
    }

    /**
     * 角色变更方法
     *
     * @param newRole 新角色
     */
    private void changeToRole(AbstractNodeRole newRole) {
        log.debug("node {}, role state changed ->{}", context.getSelfId(), newRole);

        // 同步新角色状态到 NodeStore
        NodeStore store = context.getStore();
        store.setTerm(newRole.getTerm());
        if (RoleName.FOLLOWER.equals(newRole.getRoleName())) {
            store.setVotedFor(((FollowerNodeRole) newRole).getVotedFor());
        }
        this.role = newRole;
    }

    // =================================


    private class NewNodeCatchUpTaskContextImpl implements NewNodeCatchUpTaskContext {

        @Override
        public void replicateLog(NodeEndpoint endpoint) {
            context.getTaskExecutor().submit(() -> doReplicateLog(endpoint, context.getLog().getNextIndex()));
        }

        @Override
        public void doReplicateLog(NodeEndpoint endpoint, int nextIndex) {
            try {
                AppendEntriesRpc appendEntriesRpc = context.getLog().createAppendEntriesRpc(role.getTerm(),
                        context.getSelfId(), nextIndex, context.getConfig().getMaxReplicationEntriesForNewNode());
                context.getConnector().sendAppendEntries(appendEntriesRpc, endpoint);
            } catch (EntryInSnapshotException e) {
                log.debug("log entry {} in snapshot, replicate with install snapshot RPC", nextIndex);

                InstallSnapshotRpc installSnapshotRpc = context.getLog().createInstallSnapshotRpc(role.getTerm(),
                        context.getSelfId(), 0, context.getConfig().getSnapshotDataLength());
                context.getConnector().sendInstallSnapshot(installSnapshotRpc, endpoint);
            }
        }

        @Override
        public void sendInstallSnapshot(NodeEndpoint endpoint, int offset) {
            InstallSnapshotRpc installSnapshotRpc = context.getLog().createInstallSnapshotRpc(role.getTerm(),
                    context.getSelfId(), offset, context.getConfig().getSnapshotDataLength());
            context.getConnector().sendInstallSnapshot(installSnapshotRpc, endpoint);
        }

        @Override
        public void done(NewNodeCatchUpTask task) {
            newNodeCatchUpTaskGroup.remove(task);
        }
    }

    private class GroupConfigChangeTaskContextImpl implements GroupConfigChangeTaskContext {

        @Override
        public void addNode(NodeEndpoint endpoint, int nextIndex, int matchIndex) {
            context.getTaskExecutor().submit(() -> {
                Set<NodeEndpoint> nodeEndpoints = context.getGroup().listEndpointOfMajor();
                AddNodeEntry entry = context.getLog().appendEntryForAddNode(role.getTerm(), nodeEndpoints, endpoint);
                assert !context.getSelfId().equals(endpoint.getId());
                context.getGroup().addNode(endpoint, nextIndex, matchIndex, true);
                currentGroupConfigChangeTask.setGroupConfigEntry(entry);
                NodeImpl.this.doReplicateLog();
            });
        }

        @Override
        public void downgradeSelf() {
            // 如果移除的节点是自己，当前节点需要退化为 Follower，但是不需要启动选举超时定时器
            becomeFollower(role.getTerm(), null, null, 0, false);
        }

        @Override
        public void removeNode(NodeId nodeId) {
            context.getTaskExecutor().submit(() -> {
                Set<NodeEndpoint> nodeEndpoints = context.getGroup().listEndpointOfMajor();
                RemoveNodeEntry entry = context.getLog().appendEntryForRemoveNode(role.getTerm(), nodeEndpoints, nodeId);
                context.getGroup().removeNode(nodeId);
                currentGroupConfigChangeTask.setGroupConfigEntry(entry);
                NodeImpl.this.doReplicateLog();
            });
        }


        @Override
        public void done() {
            synchronized (NodeImpl.this) {
                currentGroupConfigChangeTask = GroupConfigChangeTask.NONE;
                currentGroupConfigChangeTaskReference = new FixedResultGroupConfigTaskReference(GroupConfigChangeTaskResult.OK);
            }
        }

    }

}
