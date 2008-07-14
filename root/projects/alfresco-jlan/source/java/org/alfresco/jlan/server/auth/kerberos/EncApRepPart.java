/*
 * Copyright (C) 2006-2008 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */

package org.alfresco.jlan.server.auth.kerberos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Enumeration;

import org.alfresco.jlan.server.auth.asn.DERBuffer;
import org.alfresco.jlan.server.auth.asn.DERGeneralizedTime;
import org.alfresco.jlan.server.auth.asn.DERInteger;
import org.alfresco.jlan.server.auth.asn.DERObject;
import org.alfresco.jlan.server.auth.asn.DEROctetString;
import org.alfresco.jlan.server.auth.asn.DERSequence;
import org.alfresco.jlan.util.HexDump;

/**
 * Encrypted AP-REP Part Class
 * 
 * @author gkspencer
 */
public class EncApRepPart {

	// AP-REP fields
	//
	// Microseconds

	private int m_microseconds;

	// Timestamp

	private String m_timestamp;

	// Sub-key

	private int m_subKeyType;
	private byte[] m_subKey;

	// Sequence number

	private int m_seqNo;

	/**
	 * Default constructor
	 */
	public EncApRepPart() {
	}

	/**
	 * Class constructor
	 * 
	 * @param byte[] blob
	 * @exception IOException
	 */
	public EncApRepPart(byte[] blob) throws IOException {
		parseApRep(blob);
	}

	/**
	 * Return the timestamp
	 * 
	 * @return String
	 */
	public final String getTimestamp() {
		return m_timestamp;
	}

	/**
	 * Return the sub-key type
	 * 
	 * @return int
	 */
	public final int getSubKeyType() {
		return m_subKeyType;
	}

	/**
	 * Return the sub-key
	 * 
	 * @return byte[]
	 */
	public final byte[] getSubKey() {
		return m_subKey;
	}

	/**
	 * Return the sequence number
	 * 
	 * @return int
	 */
	public final int getSequenceNumber() {
		return m_seqNo;
	}

	/**
	 * Set the sub-key and type
	 * 
	 * @param int keyType
	 * @param byte[] subkey
	 */
	public final void setSubkey(int keyType, byte[] subkey) {
		m_subKeyType = keyType;
		m_subKey = subkey;
	}

	/**
	 * Parse the ASN/1 encoded AP-REP encrypted part
	 * 
	 * @param byte[] apRep
	 * @exception IOException
	 */
	public final void parseApRep(byte[] apRep)
		throws IOException {
		
		// Create a stream to parse the ASN.1 encoded blob

		DERBuffer derBuf = new DERBuffer(apRep);

		DERObject derObj = derBuf.unpackObject();
		if ( derObj instanceof DERSequence) {
			
			// Enumerate the AP-REP objects

			DERSequence derSeq = (DERSequence) derObj;

			for (int idx = 0; idx < derSeq.numberOfObjects(); idx++) {
				
				// Read an object

				derObj = (DERObject) derSeq.getObjectAt(idx);

				if ( derObj != null && derObj.isTagged()) {
					switch (derObj.getTagNo()) {
						
						// Timestamp

						case 0:
							if ( derObj  instanceof DERGeneralizedTime) {
								DERGeneralizedTime derTime = (DERGeneralizedTime) derObj;
								m_timestamp = derTime.getValue();
							}
							break;

						// Microseconds

						case 1:
							if ( derObj instanceof DERInteger) {
								DERInteger derInt = (DERInteger) derObj;
								m_microseconds = (int) derInt.getValue();
							}
							break;

						// Sub-key

						case 2:
							if ( derObj instanceof DERSequence) {
								DERSequence derEncSeq = (DERSequence) derObj;

								// Enumerate the sequence

								for (int i = 0; i < derEncSeq.numberOfObjects(); i++) {
									
									// Get the current sequence element

									derObj = (DERObject) derEncSeq.getObjectAt(i);

									if ( derObj != null && derObj.isTagged()) {
										switch (derObj.getTagNo()) {
											
											// Encryption key type

											case 0:
												if ( derObj instanceof DERInteger) {
													DERInteger derInt = (DERInteger) derObj;
													m_subKeyType = (int) derInt.getValue();
												}
												break;

											// Encryption key

											case 1:
												if ( derObj instanceof DEROctetString) {
													DEROctetString derOct = (DEROctetString) derObj;
													m_subKey = derOct.getValue();
												}
												break;
										}
									}
								}
							}
							break;

						// Sequence number

						case 3:
							if ( derObj instanceof DERInteger) {
								DERInteger derInt = (DERInteger) derObj;
								m_seqNo = (int) derInt.getValue();
							}
							break;
					}
				}
			}
		}
	}

	/**
	 * ASN.1 encode the encrypted AP-REP blob
	 * 
	 * @return byte[]
	 * @exception IOException
	 */
	public final byte[] encodeApRep()
		throws IOException {
		
		// Build the sequence of tagged objects

		DERSequence derList = new DERSequence();

		// Add the Kerberos time

		DERGeneralizedTime derTime = new DERGeneralizedTime(getTimestamp());
		derTime.setTagNo( 0);
		derList.addObject( derTime);

		// Add the microseconds

		DERInteger derInt = new DERInteger(m_microseconds);
		derInt.setTagNo( 1);
		derList.addObject( derInt);

		// Create the encryption key value

		DERSequence derSeq = new DERSequence( 2);

		DERObject derObj = new DERInteger( m_subKeyType);
		derObj.setTagNo( 0);
		derSeq.addObject( derObj);
		
		derObj = new DEROctetString( m_subKey);
		derObj.setTagNo( 1);
		derSeq.addObject( derObj);

		// Add the sub-key

		derSeq.setTagNo( 2);
		derList.addObject( derSeq);

		// Add the sequence number

		DERInteger derIntSeq = new DERInteger(m_seqNo);
		derIntSeq.setTagNo( 3);
		derList.addObject( derIntSeq);

		// Pack the objects

		DERBuffer derBuf = new DERBuffer(256);
		derBuf.packApplicationSpecific( 27, derList);

		// Return the packed encrypted part AP-REP blob

		return derBuf.getBytes();
	}

	/**
	 * Return the AP-REP as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuilder str = new StringBuilder();

		str.append("[AP-REP uSec=");
		str.append(m_microseconds);
		str.append(",Time=");
		str.append(getTimestamp());
		str.append(",SubKey=Type=");
		str.append(getSubKeyType());
		str.append(",Key=");
		str.append(getSubKey() != null ? HexDump.hexString(getSubKey()) : "null");
		str.append(",SeqNo=");
		str.append(getSequenceNumber());
		str.append("]");

		return str.toString();
	}
}
