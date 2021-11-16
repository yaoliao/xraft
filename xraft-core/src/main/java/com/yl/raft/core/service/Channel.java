package com.yl.raft.core.service;

public interface Channel {

    Object send(Object payload);

}
