package jrm.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServerResourceLocatorTest {
	private static final String IMAGE_CODE = "org.graalvm.nativeimage.imagecode";

	@AfterEach
	void clearImageCode() {
		System.clearProperty(IMAGE_CODE);
	}

	@Test
	@DisplayName("resolve short-circuits jrt: in a native image")
	void resolveSkipsJrtInNativeImage() {
		System.setProperty(IMAGE_CODE, "runtime");
		assertThat(ServerResourceLocator.resolve("jrt:/jrm.merged.module/certs/localhost.pfx")).isNull();
	}

	@Test
	@DisplayName("resolve still accepts filesystem paths in a native image")
	void resolveKeepsFilesystemPathsInNativeImage() {
		System.setProperty(IMAGE_CODE, "runtime");
		assertThat(ServerResourceLocator.resolve(".")).isNotNull();
	}
}
