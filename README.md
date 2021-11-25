### Raft 实现

照着 [分布式一致性算法开发实战](https://book.douban.com/subject/35051108/) 这本书敲一遍

书中原来的实现 [github](https://github.com/xnnyygn/xraft)

[raft 论文翻译](https://github.com/maemual/raft-zh_cn)

### 记录一下

1. 为什么新 leader 选举出来之后需要创建一条空日志（Noop）？

> 这是因为 leader 只能 commit 当前任期的日志，不能提交之前任期的日志。
> 如果不提交空日志，就可能产生这样一种情况：客户端一直未发送日志过来， leader 就一直不产生当前任期的日志，导致之前任期产生的日志会一直无法被提交。
> 所以，当新 leader 选举成功之后，先在提交一个当前任期的空日志，以此来保证之前的日志都可以被 commit

2. 日志快照中的数据都是已经被状态机应用过的（applied）

> raft 论文: the last included index is the index of the last entry in the log that the snapshot replaces (the last en- try the state machine had applied)

3. raft集群成员变更有两种方法
    * 联合共识：将变更分为两阶段，先应用 C(old,new) 再应用 C(new)。实现复杂
    * 单节点变更：一次只能变更一个节点。实现简单，基本上都用这种方式

> 单节点变更规则 (书中 10.1.2 小结)：
>1. 新集群配置是一条日志
>2. 节点收到新的配置立即应用，不用等到对应的日志被提交
>3. 新集群配置只发送给新集群配置中的成员，不会发送给被移除的节点
>4. 收到来自不属于自己集群配置的 RequestVote 消息时，按照原有方法比较日志后决定是否投票
>5. 收到来自不属于自己集群配置的 AppendEntries 消息时，按照原有的方法执行

> [大佬对单节点变更的说明](https://blog.openacid.com/distributed/raft-bug/)   没看懂。。 😂

4. 集群成员变更时，被移除的成员因为不会再收到心跳信息，会选举超时而发起选举投票，这时候 leader 可能因为该节点的影响，而退化为 follower。使得集群短暂的不可用。 为了防止这种情况，可以加入 preVote 机制，并且
   follower 只有在最小选举间隔内不接受 requestVote 的消息。（书中 10.2.3 节）


5. raft 优化方式
    * 读请求优化使用 readIndex 的方式：当 leader 接收到一个读请求，记录当前的 commitIndex 为 readIndex，然后等 leader 的 applyIndex >= readIndex
      的时候，执行读操作，返回客户端
    * pipelining：将日志复制请求连续发送，而不是等待前一个请求返回之后再发起后一个请求
   
>  [tikv 对 raft 的优化](https://pingcap.com/zh/blog/optimizing-raft-in-tikv)