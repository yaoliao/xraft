package com.yl.raft.kvstore.server;

import com.google.protobuf.ByteString;
import com.yl.raft.core.log.statemachine.AbstractSingleThreadStateMachine;
import com.yl.raft.core.node.Node;
import com.yl.raft.core.node.role.RoleName;
import com.yl.raft.core.node.role.RoleNameAndLeaderId;
import com.yl.raft.core.node.task.GroupConfigChangeTaskReference;
import com.yl.raft.kvstore.Protos;
import com.yl.raft.kvstore.message.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Service
 */
@Slf4j
public class Service {

    private final Node node;

    private final ConcurrentHashMap<String, CommandRequest<?>> pendingCommands = new ConcurrentHashMap<>();

    private Map<String, byte[]> map = new HashMap<>();

    public Service(Node node) {
        this.node = node;
        this.node.registerStateMachine(new StateMachineImpl());
    }

    public void set(CommandRequest<SetCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }
        SetCommand command = commandRequest.getCommand();
        log.debug("set {}", command.getKey());
        pendingCommands.put(command.getRequestId(), commandRequest);
        commandRequest.addCloseListener(() -> pendingCommands.remove(command.getRequestId()));

        // 追加日志
        this.node.appendLog(command.toBytes());
    }

    /**
     * 测试用的 get 读
     * 正确的情况是即使是读请求也需要经过 raft 日志复制，保证一致性（或者使用 ReadIndex 优化）
     */
    public void get(CommandRequest<GetCommand> commandRequest) {
        String key = commandRequest.getCommand().getKey();
        log.debug("get {}", key);
        byte[] bytes = map.get(key);
        commandRequest.reply(new GetCommandResponse(bytes));
    }

    /**
     * 添加节点
     */
    public void addNode(CommandRequest<AddNodeCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }

        AddNodeCommand command = commandRequest.getCommand();
        GroupConfigChangeTaskReference taskReference = this.node.addNode(command.toNodeEndpoint());
        awaitResult(taskReference, commandRequest);
    }

    /**
     * 移除节点
     */
    public void removeNode(CommandRequest<RemoveNodeCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            commandRequest.reply(redirect);
            return;
        }

        RemoveNodeCommand command = commandRequest.getCommand();
        GroupConfigChangeTaskReference taskReference = node.removeNode(command.getNodeId());
        awaitResult(taskReference, commandRequest);
    }

    private <T> void awaitResult(GroupConfigChangeTaskReference taskReference, CommandRequest<T> commandRequest) {
        try {
            switch (taskReference.getResult(3000L)) {
                case OK:
                    commandRequest.reply(Success.INSTANCE);
                    break;
                case TIMEOUT:
                    commandRequest.reply(new Failure(101, "timeout"));
                    break;
                default:
                    commandRequest.reply(new Failure(100, "error"));
            }
        } catch (TimeoutException e) {
            commandRequest.reply(new Failure(101, "timeout"));
        } catch (InterruptedException ignored) {
            commandRequest.reply(new Failure(100, "error"));
        }
    }

    /**
     * 判断是否为 leader
     */
    private Redirect checkLeadership() {
        RoleNameAndLeaderId roleNameAndLeaderId = node.getRoleNameAndLeaderId();
        if (!RoleName.LEADER.equals(roleNameAndLeaderId.getRoleName())) {
            return new Redirect(roleNameAndLeaderId.getLeaderId());
        }
        return null;
    }

    public static void toSnapshot(Map<String, byte[]> map, OutputStream outputStream) throws IOException {
        Protos.EntryList.Builder builder = Protos.EntryList.newBuilder();
        map.forEach((k, v) ->
                builder.addEntries(Protos.EntryList.Entry.newBuilder()
                        .setKey(k)
                        .setValue(ByteString.copyFrom(v)).build()));
        builder.build().writeTo(outputStream);
    }

    public static Map<String, byte[]> fromSnapshot(InputStream inputStream) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        Protos.EntryList entryList = Protos.EntryList.parseFrom(inputStream);
        for (Protos.EntryList.Entry entry : entryList.getEntriesList()) {
            map.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return map;
    }


    private class StateMachineImpl extends AbstractSingleThreadStateMachine {

        @Override
        protected void applyCommand(@Nonnull byte[] commandBytes) {
            SetCommand command = SetCommand.fromBytes(commandBytes);
            map.put(command.getKey(), command.getValue());
            CommandRequest<?> commandRequest = pendingCommands.remove(command.getRequestId());
            if (commandRequest != null) {
                commandRequest.reply(Success.INSTANCE);
            }
        }

        @Override
        protected void doApplySnapshot(@Nonnull InputStream input) throws IOException {
            map = fromSnapshot(input);
        }

        @Override
        public boolean shouldGenerateSnapshot(int firstLogIndex, int lastApplied) {
            return lastApplied - firstLogIndex > 1;
        }

        @Override
        public void generateSnapshot(@Nonnull OutputStream output) throws IOException {
            toSnapshot(map, output);
        }

    }
}
