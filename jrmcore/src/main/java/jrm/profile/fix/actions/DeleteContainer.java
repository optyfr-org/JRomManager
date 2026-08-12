/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix.actions;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import lombok.val;

/**
 * Delete a container (will all its entries)
 * 
 * @author optyfr
 */
public class DeleteContainer extends ContainerAction {

    /**
     * constructor
     * 
     * @param container to delete
     * @param format format of the container
     */
    public DeleteContainer(final Container container, final FormatOptions format) {
        super(container, format);
    }

    /**
     * shortcut static method to get an instance of {@link DeleteContainer}
     * 
     * @param action the potentially already existing {@link DeleteContainer}
     * @param container the container to backup
     * @param format the format of the container
     * 
     * @return a {@link DeleteContainer}
     */
    public static DeleteContainer getInstance(DeleteContainer action, final Container container, final FormatOptions format) {
        if (action == null)
            action = new DeleteContainer(container, format);
        return action;
    }

    @Override
    public boolean doAction(final Session session, final ProgressHandler handler) {
        handler.setProgress(toDocument(toNoBR(String.format(escape(session.getMsgs().getString("DeleteContainer.Deleting")), toBlue(escape(container.getFile().getName())))))); //$NON-NLS-1$
        if (!PathAbstractor.isWriteable(session, container.getFile().toPath())) {
            Log.err(() -> String.format("write access denied for delete %s", container.getFile()));
            return false;
        }
        if (container.getType() == Container.Type.ZIP || container.getType() == Container.Type.SEVENZIP || container.getType() == Container.Type.UNK) {
            try {
                return Files.deleteIfExists(container.getFile().toPath());
            } catch (IOException _) {
                Log.err(() -> String.format("failed to delete %s", container.getFile()));
                return false;
            }
        } else if (container.getType() == Container.Type.DIR) {
            return doActionDir();
        } else if (container.getType() == Container.Type.FAKE) {
            return doActionFake();
        }
        return false;
    }

    /**
     * 
     */
    private boolean doActionFake() {
        for (val entry : container.getEntries()) {
            try {
                if (!Files.deleteIfExists(container.getFile().getParentFile().toPath().resolve(entry.getFile())))
                    return false;
            } catch (final IOException _) {
                Log.err("failed to delete " + container.getRelFile()); //$NON-NLS-1$
                return false;
            }
        }
        return true;
    }

    /**
     * @return
     */
    private boolean doActionDir() {
        try {
            FileUtils.deleteDirectory(container.getFile());
            return true;
        } catch (final IOException e) {
            Log.err("failed to delete " + container.getRelFile() + " (" + e.getMessage() + ")"); //$NON-NLS-1$
            if (container.getFile().exists())
                return FileUtils.deleteQuietly(container.getFile());
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format(Messages.getString("DeleteContainer.Delete"), container); //$NON-NLS-1$
    }
}
