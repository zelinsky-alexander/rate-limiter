# Third-party notices

This project contains original project code and depends on third-party libraries through Maven. The dependencies are not copied into this repository as source code.

| Dependency | License | Purpose | Maintenance / concern |
|---|---|---|---|
| Lettuce 7.6.0.RELEASE (`io.lettuce:lettuce-core`) | Apache-2.0 | Redis Cluster client, asynchronous command execution, Lua `EVAL` | Maintained by Redis. It depends on Netty; keep both current because networking and protocol parsers are security-sensitive. |
| Netty 4.1.137.Final (`io.netty:*`) | Apache-2.0 | Non-blocking HTTP gateway transport; also used by Lettuce | Actively maintained. 4.1.137.Final is pinned because recent Netty releases contained security fixes; dependency updates should receive prompt review. |
| JUnit 6.1.3 (`org.junit.jupiter:junit-jupiter`) | EPL-2.0 | Unit tests only | Actively maintained. Test-only dependency. |

## Redis server used for local development

`compose.yaml` deliberately pins **Redis 7.2.4** for the local test cluster because that release is available under the BSD 3-Clause license. Newer Redis releases changed licensing. Redis 7.2.4 is therefore a development/debugging choice here, not a recommendation to deploy an old server release in production.

Before production deployment, select a currently supported Redis-compatible service/server whose license and security posture are acceptable for the deployment. Do not silently upgrade the local image to a source-available, SSPL, or AGPL Redis release without explicit project approval.

For redistributed binaries, containers, or commercial releases, review the complete resolved dependency tree and corresponding licenses rather than relying only on this summary.
