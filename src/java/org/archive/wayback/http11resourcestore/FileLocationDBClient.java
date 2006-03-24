/* FileLocationDBClient
 *
 * $Id$
 *
 * Created on 5:29:01 PM Mar 21, 2006.
 *
 * Copyright (C) 2006 Internet Archive.
 *
 * This file is part of wayback.
 *
 * wayback is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * wayback is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with wayback; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.wayback.http11resourcestore;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.util.ParameterFormatter;

/**
 * Class for client-side communication with FileLocationDBServlet.
 *
 * @author brad
 * @version $Date$, $Revision$
 */
public class FileLocationDBClient {
	private static final Logger LOGGER = Logger.getLogger(FileLocationDBClient
			.class.getName());

	private final static String ARC_SUFFIX = ".arc";
	private final static String ARC_GZ_SUFFIX = ".arc.gz";
	private final static String OK_RESPONSE_PREFIX = "OK ";
    private HttpClient client = null;
	
	private String serverUrl = null;

	/**
	 * @param serverUrl
	 */
	public FileLocationDBClient(final String serverUrl) {
		super();
		this.serverUrl = serverUrl;
		this.client = new HttpClient();
	}
	
	
	/**
	 * return an array of String URLs for all known locations of the ARC file
	 * in the DB.
	 * @param arcName
	 * @return String[] of URLs to arcName
	 * @throws IOException
	 */
	public String[] arcToUrls(final String arcName) throws IOException {
		String[] arcUrls = null;
		ParameterFormatter formatter = new ParameterFormatter();
		formatter.setAlwaysUseQuotes(false);
		String finalUrl = serverUrl;

		NameValuePair operationArg = new NameValuePair(
				FileLocationDBServlet.OPERATION_ARGUMENT,
        		FileLocationDBServlet.LOOKUP_OPERATION);

		NameValuePair nameArg = new NameValuePair(
				FileLocationDBServlet.NAME_ARGUMENT,
        		arcName);
		
		finalUrl += "?" + formatter.format(operationArg) + "&"
			+ formatter.format(nameArg);

		GetMethod method = new GetMethod(finalUrl); 
		
        int statusCode = client.executeMethod(method);
        if (statusCode != HttpStatus.SC_OK) {
            throw new IOException("Method failed: " + method.getStatusLine());
        }
        String responseString = method.getResponseBodyAsString();
        if(!responseString.startsWith(OK_RESPONSE_PREFIX)) {
        	if(responseString.startsWith(FileLocationDBServlet.NO_LOCATION_PREFIX)) {
        		return null;
        	}
        	throw new IOException(responseString);
        }
        String payLoad = responseString.substring(OK_RESPONSE_PREFIX.length()+1); 
		arcUrls = payLoad.split("\n");
		return arcUrls;
	}

	/**
	 * add an Url location for an arcName, unless it already exists
	 * @param arcName
	 * @param arcUrl
	 * @throws IOException
	 */
	public void addArcUrl(final String arcName, final String arcUrl) 
	throws IOException {
		doPostMethod(FileLocationDBServlet.ADD_OPERATION, arcName, arcUrl);
	}

	/**
	 * remove a single Url location for an arcName, if it exists
	 * @param arcName
	 * @param arcUrl
	 * @throws IOException
	 */
	public void removeArcUrl(final String arcName, final String arcUrl) 
	throws IOException {
		doPostMethod(FileLocationDBServlet.REMOVE_OPERATION, arcName, arcUrl);
	}
	
	private void doPostMethod(final String operation, final String arcName,
			final String arcUrl) 
	throws IOException {
	    PostMethod method = new PostMethod(serverUrl);
        NameValuePair[] data = {
                new NameValuePair(FileLocationDBServlet.OPERATION_ARGUMENT,
                		operation),
                new NameValuePair(FileLocationDBServlet.NAME_ARGUMENT,
                   		arcName),
                new NameValuePair(FileLocationDBServlet.URL_ARGUMENT,
                   		arcUrl)
              };
        method.setRequestBody(data);
        int statusCode = client.executeMethod(method);
        if (statusCode != HttpStatus.SC_OK) {
            throw new IOException("Method failed: " + method.getStatusLine());
        }
        String responseString = method.getResponseBodyAsString();
        if(!responseString.startsWith(OK_RESPONSE_PREFIX)) {
        	throw new IOException(responseString);
        }
	}

	private static void USAGE(String message) {
		System.err.print("USAGE: " + message + "\n" +
				"\t[lookup|add|remove|sync] ...\n" +
				"\n" +
				"\t lookup LOCATION-DB-URL ARC\n" +
				"\t\temit all known URLs for arc ARC\n" +
				"\n" +
				"\t add LOCATION-DB-URL ARC URL\n" +
				"\t\tinform locationDB that ARC is located at URL\n" +
				"\n" +
				"\t remove LOCATION-DB-URL ARC URL\n" +
				"\t\tremove reference to ARC at URL in locationDB\n" +
				"\n" +
				"\t sync LOCATION-DB-URL DIR DIR-URL\n" +
				"\t\tscan directory DIR, and submit all ARC files therein\n" +
				"\t\tto locationDB at url DIR-URL/ARC\n");
		System.exit(2);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length < 3) {
			USAGE("");
			System.exit(1);
		}
		String operation = args[0];
		String url = args[1];
		String arc = args[2];
		if(!url.startsWith("http://")) {
			USAGE("URL argument 1 must begin with http://");
		}

		FileLocationDBClient locationClient = new FileLocationDBClient(url); 
		if(operation.equalsIgnoreCase("lookup")) {
			if(args.length < 3) {
				USAGE("LOCATION-URL lookup ARC");
			}
			try {
				String[] locations = locationClient.arcToUrls(arc);
				if(locations == null) {
					System.err.println("No locations for " + arc);
					System.exit(1);
				}
				for(int i=0; i <locations.length; i++) {
					System.out.println(locations[i]);
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			
		} else if(operation.equalsIgnoreCase("add")) {
			if(args.length != 4) {
				USAGE("LOCATION-URL add ARC ARC-URL");
			}
			String arcUrl = args[3];
			if(!arcUrl.startsWith("http://")) {
				USAGE("ARC-URL argument 4 must begin with http://");
			}
			try {
				locationClient.addArcUrl(arc,arcUrl);
				System.out.println("OK");
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
		} else if(operation.equalsIgnoreCase("remove")) {
			
			if(args.length != 4) {
				USAGE("LOCATION-URL remove ARC ARC-URL");
			}
			String arcUrl = args[3];
			if(!arcUrl.startsWith("http://")) {
				USAGE("ARC-URL argument 4 must begin with http://");
			}
			try {
				locationClient.removeArcUrl(arc,arcUrl);
				System.out.println("OK");
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

		} else if(operation.equalsIgnoreCase("sync")) {
			
			if(args.length != 4) {
				USAGE("Usage: LOCATION-URL sync DIR DIR-URL");
			}
			File dir = new File(arc);
			String dirUrl = args[3];
			if(!dirUrl.startsWith("http://")) {
				USAGE("DIR-URL argument 4 must begin with http://");
			}
			try {
				if(!dir.isDirectory()) {
					USAGE("DIR " + arc + " is not a directory");
				}
				
				FileFilter filter = new FileFilter() {
					public boolean accept(File daFile) {
						return daFile.isFile() && 
							(daFile.getName().endsWith(ARC_SUFFIX) ||
								daFile.getName().endsWith(ARC_GZ_SUFFIX));
					}
				};
				
				File[] arcs = dir.listFiles(filter);
				if(arcs == null) {
					throw new IOException("Directory " + dir.getAbsolutePath() +
							" is not a directory or had an IO error");
				}
				for(int i = 0; i < arcs.length; i++) {
					File arcFile = arcs[i];
					String arcName = arcFile.getName();
					String arcUrl = dirUrl + arcName;
					LOGGER.info("Adding location " + arcUrl + " for arc " + arcName);
					locationClient.addArcUrl(arcName,arcUrl);
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
		} else {
			USAGE(" unknown operation " + operation);
		}
	}
}
