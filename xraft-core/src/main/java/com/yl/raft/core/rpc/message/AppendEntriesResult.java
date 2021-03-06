package com.yl.raft.core.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * AppendEntries RPC Result
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class AppendEntriesResult implements Serializable {

    private String messageId;

    /**
     * currentTerm, for leader to update itself
     */
    private int term;

    /**
     * rue if follower contained entry matching prevLogIndex and prevLogTerm
     */
    private boolean success;

}
