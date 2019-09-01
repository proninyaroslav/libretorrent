package org.proninyaroslav.libretorrent.core.system.filesystem;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;

public interface FileDescriptorWrapper extends Closeable
{
    FileDescriptor open(@NonNull String mode) throws FileNotFoundException;
}
