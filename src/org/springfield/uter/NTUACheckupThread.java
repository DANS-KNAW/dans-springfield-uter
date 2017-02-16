/* 
* NTUACheckupThread.java
* 
* Copyright (c) 2015 Noterik B.V.
* 
* This file is part of Uter, related to the Noterik Springfield project.
*
* Uter is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Uter is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Uter.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.springfield.uter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springfield.uter.fs.*;
import org.springfield.uter.homer.LazyHomer;

import com.noterik.springfield.tools.HttpHelper;

public class NTUACheckupThread extends Thread {
	private static boolean running = false;

	private static String mintURI = "http://mint-projects.image.ntua.gr/euscreenxl/PortalService";
	private static String maggieURI = "http://bart6b.noterik.com:8080/maggie/data";
	private static int INIT_SLEEP = 30*60*1000; //30 minutes initially 
	private static int LOOP_SLEEP = 12*60*60*1000; //12 hours for every successive run 
	
	public NTUACheckupThread() {
		System.out.println("STARTING NTUA CHECKUP THREAD");
		if (!running) {
			running = true;
			start();
		}
	}
	
	public void run() {
		try {
			//Thread.sleep(INIT_SLEEP);
			while (running) {
				try {
					System.out.println("Uter NTUAcheck: running");
					
					ArrayList<String> ntuaList = new ArrayList<String>();
					ArrayList<String> ntkList = new ArrayList<String>();
					
					String uri = mintURI;
			        try {
			            URL site = new URL(uri);
			        	BufferedReader in = new BufferedReader(new InputStreamReader(site.openStream()));
			        	String line;
			        	

			        	while ((line = in.readLine()) != null) {
			        		ntuaList.add(line);
			        	}
			        	in.close();
			        } catch(Exception e) {
			        	System.out.println("NTUAcheck error");
			        	e.printStackTrace();
			        }
			        
			        uri = maggieURI;
			        String body = "id=";
					String contentType = "application/x-www-form-urlencoded";
			        String response = HttpHelper.sendRequest("POST", uri, body, contentType);
			        Document doc = null;
					
					if(response!=null) {
						try {
							doc = DocumentHelper.parseText(response);
						} catch(DocumentException e) {
							System.out.println("Springfield check response exception: " + e);
						}
						
						List<Node> nodes = doc.selectNodes("//item");
						
						Iterator<Node> iterator = nodes.iterator();
						Element e;
						while (iterator.hasNext()) {
							Node item = iterator.next();
							if(item.selectSingleNode("id")!=null) {
								ntkList.add(item.selectSingleNode("id").getText());
							}
						}
					}
					
			        for (String id : ntuaList) {
			        	if(!ntkList.contains(id)) {
			        		if(id.equals("EUS_00000000000000000000000000000000")) continue;
			        		System.out.println("Missing id: " + id);
			        	}
			        }
			        
					Thread.sleep(LOOP_SLEEP);
				} catch(Exception e) {
					System.out.println("Uter NTUAcheck: error loop 1: ");
					e.printStackTrace();
				}
			}
			
			System.out.println("Uter NTUAcheck: stopping");	
		} catch(Exception e2) {
			System.out.println("Uter NTUAcheck: error loop 2");	
		}
	}
	
	public void stopTask(){
        running = false;
    }

}
