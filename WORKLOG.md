# google/jimfs WORKLOG

## 2026-08-29 首次贡献
- **目标**：修复 issue #67 —— jimfs 的 FileChannel 锁实现违反 JDK 契约：同一 JVM 内对同一文件的重叠锁（同一 channel 或不同 channel）不会抛 `OverlappingFileLockException`，而真实文件系统会。
- **做了什么**：在 `RegularFile` 加锁注册表（`addLock`/`removeLock` + 重叠判断，镜像 JDK `FileLockTable.overlaps`）；`JimfsFileChannel.lock/tryLock` 创建锁时注册并检查重叠；`JimfsAsynchronousFileChannel.tryLock` 同样注册；`FakeFileLock.release()` 从注册表移除并置 invalid；`implCloseChannel` 释放该 channel 的全部锁。同步更新了两个现有测试（旧测试断言同一 channel 可无限重叠锁，正是被修的错误行为）。
- **解决了什么问题**：验证方式 = 单元测试 `JimfsFileChannelTest`/`JimfsAsynchronousFileChannelTest`（新增重叠/非重叠/跨 channel/关闭释放用例）+ `mvn test` 全量回归。
- 元数据：非 archived、非镜像、默认分支 master、2559 stars、hasIssuesEnabled。维护活跃（近期 merged #479/#482/#506 行为修复）。
- 排除的候选：#183(FileStore.getBlockSize，open PR #511 已覆盖)、#483(WatchService 时间戳，open PR #510 已覆盖)、#74(空 fs 有 work 目录，设计行为)、#165(维护者 netdpb 质疑要 repro)、#254(符号链接到不存在目标，维护者说需要大 plumbing)、#40(flaky 测试，难可靠修)、#362/#258/#161/#134/#119/#107/#106/#97/#73/#72/#69/#67 之外的 enhancement 均无 issue→PR 映射。
- 代码层另挖到候选：`WindowsPathType.parseUriPath` 对 uriPath 长度≤1（如 `Paths.get(URI.create("jimfs://host/"))`）会 `charAt(1)` 抛 StringIndexOutOfBoundsException——真实但边缘，未采纳为主切入，记档备用。
- 分支：`fix-overlapping-file-locks`；状态：implemented 待 record。
- 提交：42552d7（源码修复）、f0ed1fa（测试）。
- 验证：`mvn -pl jimfs test` 全量 5911 tests / 0 failures / 1 预存 skip；另用临时 scratch 测试经公开 `FileChannel.open` API 端到端确认（已删）。

## 2026-11-08 处理 PR #526 代码评审意见
- **背景**：PR https://github.com/google/jimfs/pull/526 收到 gemini-code-assist 评审，指出 3 个问题：(1) `implCloseChannel` 在持有 `myLocks` 监视器时逐个 `lock.close()`，与并发 `release()` 存在 `ConcurrentModificationException` / AB-BA 死锁风险；(2) 已释放的锁没有被移出 channel 的 `myLocks` 集合，造成内存泄漏；(3) `FakeFileLock` 因而不持有所属 `JimfsFileChannel` 的引用，无法在 `release()` 时自我注销。
- **做了什么**：`FakeFileLock` 增加 `JimfsFileChannel jimfsChannel` 字段（两个构造器同步更新）；`release()` 在 CAS 成功后同时从 `RegularFile` 与 channel 的 `myLocks` 注销；`JimfsFileChannel.removeLock(FakeFileLock)` 包级私有；`implCloseChannel` 先在 `synchronized (myLocks)` 内把锁复制成数组，再在监视器外逐个 `close()`，避免边迭代边修改，并保持与 `release()` 一致的加锁顺序（不再嵌套持有 `myLocks` → `RegularFile.locks`）。新增 `@VisibleForTesting int lockCount()` 与回归测试 `testLock_releasedLocksAreRemovedFromChannel`，覆盖 release 与 close 两条注销路径。
- **解决了什么问题**：消除评审指出的并发修改/死锁与内存泄漏；验证方式 = 目标测试 + `mvn -pl jimfs test` 全量 5913 tests / 0 failures / 1 预存 skip。
- 提交：见本次 commit（在 `b0542e1` 之上）。
