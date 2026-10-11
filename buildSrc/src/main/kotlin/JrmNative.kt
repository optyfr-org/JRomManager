/**
 * Shared GraalVM native-image build args (charsets, Fory build-time init).
 *
 * Single source of truth; jrmcli/jrmserver/jrmfx add their binary-specific
 * args on top via buildArgs.addAll(JrmNative.commonBuildArgs).
 */
object JrmNative {
    val commonBuildArgs = listOf(
        "--no-fallback",
        "-H:+ReportExceptionStackTraces",
        "--install-exit-handlers",
        "-H:+AddAllCharsets",
        "--initialize-at-build-time=jrm.security.ForyPersistence",
        "--initialize-at-build-time=org.apache.fory.pool.ThreadPoolFory\$PooledEntry"
    )
}
