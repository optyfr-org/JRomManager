/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a device belonging to a {@link Machine}, providing details about its type, tags, matching interfaces, instance
 * characteristics, and supported media extensions.
 *
 * @author optyfr
 */
public final class Device implements Serializable {
    /**
     * The type designation of the device.
     *
     * @param type the type of the device
     * 
     * @return the type of the device
     */
    protected @Getter @Setter String type;

    /**
     * The unique tag name used to identify the device.
     *
     * @param tag the tag name used for the device
     * 
     * @return the tag name used for the device
     */
    protected @Getter @Setter String tag = null;

    /**
     * The hardware or logical interface name associated with the device.
     *
     * @param intrface the interface name associated with the device
     * 
     * @return the interface name associated with the device
     */
    protected @Getter @Setter String intrface = null;

    /**
     * Indicates if the device supports a fixed/permanent image or acts as a media switch (typically "1" or {@code null}).
     *
     * @param fixedImage the fixed image state string
     * 
     * @return the fixed image state string
     */
    protected @Getter @Setter String fixedImage = null;

    /**
     * Specifies whether this device is mandatory for the machine's operation (commonly "1" or {@code null}).
     *
     * @param mandatory the mandatory state string
     * 
     * @return the mandatory state string
     */
    protected @Getter @Setter String mandatory = null;

    /**
     * The {@link Instance} details representing the active implementation of this device.
     *
     * @param instance the Instance associated with this device
     * 
     * @return the Instance associated with this device
     */
    protected @Getter @Setter Instance instance = null;

    /**
     * The list of file extensions supported by this device.
     *
     * @param extensions the list of supported file extensions
     * 
     * @return the list of supported file extensions
     */
    private @Getter @Setter List<Extension> extensions = new ArrayList<>();

    /**
     * Constructs a new Device instance with default values. This constructor initializes the type, tag, intrface, fixedImage,
     * mandatory, and instance fields to null, and the extensions list to an empty ArrayList. This allows for the creation of a
     * Device object that can be populated with specific details as needed.
     */
    public Device() {
        /* default constructor */ }

    /**
     * Describes a specific instance of a {@link Device}, detailing its name and brief representation.
     */
    public class Instance implements Serializable {
        /**
         * The full name of the device instance.
         *
         * @param name the name of the instance
         * 
         * @return the name of the instance
         */
        protected @Getter @Setter String name;

        /**
         * The brief/shortened name of the device instance.
         *
         * @param briefname the brief name of the instance
         * 
         * @return the brief name of the instance
         */
        protected @Getter @Setter String briefname = null;

        /**
         * Constructs a new Instance object with default values. This constructor initializes the name and briefname fields to null,
         * allowing for the creation of an Instance object that can be populated with specific details as needed.
         */
        public Instance() {
            /* default constructor */ }
    }

    /**
     * Describes a supported file extension configuration for a {@link Device}.
     */
    public class Extension implements Serializable {
        /**
         * The file extension name (e.g., "bin", "rom").
         *
         * @param name the file extension name
         * 
         * @return the file extension name
         */
        protected @Getter @Setter String name;

        /**
         * Constructs a new Extension object with default values. This constructor initializes the name field to null, allowing for
         * the creation of an Extension object that can be populated with specific details as needed.
         */
        public Extension() {
            /* default constructor */ }
    }
}
