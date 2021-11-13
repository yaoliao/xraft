package com.yl.raft.kvstore.server;

import com.yl.raft.core.log.statemachine.AbstractSingleThreadStateMachine;
import com.yl.raft.core.node.Node;
import com.yl.raft.core.node.role.RoleName;
import com.yl.raft.core.node.role.RoleNameAndLeaderId;
import com.yl.raft.kvstore.message.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service
 */
@Slf4j
public class Service {

    private final Node node;

    private final ConcurrentHashMap<String, CommandRequest<?>> pendingCommands = new ConcurrentHashMap<>();

    private final Map<String, byte[]> map = new HashMap<>();

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
     * 判断是否为 leader
     */
    private Redirect checkLeadership() {
        RoleNameAndLeaderId roleNameAndLeaderId = node.getRoleNameAndLeaderId();
        if (!RoleName.LEADER.equals(roleNameAndLeaderId.getRoleName())) {
            return new Redirect(roleNameAndLeaderId.getLeaderId());
        }
        return null;
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

    }
}
