package com.yl.raft.kvstore.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * GetCommand
 */
@Getter
@Setter
@ToString
public class GetCommand {

    private final String key;

    public GetCommand(String key) {
        this.key = key;
    }
}
