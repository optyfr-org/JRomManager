package jrm.fx.ui.controls;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import jrm.locale.Messages;
import jrm.profile.manager.ProfileNFO;

/**
 * A cell factory for profile names with MAME status color coding.
 * <p>
 * Extends {@link NameCellFactory} to add color-coded text based on the profile's
 * MAME update status (up-to-date, needs update, not found) and shows a tooltip
 * describing the status.
 *
 * @since 2.5
 */
public class ProfileCellFactory extends NameCellFactory<ProfileNFO> {
    /**
     * Constructs a profile cell factory.
     */
    public ProfileCellFactory() {
        super(new StringConverter<>() {

            ProfileNFO profileNfo;

            @Override
            public String toString(ProfileNFO nfo) {
                this.profileNfo = nfo;
                if (nfo != null)
                    return nfo.getName();
                return null;
            }

            @Override
            public ProfileNFO fromString(String string) {
                if (profileNfo != null)
                    profileNfo.setNewName(string);
                return profileNfo;
            }

        });
    }

    /**
     * Updates the cell with color-coded text and tooltip based on MAME status.
     * <p>
     * Binds the cell style to the row selection state and shows a tooltip indicating
     * whether the profile is up-to-date, needs an update, or was not found in MAME.
     *
     * @param item  the profile to display, or {@code null}
     * @param empty whether this cell represents an empty row
     */
    @Override
    public void updateItem(ProfileNFO item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty) {
            styleProperty().bind(Bindings.when(getTableRow().selectedProperty()).then(new SimpleStringProperty(switch (item.getMame().getStatus()) {
                case UPTODATE -> "-fx-text-fill: #aaffaa;";
                case NEEDUPDATE -> "-fx-text-fill: #ffaa88;";
                case NOTFOUND -> "-fx-text-fill: #ffaaaa;";
                default -> "-fx-text-fill: #ffffff;";
            })).otherwise(new SimpleStringProperty(switch (item.getMame().getStatus()) {
                case UPTODATE -> "-fx-text-fill: #00aa00;";
                case NEEDUPDATE -> "-fx-text-fill: #cc8800;";
                case NOTFOUND -> "-fx-text-fill: #cc0000;";
                default -> "-fx-text-fill: #000000;";
            })));
            setTooltip(new Tooltip(switch (item.getMame().getStatus()) {
                case UPTODATE -> String.format(Messages.getString("FileTableCellRenderer.IsUpToDate"), item.getName());
                case NEEDUPDATE -> String.format(Messages.getString("FileTableCellRenderer.NeedUpdateFromMame"), item.getName());
                case NOTFOUND -> String.format(Messages.getString("FileTableCellRenderer.StatusUnknownMameNotFound"), item.getName());
                default -> getText();
            }));
        }
    }
}
