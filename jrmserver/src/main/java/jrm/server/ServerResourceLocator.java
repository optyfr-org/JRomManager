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
			resource = resourceFactory.newResource(path);
			if (Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(classpathName(path), true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = classpathResource(resourceFactory, "/webclient/");
		if (resource != null && Resources.exists(resource))
			return resource;
		if (!inNativeImage()) {
			resource = resourceFactory.newResource("jrt:/jrm.merged.module/webclient/");
			if (Resources.exists(resource))
				return resource;
		}
		throw new FileNotFoundException("Unable to find webclient path");
	}

	static Resource locateCerts(String path) throws IOException {
		Resource resource;
		final var resourceFactory = ResourceFactory.root();
		if (path != null) {
			resource = resourceFactory.newResource(path);
			if (Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(classpathName(path), true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = classpathResource(resourceFactory, "/certs/localhost.pfx");
		if (resource != null && Resources.exists(resource))
			return resource;
		if (!inNativeImage()) {
			resource = resourceFactory.newResource("jrt:/jrm.merged.module/certs/localhost.pfx");
			if (Resources.exists(resource))
				return resource;
		}
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

	private static Resource classpathResource(ResourceFactory resourceFactory, String classResource) {
		final URL url = FullServer.class.getResource(classResource);
		if (url == null)
			return null;
		return resourceFactory.newClassLoaderResource(classpathName(classResource), true);
	}

	private static String classpathName(String resource) {
		return resource.startsWith("/") ? resource.substring(1) : resource;
	}

	private static boolean inNativeImage() {
		return System.getProperty(IMAGE_CODE) != null;
	}
}
