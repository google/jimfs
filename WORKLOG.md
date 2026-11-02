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
