package jrm.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;

import jrm.fullserver.FullServer;

final class ServerResourceLocator {
	static Resource locateClient(ResourceFactory resourceFactory, String path) throws IOException, URISyntaxException {
		Resource resource;
		if (path != null) {
			resource = resourceFactory.newResource(path);
			if (Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(path, true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = resourceFactory.newResource("jrt:/jrm.merged.module/webclient/");
		if (Resources.exists(resource))
			return resource;
		URL url = FullServer.class.getResource("/webclient/");
		if (url != null) {
			resource = resourceFactory.newResource(URIUtil.correctURI(url.toURI()));
			if (Resources.exists(resource))
				return resource;
		}
		throw new FileNotFoundException("Unable to find webclient path");
	}

	static Resource locateCerts(String path) throws URISyntaxException, IOException {
		Resource resource;
		final var resourceFactory = ResourceFactory.root();
		if (path != null) {
			resource = resourceFactory.newResource(path);
			if (Resources.exists(resource))
				return resource;
			resource = resourceFactory.newClassLoaderResource(path, true);
			if (Resources.exists(resource))
				return resource;
		}
		resource = resourceFactory.newResource("jrt:/jrm.merged.module/certs/localhost.pfx");
		if (Resources.exists(resource))
			return resource;
		URL url = FullServer.class.getResource("/certs/localhost.pfx");
		if (url != null) {
			resource = resourceFactory.newResource(URIUtil.correctURI(url.toURI()));
			if (Resources.exists(resource))
				return resource;
		}
		throw new FileNotFoundException("Unable to find localhost certificate");
	}

	static Path resolve(String path) {
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
}
