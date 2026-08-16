/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

/**
 * An exception used as a control-flow mechanism to break out of functional loops or lambda expressions where standard break
 * statements cannot be utilized.
 * <p>
 * This exception extends {@link RuntimeException} to avoid checked exception declarations.
 * </p>
 * 
 * @author optyfr
 */
public class BreakException extends RuntimeException {
    /**
     * Constructs a new {@code BreakException} with {@code null} as its detail message.
     */
    public BreakException() {
    }

    /**
     * Constructs a new {@code BreakException} with the specified detail message.
     * 
     * @param message the detail message of the exception
     */
    public BreakException(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@code BreakException} with the specified cause.
     * 
     * @param cause the cause of the exception
     */
    public BreakException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code BreakException} with the specified detail message and cause.
     * 
     * @param message the detail message of the exception
     * @param cause the cause of the exception
     */
    public BreakException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code BreakException} with the specified detail message, cause, suppression enabled or disabled, and
     * writable stack trace enabled or disabled.
     * 
     * @param message the detail message of the exception
     * @param cause the cause of the exception
     * @param enableSuppression whether or not suppression is enabled
     * @param writableStackTrace whether or not the stack trace should be writable
     */
    public BreakException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
