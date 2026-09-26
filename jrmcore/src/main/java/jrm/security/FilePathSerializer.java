package jrm.security;

import java.io.File;

import org.apache.fory.config.Config;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.serializer.Serializer;

final class FilePathSerializer extends Serializer<File> {
	FilePathSerializer(final Config config) {
		super(config, File.class, false);
	}

	@Override
	public void write(final WriteContext context, final File value) {
		final var buffer = context.getBuffer();
		buffer.writeByte(1);
		buffer.writeByte(0);
		buffer.writeByte(0x3e);
		buffer.writeByte(0x64);
		buffer.writeByte(0xff);
		context.writeString(value.getPath());
		context.writeChar(File.separatorChar);
	}

	@Override
	public File read(final ReadContext context) {
		context.getBuffer().increaseReaderIndex(5);
		final var path = context.readString();
		context.readChar();
		return new File(path);
	}
}
