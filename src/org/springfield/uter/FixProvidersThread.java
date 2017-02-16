/* 
* FixProvidersThread.java
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
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springfield.uter.fs.*;
import org.springfield.uter.homer.LazyHomer;

import com.noterik.springfield.tools.HttpHelper;

public class FixProvidersThread extends Thread {
	private static boolean running = false;

	private static String xmlURI = "http://c6.noterik.com/domain/euscreen/";
	private static String XMLPath = "/usr/local/sites/noterik/domain/euscreen/";
	private static int INIT_SLEEP = 30*60*1000; //30 minutes initially 
	private static int LOOP_SLEEP = 12*60*60*1000; //12 hours for every successive run 
	private static Map<String, String> pcMap = new HashMap<String, String>();
	static {
		pcMap.put("CT", "eu_ctv");
		pcMap.put("INA", "eu_ina");
		pcMap.put("ORF", "eu_orf");
		pcMap.put("RTE", "eu_rte");
		pcMap.put("TVC", "eu_tvc");
		pcMap.put("TVR", "eu_tvr");
		pcMap.put("NISV", "eu_nisv");
		pcMap.put("SASE", "eu_sase");
		pcMap.put("RTP", "eu_rtp");
		pcMap.put("LCVA", "eu_lcva");
		pcMap.put("NINA", "eu_nina");
		pcMap.put("BUFVC", "eu_bufvc");
		pcMap.put("DW", "eu_dw");
		pcMap.put("KB", "eu_kb");
		pcMap.put("NAVA", "eu_nava");
		pcMap.put("LUCE", "eu_luce");
		pcMap.put("RTV SLO", "eu_rtvs");
		pcMap.put("HeNAA", "eu_henaa");
		pcMap.put("DR", "eu_dr");
		pcMap.put("RTBF", "eu_rtbf");
		pcMap.put("ERT", "eu_rtbf");
		pcMap.put("RAI", "eu_rai");
		pcMap.put("BBC", "eu_bbc");
		pcMap.put("TVP", "eu_tvp");
		pcMap.put("VRT", "eu_vrt");
	}
	
	public FixProvidersThread() {
		System.out.println("STARTING FixProviders THREAD");
		String realPath = XMLPath + "xml/";
		generateIndexFile(realPath);
		if (!running) {
			running = true;
			start();
		}
	}
	
	public void run() {
		try {
			//Thread.sleep(INIT_SLEEP);
			while (running) {
				ArrayList<String> providerList = new ArrayList<String>();
				try {
					String uri = xmlURI + "xml";
					URL site = new URL(uri);
		        	BufferedReader in = new BufferedReader(new InputStreamReader(site.openStream()));
		        	String line;
		        	while ((line = in.readLine()) != null) {
			    		String eus_id = line + ".xml";
			    		System.out.println("Checking: " + eus_id);

			    		URL xmlUrl = new URL(xmlURI + "xml/" + eus_id);
			        	BufferedReader inXML = new BufferedReader(new InputStreamReader(xmlUrl.openStream()));
			        	StringBuffer result = new StringBuffer();
			        	String xmlLine;
			        	while ((xmlLine = inXML.readLine()) != null) {
			        		result.append(xmlLine);
			        	}
			        	
			        	//String response = HttpHelper.sendRequest("GET",xmlURI + "xml/" + eus_id, null, null);
			    		String response = result.toString();
			    		//String response = HttpHelper.sendRequest("GET",xmlURI + "xml/" + eus_id, null, null);
			    		Document doc = DocumentHelper.parseText(response);
			    		if(doc != null) {
			    			Node provider = doc.selectSingleNode("//eus:provider");
			    			String providerName = provider.getText();
			    			providerName = providerName.replaceAll("[ÈÉÊË]","E");
			    			if(!providerList.contains(providerName)) {
			    				providerList.add(providerName);
			    				
			    			}
			    			System.out.println(providerName + " / " + eus_id);
			    			moveFile(providerName, eus_id);
			    			
			    		}
		        	}
		        } catch(Exception e) {
		        	System.out.println("Fix providers error");
		        	e.printStackTrace();
		        }
				
				String realPath = XMLPath + "xml/";
				generateIndexFile(realPath);
				stopTask();
			}
			
			System.out.println("Uter FixProviders: stopping");	
		} catch(Exception e2) {
			System.out.println("Uter FixProviders: error loop 2");	
		}
	}
	
	private void moveFile(String providerName, String filename) {
		
		String provider = null;
		if(pcMap.containsKey(providerName)) {
			provider = pcMap.get(providerName);
		}
		
		if(provider==null) return;
		
		File destDir = new File(XMLPath + "user/" + provider);
		if(!destDir.exists()) {
			destDir.mkdir();
		}
		
		File srcFile = new File(XMLPath + "xml/" + filename);
		File destFile = new File(XMLPath + "user/" + provider + "/" + filename);
		System.out.println("FixProviders: Moving file: " + srcFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());
		if(destFile.exists()) {
			srcFile.delete();
		} else {
			try {
				FileUtils.moveFile(srcFile, destFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		String realPath = XMLPath;
		realPath += "user/";
		realPath += provider + "/";
		
		generateIngestReport(provider, filename.replace(".xml", ""));
		generateIndexFile(realPath);
		
	}
	

	
	private void generateIngestReport(String user, String ID) {
		String realPath = XMLPath;
		if(!user.equals("xml")) {
			realPath += "user/";
		}
		realPath += user + "/";
		StringBuffer content = new StringBuffer();
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date date = new Date();
		content.append(dateFormat.format(date) + "\n");
		content.append(date.getTime() + "\n");
		content.append(ID);
		
		File reportFile = new File(realPath + "ingestreport.html");
		if(reportFile.exists()) {
			reportFile.delete();
		}
		try {
			reportFile.createNewFile();
	
			FileChannel reportFileC = new FileOutputStream(reportFile).getChannel();
			reportFileC.write(ByteBuffer.wrap(content.toString().getBytes()));
			reportFileC.close();
		
		} catch (Exception e) {
			System.out.println("error creating report file "+e.getMessage());
		}

	}
	
	private void generateIndexFile(String realPath) {
		File[] files = getFiles(realPath);
		StringBuffer content = new StringBuffer();
		for (int i = 0; i < files.length; i++) {
			String filename = files[i].getName();
			int pos = filename.lastIndexOf('.');
			filename = filename.substring(0, pos);
			content.append(filename + "\n");
		}
		
		File indexFile = new File(realPath + "index.html");
		if(indexFile.exists()) {
			indexFile.delete();
		}
		try {
			indexFile.createNewFile();
	
			FileChannel indexFileC = new FileOutputStream(indexFile).getChannel();
			indexFileC.write(ByteBuffer.wrap(content.toString().getBytes()));
			indexFileC.close();
		
		} catch (Exception e) {
			System.out.println("error creating index file "+e.getMessage());
		}

	}
	
	/* get all files from a directory */
	private File[] getFiles(String directory) {
		File d = new File(directory);
		File[] array = d.listFiles(new FilenameFilter() {
			
			public boolean accept(File dir, String name) {
				if(name.equals(".") || name.equals("..")) {
					return false;
				}
				if (!name.toUpperCase().endsWith("XML")) {
					return false;
				}
				return true;
			}
		});
		return array;
	}
	
	public void stopTask(){
        running = false;
    }

}
