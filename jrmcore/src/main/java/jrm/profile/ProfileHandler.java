/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jrm.aui.progress.ProgressHandler;

/**
 * SAX Handler mapping parsed XML tags back into profile domain components.
 * Thin dispatcher that delegates all parsing state and element logic to {@link ProfileParseContext}.
 */
class ProfileHandler extends DefaultHandler {

	private final ProfileParseContext context;

	/**
	 * Instantiates a new parsing XML SAX handler.
	 * 
	 * @param profile the profile being populated
	 * @param handler the progress handler monitor
	 */
	public ProfileHandler(Profile profile, ProgressHandler handler) {
		this.context = new ProfileParseContext(profile, handler);
	}

	@Override
	public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
		try {
			context.startElement(qName, attributes);
		} catch (Exception e) {
			throw new ProfileHandlerException(context.getDebugMsg(attributes, qName, e), e);
		}
	}

	@Override
	public void endElement(final String uri, final String localName, final String qName) throws SAXException {
		context.endElement(qName);
	}

	@Override
	public void characters(final char[] ch, final int start, final int length) throws SAXException {
		context.characters(ch, start, length);
	}

	/**
	 * Exception thrown during parsing errors inside the SAX parser pipeline.
	 */
	private class ProfileHandlerException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ProfileHandlerException(String message, Exception e) {
			super(message, e);
		}
	}
}
