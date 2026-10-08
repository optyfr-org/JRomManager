package jrm.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;

import jrm.fullserver.FullServer;

final class ServerResourceLocator {
	private static final String IMAGE_CODE = "org.graalvm.nativeimage.imagecode";

	static Resource locateClient(ResourceFactory resourceFactory, String path) throws IOException {
		Resource resource;
		if (path != null) {
			resource = safeNewResource(resourceFactory, path);
			if (resource != null && Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(classpathName(path), true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = classpathResource(resourceFactory, "/webclient/");
		if (resource != null && Resources.exists(resource))
			return resource;
		if (!inNativeImage()) {
			resource = safeNewResource(resourceFactory, "jrt:/jrm.merged.module/webclient/");
			if (resource != null && Resources.exists(resource))
				return resource;
		}
		throw new FileNotFoundException("Unable to find webclient path");
	}

	static Resource locateCerts(String path) throws IOException {
		Resource resource;
		final var resourceFactory = ResourceFactory.root();
		if (path != null) {
			resource = safeNewResource(resourceFactory, path);
			if (resource != null && Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(classpathName(path), true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = classpathResource(resourceFactory, "/certs/localhost.pfx");
		if (resource != null && Resources.exists(resource))
			return resource;
		if (!inNativeImage()) {
			resource = safeNewResource(resourceFactory, "jrt:/jrm.merged.module/certs/localhost.pfx");
			if (resource != null && Resources.exists(resource))
				return resource;
		}
		// Native-image last resort: copy the bundled cert stream to a temp file.
		// Class.getResourceAsStream works with GraalVM resource-config (certs/.*) even when
		// Jetty's ClassLoaderResource lookup (URL/URI based) cannot resolve the entry.
		resource = extractedStreamResource(resourceFactory, "/certs/localhost.pfx", "jrm-certs", ".pfx");
		if (resource != null && Resources.exists(resource))
			return resource;
		throw new FileNotFoundException("Unable to find localhost certificate");
	}

	static Path resolve(String path) {
		if (path.startsWith("jrt:") && inNativeImage())
			return null;
		try {
			return path.startsWith("jrt:") || path.startsWith("file:") || path.startsWith("jar:") ? Path.of(URI.create(path)) : Paths.get(path);
		} catch (FileSystemNotFoundException _) {
			final var uri = URI.create(path);
			try {
				FileSystems.newFileSystem(uri, Collections.emptyMap());
				return Path.of(uri);
			} catch (IOException _) {
				return null;
			}
		}
	}

	/**
	 * Creates a resource without throwing for unsupported schemes (e.g. {@code jrt:} in a native image where the jrt
	 * filesystem provider is absent). Returns {@code null} so callers fall through to the classpath fallback.
	 */
	private static Resource safeNewResource(ResourceFactory resourceFactory, String path) {
		try {
			return resourceFactory.newResource(path);
		} catch (RuntimeException _) {
			return null;
		}
	}

	private static Resource classpathResource(ResourceFactory resourceFactory, String classResource) {
		final URL url = FullServer.class.getResource(classResource);
		if (url == null)
			return null;
		return resourceFactory.newClassLoaderResource(classpathName(classResource), true);
	}

	/**
	 * Copies a classpath resource stream to a temp file and returns it as a {@link Resource}. Used as a last resort in
	 * native images where URL/URI-based classpath lookups fail but the entry is present in the image heap.
	 *
	 * @return the temp-file resource, or {@code null} when the stream is absent or cannot be extracted
	 */
	static Resource extractedStreamResource(ResourceFactory resourceFactory, String classResource, String prefix, String suffix) {
		try (final var in = FullServer.class.getResourceAsStream(classResource)) {
			if (in == null)
				return null;
			final var tmp = java.nio.file.Files.createTempFile(prefix, suffix);
			try (final var out = java.nio.file.Files.newOutputStream(tmp)) {
				in.transferTo(out);
			}
			tmp.toFile().deleteOnExit();
			final var resource = resourceFactory.newResource(tmp);
			if (Resources.exists(resource))
				return resource;
		} catch (IOException | RuntimeException _) {
			// extraction failed -> caller falls through to FileNotFoundException
		}
		return null;
	}

	private static String classpathName(String resource) {
		return resource.startsWith("/") ? resource.substring(1) : resource;
	}

	private static boolean inNativeImage() {
		return System.getProperty(IMAGE_CODE) != null;
	}
}
