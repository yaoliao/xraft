package com.yl.raft.core.rpc.nio.handler;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Spliter
 */
public class Spliter extends LengthFieldBasedFrameDecoder {

    private static final int LENGTH_FIELD_OFFSET = 4;
    private static final int LENGTH_FIELD_LENGTH = 4;

    public Spliter() {
        super(Integer.MAX_VALUE, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH);
    }
}
