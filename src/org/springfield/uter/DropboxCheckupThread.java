/* 
* DropboxCheckupThread.java
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

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springfield.uter.fs.*;
import org.springfield.uter.homer.LazyHomer;

public class DropboxCheckupThread extends Thread {
	private static final Logger log = Logger.getLogger(DropboxCheckupThread.class);
	private static boolean running = false;
	private static ArrayList<String> singlenodes = new ArrayList<String>(Arrays.asList("provider","publisherbroadcaster","iprRestrictions","rightsTermsAndConditions","firstBroadcastChannel","identifier","originalIdentifier","originalIdentifier","filename","digitalItemURL","landingPageURL","originallanguage","LocalKeyword","clipTitle","genre", "topic", "originallanguage","summary",
			"summaryInEnglish","ThesaurusTerm","extendedDescription","SeriesSeasonNumber","episodeNumber",
			"recordType","information","contributor","relationIdentifier"));
	private static ArrayList<String> doublenodes = new ArrayList<String>(Arrays.asList("TechnicalInformation"));
	private static ArrayList<String> triplenodes = new ArrayList<String>(Arrays.asList("TitleSet","SpatioTemporalInformation"));
	
	public static String versionPattern = "";

	private static String xmlURI = "http://c6.noterik.com/domain/euscreen/user";
	//private static String xmlURI = "http://localhost/domain/euscreen/user";
	private static int INNER_SLEEP = 10*1000; //10 seconds initially 
	private static int LOOP_SLEEP = 6*60*60*1000; //3 hours initially 
	
	
	private static String[] providerList = new String[] {"eu_ctv","eu_ina","eu_lcva","eu_nina","eu_orf","eu_rtp","eu_sase","eu_tvr","eu_kb","eu_nava","eu_tvc","eu_dw","eu_rte","eu_vrt","eu_rtbf","eu_tvp","eu_henaa","eu_rai","eu_luce","eu_bbc","eu_rtvs"};
	//private static String[] providerList = new String[] {"eu_tvr","eu_kb","eu_nava","eu_tvc","eu_dw"};
	
	//private static String[] providerList = new String[] {"eu_ina"};
	
	public DropboxCheckupThread() {
		log.debug("STARTING UTERTHREAD");
		if (!running) {
			running = true;
			start();
		}
	}
	
	public void run() {
		try {
			while (running) {
				try {
					log.debug("running");
					Thread.sleep(5*1000);
					for(String provider : providerList) {
						
						euscreenConvert(provider);
						FtpIngester.checkProviderVideo(provider);
						FtpIngester.checkProviderAudio(provider);
						FtpIngester.checkProviderPicture(provider);
						FtpIngester.checkProviderDoc(provider);
						log.debug("Sleeping for " + INNER_SLEEP/1000 + " seconds");
						Thread.sleep(INNER_SLEEP);
						
					}
					INNER_SLEEP = 10*60*1000; // Sleep 10 minutes between providers after first loop
					log.debug("Sleeping for " + LOOP_SLEEP/(60*60*1000) + " hours");
					Thread.sleep(LOOP_SLEEP);
				} catch(Exception e) {
					log.debug("error loop 1: ");
					e.printStackTrace();
					running=false;
				}
			}
			
			log.debug("stopping");
		} catch(Exception e2) {
			log.debug("error loop 2");
		}
	}

	private void euscreenConvert(String provider) {
		log.debug("CONVERTING : "+provider);
		FsNode userNode = Fs.getNode("/domain/euscreenxl/user/"+provider);
		if(userNode==null) {
			log.debug("creating user " + provider);
			String xml = "<fsxml><properties></properties></fsxml>";
			String res = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/properties",xml,"text/xml");
		}

		String uri = xmlURI+"/"+provider+"/";
        try {
            URL site = new URL(uri);
        	BufferedReader in = new BufferedReader(new InputStreamReader(site.openStream()));
        	String line;
        	while ((line = in.readLine()) != null) {
        		log.debug("PROVIDER="+provider+" LINE="+line);
        		euscreenConvertItem(provider,line);
        	}
        	in.close();
        } catch(Exception e) {
        	log.debug("EuscreenXL convert");
        	e.printStackTrace();
        }
	}
	
	private void euscreenConvertItem(String provider,String eus_id) {
		String uri = xmlURI+"/"+provider+"/"+eus_id+".xml";
    	String body = "";
        try {
            URL site = new URL(uri);
        	BufferedReader in = new BufferedReader(new InputStreamReader(site.openStream()));
        	String line;
        	while ((line = in.readLine()) != null) {
        		body+=line;
        	}
        	in.close();
        } catch(Exception e) {
        	log.debug("EuscreenXL body convert");
        	e.printStackTrace();
        }
        // log.debug(body);
        
        // lets parse what we have info fsnodes and commit them
        
        // our new video node ?
        FsNode fsnode = new FsNode();
        
		try {
			Document doc = DocumentHelper.parseText(body);
			if (doc!=null) {
				for(Iterator<Node> iter = doc.getRootElement().nodeIterator(); iter.hasNext(); ) {					
					Node node = iter.next();
					short ntype = node.getNodeType();
					if (ntype==node.ELEMENT_NODE) {
						Element e = (Element)node;
						
						// decode and set the nodes
						for(Iterator<Element> iter2 = e.elementIterator(); iter2.hasNext(); ) {
							Element e2 = iter2.next();
							String cname = e2.getName();
							if (singlenodes.contains(cname)) {
								decodeSingleNode(e2,fsnode);
								//fsnode.setProperty(cname, e2.getText());
							} else if (doublenodes.contains(cname)) {
								decodeDoubleNode(cname,e2,fsnode);
							} else if (triplenodes.contains(cname)) {
								decodeTripleNode(cname,e2,fsnode);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.debug("EuscreenXL parse xml error");
			e.printStackTrace();
		}
		
		
		
		String fsType = "video";
		String curType = fsnode.getProperty("TechnicalInformation_materialType");
		if(curType.equalsIgnoreCase("audio") || curType.equalsIgnoreCase("sound")) {
			fsType = "audio";
		} else if(curType.equalsIgnoreCase("text")) {
			fsType = "doc";
		} else if(curType.equalsIgnoreCase("still") || curType.equalsIgnoreCase("image")) {
			fsType = "picture";
		}
		
		String recordType = fsnode.getProperty("recordType");
		if(recordType!=null && recordType.equalsIgnoreCase("SERIES/COLLECTION")) {
			fsType = "series";
		}
		
		String filename = fsnode.getProperty("filename");
		if(filename==null && !fsType.equals("series")) {
			log.debug("Item has no filename!!! Skipping.");
			return;
		}
		
		//Set the decades
		String year = fsnode.getProperty("SpatioTemporalInformation_TemporalInformation_productionYear");
		if (year==null) {
			year = fsnode.getProperty("SpatioTemporalInformation_TemporalInformation_broadcastDate");
			if (year!=null) {
				year = year.substring(year.lastIndexOf("/")+1);
				fsnode.setProperty("SpatioTemporalInformation_TemporalInformation_productionYear", year);
			}
		}
		if (year!=null) {
			try {
				int iyear = Integer.parseInt(year);
				int decade = (int) Math.ceil(iyear/10)*10;
				fsnode.setProperty("decade", Integer.toString(decade) + "s");
			} catch(Exception e) {
				log.debug("YEAR NOT A VALID INT");
			}
		} else {
			fsnode.setProperty("decade", "1800s");
		}
		
		//Check for versions of this item
		String[] versions = checkVersions(provider, eus_id);
		
		if(versions!=null) {
			fsnode.setProperty("currentimportdate",versions[0]);
			if(versions[1] != null) {
				fsnode.setProperty("firstimportdate",versions[1]);
			}
		}

		// now lets save this node, for now we assume video nodes
    	String newbody ="<fsxml><properties>";	
    	// enum the properties
		for(Iterator<String> iter = fsnode.getKeys(); iter.hasNext(); ) {
			String key = iter.next();
			String value = fsnode.getProperty(key);
			if (value.contains("&") || value.contains("<")) {
				newbody+="<"+key+"><![CDATA["+value+"]]></"+key+">\n";
			} else {
				newbody+="<"+key+">"+value+"</"+key+">\n";
			}

			//log.debug("NAME="+key+" VALUE="+fsnode.getProperty(key));
		}
    	newbody+="</properties></fsxml>";
    	//log.debug("NEWBODY="+newbody);
    	
		String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/"+fsType+"/"+eus_id+"/properties",newbody,"text/xml");
		if(fsType.equals("series")) { //Check for old video nodes that should not be there.
			FsNode videonode = Fs.getNode("/domain/euscreenxl/user/"+provider+"/video/"+eus_id);
			if(videonode!=null) {
				LazyHomer.sendRequest("DELETE","/domain/euscreenxl/user/"+provider+"/video/"+eus_id, null, null);
			}
		}
		//log.debug("RESULTD="+result);
	}
	
	private String[] checkVersions(String provider, String eus_id) {
		log.debug("Checking versions for: " + provider + " " + eus_id);
		String basePath = "/usr/local/sites/noterik/domain/euscreen/user/";
		String path = basePath + provider;
		File currentVersion = new File(path + File.separator + eus_id + ".xml");
		// Check if running on server where the XMLs are stored  
		if(!currentVersion.exists()) return null;
		
		long currentDate = currentVersion.lastModified();
		
		Date cDate = new Date(currentDate);
		Format format = new SimpleDateFormat("dd MM yyyy HH:mm:ss");
	    String currentimportdate = format.format(cDate).toString();
	    
	    String[] response = new String[2]; 
	    response[0] = Long.toString(currentDate) + " (" + currentimportdate + ")";
	    
	    versionPattern = eus_id;
	    //log.debug("Checking versions in: " + path + File.separator + "backup");
	    File dir = new File(path + File.separator + "backup");
	    File[] files = dir.listFiles(new FilenameFilter() {
	        @Override
	        public boolean accept(File dir, String name) {
	            return name.matches(versionPattern + "_.*");
	        }
	    });
	    
	    versionPattern = "";
	    
	    if(files==null || files.length==0) {
	    	response[1] = response[0];
	    	return response;
	    }
	    log.debug("VERSIONS FOUND FOR: " + eus_id + "(" + files.length  + ")");
	    ArrayList<Long> versions = new ArrayList<Long>(files.length); 
	    for(int i=0; i<files.length;i++) {
	    	File f = files[i];
	    	String fname = f.getName();
	    	String[] id_v = fname.split("_");
	    	versions.add(new Long(id_v[2]));
	    }
	    
	    Long firstimport = Collections.min(versions);
	    Date fDate = new Date(firstimport);
	    String firstimportdate = format.format(fDate).toString();
	    response[1] = Long.toString(firstimport) + " (" + firstimportdate + ")";
	    
	    return response;
	}
	
	private void decodeSingleNode(Element e,FsNode fsnode) {
		String name = e.getName();
		String value = e.getText();
		String oldvalue = fsnode.getProperty(name);
		if (oldvalue!=null) {
			fsnode.setProperty(name,oldvalue+","+value);
		} else {
			fsnode.setProperty(name, value);	
		}
	}
	
	private void decodeDoubleNode(String prefix,Element e,FsNode fsnode) {
		for(Iterator<Element> iter = e.elementIterator(); iter.hasNext(); ) {
			Element e2 = iter.next();
			String name = e2.getName();
			String value = e2.getText();
			String oldvalue = fsnode.getProperty(prefix+"_"+name);
			if (oldvalue!=null) {
				fsnode.setProperty(prefix+"_"+name,oldvalue+","+value);
	
			} else {
				fsnode.setProperty(prefix+"_"+name,value);
			}
		}
	}
	
	private void decodeTripleNode(String prefix,Element e,FsNode fsnode) {
		for(Iterator<Element> iter = e.elementIterator(); iter.hasNext(); ) {
			Element e2 = iter.next();
			String prefix2 = e2.getName();
			for(Iterator<Element> iter2 = e2.elementIterator(); iter2.hasNext(); ) {
				Element e3 = iter2.next();
				String name = e3.getName();
				String value = e3.getText();
				String oldvalue = fsnode.getProperty(prefix+"_"+prefix2+"_"+name);
				if (oldvalue!=null) {
					fsnode.setProperty(prefix+"_"+prefix2+"_"+name,oldvalue+","+value);
				} else {
					fsnode.setProperty(prefix+"_"+prefix2+"_"+name,value);
				}
			}
		}
	}
	
	public void stopTask(){
        running = false;
    }

}
