package com.yl.raft.kvstore.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * GetCommandResponse
 */
@Getter
@Setter
@ToString
public class GetCommandResponse {

    private final boolean found;
    private final byte[] value;

    public GetCommandResponse(byte[] value) {
        this(true, value);
    }

    public GetCommandResponse(boolean found, byte[] value) {
        this.found = found;
        this.value = value;
    }
}
