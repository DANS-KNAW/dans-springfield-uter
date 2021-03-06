/* 
* LazyHomer.java
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

package org.springfield.uter.homer;

import com.noterik.springfield.tools.HttpHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;


public class LazyHomer implements MargeObserver {
	
	private static Logger log = Logger.getLogger(LazyHomer.class);

	/** Noterik package root */
	public static final String PACKAGE_ROOT = "org.springfield.uter";
	private static enum loglevels { all,info,warn,debug,trace,error,fatal,off; }
	public static String myip = "unknown";
	private static int port = -1;
	public static boolean local = true;
	static String group = "224.0.0.0";
	static int ttl = 1;
	static boolean noreply = true;
	static LazyMarge marge;
	static SmithersProperties selectedsmithers = null;
	private static String rootPath = null;
	//private static NelsonServer serv;
	private static Map<String, SmithersProperties> smithers = new HashMap<String, SmithersProperties>();
	private static Map<String, UterProperties> uters = new HashMap<String, UterProperties>();
	private static LazyHomer ins;
	private static boolean running = false;
	static String role = "production";
	private static Map<String, MountProperties> mounts = null;

	/**
	 * Initializes the configuration
	 */
	public void init(String r) {
		rootPath = r;
		ins = this;
		initLogger();
		initConfig();

		
		try{
			InetAddress mip=InetAddress.getLocalHost();
			myip = ""+mip.getHostAddress();
		}catch (Exception e){
			log.debug("Exception ="+e.getMessage());
		}
		log.info("Uter init service name = uter on ipnumber = "+myip);
		log.debug("init service name = uter on ipnumber = "+myip+" on marge port "+port);
		marge = new LazyMarge();
		
		// lets watch for changes in the service nodes in smithers
		marge.addObserver("/domain/internal/service/uter/nodes/"+myip, ins);
		marge.addTimedObserver("/smithers/downcheck",6,this);
		new DiscoveryThread();	
	}

	public static void addSmithers(String ipnumber,String port,String mport,String role) {
		int oldsize = smithers.size();
		if (!(""+LazyHomer.getPort()).equals(mport)) {
			log.debug("EXTREEM WARNING CLUSTER COLLISION ("+LazyHomer.getPort()+") "+ipnumber+":"+port+":"+mport);
			return;
		}
		
		if (!role.equals(getRole())) {
			log.debug("Ignored this smithers ("+ipnumber+") its "+role+" and not "+getRole()+" like us");
			return;
		}
		
		SmithersProperties sp = smithers.get(ipnumber);
		if (sp==null) {
			sp = new SmithersProperties();
			smithers.put(ipnumber, sp);
			sp.setIpNumber(ipnumber);
			sp.setPort(port);
			sp.setAlive(true); // since talking its alive 
			noreply = false; // stop asking (minimum of 60 sec, delayed)
			log.info("uter found smithers at = "+ipnumber+" port="+port+" multicast="+mport);
			log.debug("found smithers at = "+ipnumber+" port="+port+" multicast="+mport);
		} else {
			if (!sp.isAlive()) {
				sp.setAlive(true); // since talking its alive again !
				log.info("uter recovered smithers at = "+ipnumber);
			}
		}

	// so check if we are known 
	if (oldsize==0 && ins.checkKnown()) {
		
		// we are verified (has a name other than unknown) and status is on
		UterProperties mp = uters.get(myip);
		setLogLevel(mp.getDefaultLogLevel());
		if (mp!=null && mp.getStatus().equals("on")) {
			if (!running) {
				running = true;
				log.info("This uter will be started (on startup)");
			}
		} else {
			if (running) {
				running = false;
			} else {
				log.info("This lou is not turned on, use smithers todo this for ip "+myip);
			}
		}
	}
	if (oldsize>0) {
		// we already had one so lets see if we need to switch to
		// a better one.
		getDifferentSmithers();
	}
}




	
	public static UterProperties getMyUterProperties() {
		return uters.get(myip);
	}
	
	public static int getMyUterPosition() {
		int i = 0;
		for(Iterator<UterProperties> iter = uters.values().iterator(); iter.hasNext(); ) {
			UterProperties m = (UterProperties)iter.next();
			i++;
			if (m.getIpNumber().equals(myip)) return i;
		}
		return -1;
	}
	
	public static int getNumberOfEdnas() {
		return uters.size();
	}
	
	
	private Boolean checkKnown() {
		String xml = "<fsxml><properties><depth>1</depth></properties></fsxml>";
		String nodes = LazyHomer.sendRequest("GET","/domain/internal/service/uter/nodes",xml,"text/xml");
		//log.debug("NODES="+nodes);
		boolean iamok = false;

		try { 
			boolean foundmynode = false;
			
			Document result = DocumentHelper.parseText(nodes);
			for(Iterator<Node> iter = result.getRootElement().nodeIterator(); iter.hasNext(); ) {
				Element child = (Element)iter.next();
				if (!child.getName().equals("properties")) {
					String ipnumber = child.attributeValue("id");
					String status = child.selectSingleNode("properties/status").getText();
					String name = child.selectSingleNode("properties/name").getText();

					// lets put all in our uter list
					UterProperties mp = uters.get(ipnumber);
					if (mp==null) {
						mp = new UterProperties();
						uters.put(ipnumber, mp);

					}
					mp.setIpNumber(ipnumber);
					mp.setName(name);
					mp.setStatus(status);
					mp.setDefaultLogLevel(child.selectSingleNode("properties/defaultloglevel").getText());
					mp.setPreferedSmithers(child.selectSingleNode("properties/preferedsmithers").getText());

					if (ipnumber.equals(myip)) {
						foundmynode = true;
						if (name.equals("unknown")) {
							log.debug("This uter is not verified change its name, use smithers todo this for ip "+myip);
						} else {
							// so we have a name (verified) return true
							iamok = true;
						}
					}
				}	
			}
			if (!foundmynode) {
				log.info("LazyHomer : Creating my processing node "+LazyHomer.getSmithersUrl()  + "/domain/internal/service/uter/properties");
				String os = "unknown"; // we assume windows ?
				try{
					  os = System.getProperty("os.name");
				} catch (Exception e){
					log.debug("LazyHomer : "+e.getMessage());
				}
				
				String newbody = "<fsxml>";
	        	newbody+="<nodes id=\""+myip+"\"><properties>";
	        	newbody+="<name>unknown</name>";
	        	newbody+="<status>off</status>";
	        	newbody+="<activesmithers>"+selectedsmithers.getIpNumber()+"</activesmithers>";
	        	newbody+="<lastseen>"+new Date().getTime()+"</lastseen>";
	        	newbody+="<preferedsmithers>"+myip+"</preferedsmithers>";
	        	if (isWindows()) {
	        		newbody+="<defaultloglevel>info</defaultloglevel>";
	        		newbody+="<temporarydirectory>c:\\springfield\\uter\\temp</temporarydirectory>";
	        	} if (isMac()) {
	        		newbody+="<defaultloglevel>info</defaultloglevel>";
	        		newbody+="<temporarydirectory>/springfield/uter/temp</temporarydirectory>";
	        	} if (isUnix()) {
	        		newbody+="<defaultloglevel>info</defaultloglevel>";
	        		newbody+="<temporarydirectory>/springfield/uter/temp</temporarydirectory>";
	        	} else {
	        		newbody+="<defaultloglevel>info</defaultloglevel>";
	        		newbody+="<temporarydirectory>c:\\springfield\\uter\\temp</temporarydirectory>";

	        	}
	        	newbody+="</properties></nodes></fsxml>";	
				LazyHomer.sendRequest("PUT","/domain/internal/service/uter/properties",newbody,"text/xml");
			}
		} catch (Exception e) {
			log.info("LazyHomer exception doc");
			e.printStackTrace();
		}
		return iamok;
	}
	
	public static void setLastSeen() {
		Long value = new Date().getTime();
		log.debug("Sending lastseen value to Smithers: " + value);
		LazyHomer.sendRequest("PUT", "/domain/internal/service/uter/nodes/"+myip+"/properties/lastseen", ""+value, "text/xml");
	}
	

	
	public static void send(String method, String uri) {
		try {
			MulticastSocket s = new MulticastSocket();
			String msg = myip+" "+method+" "+uri;
			byte[] buf = msg.getBytes();
			//log.debug("UTER SEND="+msg);
			DatagramPacket pack = new DatagramPacket(buf, buf.length,InetAddress.getByName(group), port);
			s.send(pack,(byte)ttl);
			s.close();
		} catch(Exception e) {
			log.debug("LazyHomer error "+e.getMessage());
		}
	}
	
	public static Boolean up() {
		if (smithers==null) return false;
		return true;
	}
	
	
	public static String getSmithersUrl() {
		if (selectedsmithers==null) {
			for(Iterator<SmithersProperties> iter = smithers.values().iterator(); iter.hasNext(); ) {
				SmithersProperties s = (SmithersProperties)iter.next();
				if (s.isAlive()) {
					selectedsmithers = s;
				}
			}
		}

		return "http://"+selectedsmithers.getIpNumber()+":"+selectedsmithers.getPort()+"/smithers2";
	}
	
	public void remoteSignal(String from,String method,String url) {
		if (url.indexOf("/smithers/downcheck")!=-1) {
			for(Iterator<SmithersProperties> iter = smithers.values().iterator(); iter.hasNext(); ) {
				SmithersProperties sm = (SmithersProperties)iter.next();
				if (!sm.isAlive()) {
					log.info("One or more smithers down, try to recover it");
					LazyHomer.send("INFO","/domain/internal/service/getname");
				}
			}
		} else {
		// only one trigger is set for now so we know its for nodes :)
		if (ins.checkKnown()) {
			// we are verified (has a name other than unknown)		
			UterProperties mp = uters.get(myip);
			/*
			if (serv==null) serv = new EdnaServer();
			if (mp!=null && mp.getStatus().equals("on")) {

				if (!serv.isRunning()) { 
					log.info("This edna will be started");
					serv.init();
				}
				setLogLevel(mp.getDefaultLogLevel());
			} else {
				if (serv.isRunning()) {
					log.info("This edna will be turned off");
					serv.destroy();
				} else {
					log.info("This edna is not turned on, use smithers todo this for ip "+myip);
				}
			}
			*/
		}
		}
	}
	
	public static boolean isWindows() {
		String os = System.getProperty("os.name").toLowerCase();
		return (os.indexOf("win") >= 0);
	}
 
	public static boolean isMac() {
 		String os = System.getProperty("os.name").toLowerCase();
		return (os.indexOf("mac") >= 0);
 	}
 
	public static boolean isUnix() {
 		String os = System.getProperty("os.name").toLowerCase();
		return (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0);
 	}
	
	private void initConfig() {
		log.debug("initializing configuration.");
		
		// properties
		Properties props = new Properties();
		
		// new loader to load from disk instead of war file
		String configfilename = "/springfield/homer/config.xml";
		if (isWindows()) {
			configfilename = "/springfield/homer/config.xml";
		}
		
		// load from file
		try {
			log.debug("INFO: Loading config file from load : "+configfilename);
			File file = new File(configfilename);

			if (file.exists()) {
				props.loadFromXML(new BufferedInputStream(new FileInputStream(file)));
			} else { 
				log.debug("FATAL: Could not load config "+configfilename);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// only get the marge communication port unless we are a smithers
		port = Integer.parseInt(props.getProperty("marge-port"));
		
		role = props.getProperty("role");
		if (role==null) role = "production";
		log.debug("SERVER ROLE="+role);
	}

	private static String getPercentEncodedFullUri(String url) {
		try {
			if (url.startsWith("/") && url.length() > 1) url = url.substring(1);
			URI s = new URI(getSmithersUrl() + "/"); // Trailing slash to make sure smithers2 context is included.
			URI fullUri = new URI(s.getScheme(),
					s.getUserInfo(), s.getHost(), s.getPort(),
					s.getPath() + url, s.getQuery(), s.getFragment());
			return fullUri.toASCIIString();
		} catch (URISyntaxException e) {
			log.error("Could not produce legal URL", e);
		}
		return null;
	}

	public synchronized static String sendRequest(String method,String url,String body,String contentType) {
		String fullurl = getPercentEncodedFullUri(url);
		String result = null;
		boolean validresult = true;

		log.debug("Sending request. Method = " + method + ", url = " + fullurl + ", body = " + body);
		try {
			result = HttpHelper.sendRequest(method, fullurl, body, contentType);
			if (result.indexOf("<?xml")==-1) {
				log.error("FAIL TYPE ONE ("+fullurl+")");
				log.error("XML="+result);
				validresult = false;
			}
		} catch(Exception e) {
			log.error("FAIL TYPE TWO ("+fullurl+")", e);
			log.error("XML="+result);
			validresult = false;
		}
		
		// something is wrong retry with new server
		while (!validresult) {
			validresult = true;
			// turn the current one off
			if (selectedsmithers!=null) selectedsmithers.setAlive(false);
			getDifferentSmithers();
			fullurl = getSmithersUrl()+url;
			try {
				result = HttpHelper.sendRequest(method, fullurl, body, contentType);
				if (result.indexOf("<?xml")==-1) {
					log.error("FAIL TYPE THREE ("+fullurl+")");
					log.error("XML="+result);
					validresult = false;
				}
			} catch(Exception e) {
				validresult = false;
				log.error("FAIL TYPE FOUR ("+fullurl+")", e);
				log.error("XML="+result);
			}
		}
		
		log.debug("VALID REQUEST RESULT: " + result);
		
		return result;
	}
	
	private static void getDifferentSmithers() {
		log.debug("Request for new smithers");
		// lets first find our prefered smithers.
		UterProperties mp = getMyUterProperties();
		String pref = mp.getPreferedSmithers();
		SmithersProperties winner = null;
		for(Iterator<SmithersProperties> iter = smithers.values().iterator(); iter.hasNext(); ) {
			SmithersProperties sm = (SmithersProperties)iter.next();
			if (sm.isAlive()) {
				if (sm.getIpNumber().equals(pref))  {
					winner = sm; // we can return its the prefered
				} else if (winner==null) {
					winner = sm; // only override if empty
				}
			}
		}
		if (winner==null) {
			// they are all down ? ok this is tricky lets wait until one comes up
			boolean foundone = false;
			while (!foundone) {
				log.info("All smithers seem down waiting for one to recover");
				LazyHomer.send("INFO","/domain/internal/service/getname");
				for(Iterator<SmithersProperties> iter = smithers.values().iterator(); iter.hasNext(); ) {
					SmithersProperties sm = (SmithersProperties)iter.next();
					if (sm.isAlive()) {
						winner = sm;
						selectedsmithers = null;
						foundone = true;
					}
				} 
				if (!foundone) {
					try {
						Thread.sleep(5000);
					} catch(Exception e) {}
				}
			}
	
		}
		
		if (winner!=selectedsmithers) {
			LazyHomer.sendRequest("PUT", "/domain/internal/service/uter/nodes/"+myip+"/properties/activesmithers", winner.getIpNumber(), "text/xml");
			if (selectedsmithers==null) {
				log.info("changed to "+winner.getIpNumber()+" prefered="+pref);
			} else {
				log.info("changed from "+selectedsmithers.getIpNumber()+" to "+winner.getIpNumber()+" prefered="+pref);
			}
		}
		selectedsmithers = winner;
	}
	
	/**
	 * get root path
	 */
	public static String getRootPath() {
		return rootPath;
	}
	
	private static void setLogLevel(String level) {
		Level logLevel = Level.INFO;
		Level oldlevel = log.getLogger(PACKAGE_ROOT).getLevel();
		switch (loglevels.valueOf(level)) {
			case all : logLevel = Level.ALL;break;
			case info : logLevel = Level.INFO;break;
			case warn : logLevel = Level.WARN;break;
			case debug : logLevel = Level.DEBUG;break;
			case trace : logLevel = Level.TRACE;break;
			case error: logLevel = Level.ERROR;break;
			case fatal: logLevel = Level.FATAL;break;
			case off: logLevel = Level.OFF;break;
		}
		if (logLevel.toInt()!=oldlevel.toInt()) {
			log.getLogger(PACKAGE_ROOT).setLevel(logLevel);
			log.info("logging level: " + logLevel);
		}
	}

	/**
	 * Initializes logger
	 */
	private void initLogger() {
		File xmlConfig = new File("/springfield/uter/log4j.xml");
		if (xmlConfig.exists()) {
			System.out.println("UTER: Reading logging config from XML file at " + xmlConfig);
			DOMConfigurator.configure(xmlConfig.getAbsolutePath());
			log.info("Logging configured from file: " + xmlConfig);
		}
		else {
			System.out.println("UTER: Could not find logger config at " + xmlConfig);
		}
		log.info("Initializing logging done.");
	}
    
	public synchronized static String sendRequestBart(String method,String url,String body,String contentType) {
		String fullurl = getBartUrl()+url;
		String result = null;
		boolean validresult = true;
		
		// first try 
		try {
			//log.debug("BEFORE LOU LAZY FULLURL="+fullurl+" method="+method+" ct="+contentType);
			result = HttpHelper.sendRequest(method, fullurl, body, contentType);
			//System.out.println("AFTER LOU LAZY FULLURL="+fullurl+" result="+result);
			if (result.indexOf("<?xml")==-1) {
				log.error("FAIL TYPE ONE ("+fullurl+")");
				log.error("XML="+result);
				String b = null;
				b.toString();
				validresult = false;
			}
		} catch(Exception e) {
			log.error("FAIL TYPE TWO ("+fullurl+")");
			log.error("XML="+result);
			e.printStackTrace();
			validresult = false;
		}
		
		// something is wrong retry with new server
		while (!validresult) {
			validresult = true;
			// turn the current one off
			if (selectedsmithers!=null) selectedsmithers.setAlive(false);
			getDifferentSmithers();
			fullurl = getBartUrl()+url;
			try {
				result = HttpHelper.sendRequest(method, fullurl, body, contentType);
				if (result.indexOf("<?xml")==-1) {
					log.error("FAIL TYPE THREE ("+fullurl+")");
					log.error("XML="+result);
					validresult = false;
				}
			} catch(Exception e) {
				validresult = false;
				log.error("FAIL TYPE FOUR ("+fullurl+")");
				log.error("XML="+result);
			}
		}
		
		log.debug("VALID REQUEST RESULT ("+fullurl+") ");
		return result;
	}
	
	public static String getBartUrl() {
		if (selectedsmithers==null) {
			for(Iterator<SmithersProperties> iter = smithers.values().iterator(); iter.hasNext(); ) {
				SmithersProperties s = (SmithersProperties)iter.next();
				if (s.isAlive()) {
					selectedsmithers = s;
				}
			}
		}
		return "http://"+selectedsmithers.getIpNumber()+":"+selectedsmithers.getPort()+"/bart";
	}


	
    /**
     * Shutdown
     */
	public static void destroy() {
		// destroy timer
		if (marge!=null) {
			marge.destroy();
			try {
				marge.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private class DiscoveryThread extends Thread {
	    DiscoveryThread() {
	      super("dthread");
	      log.info("Starting discovery thread for Uter...");
	      start();
	    }

	    public void run() {
	    	log.info("Discovery thread for Uter started.");
	     int counter = 0;
	      while (LazyHomer.noreply || counter<10) {
	      	log.debug("counter = " + counter);
	    	if (counter>4 && LazyHomer.noreply) log
            .info("Still looking for smithers on multicast port "+port+" ("+LazyHomer.noreply+")");
	    	LazyHomer.send("INFO","/domain/internal/service/getname");
	        try {
	          sleep(500+(counter*100));
	          counter++;
	        } catch (InterruptedException e) {
	        	log.error("Discovery thread for Uter was interrupted");
	          throw new RuntimeException(e);
	        }
	      }
	      log.info("Stopped looking for new smithers");
	    }
	}
	
	public static int getPort() {
		return port;
	}

	public static String getRole() {
		return role;
	}
	
	public static MountProperties getMountProperties(String name) {
		if (mounts==null) readMounts();
		return mounts.get(name);
	}
	
	private static void readMounts() {
		mounts = new HashMap<String, MountProperties>();
		String mountslist = LazyHomer.sendRequest("GET","/domain/internal/service/uter/mounts",null,null);
		try { 
			Document result = DocumentHelper.parseText(mountslist);
			for(Iterator<Node> iter = result.getRootElement().nodeIterator(); iter.hasNext(); ) {
				Element child = (Element)iter.next();
				if (!child.getName().equals("properties")) {
					String name = child.attributeValue("id");
					String hostname = child.selectSingleNode("properties/hostname") == null ? ""  : child.selectSingleNode("properties/hostname").getText();
					String path = child.selectSingleNode("properties/path") == null ? "" : child.selectSingleNode("properties/path").getText();
					String account = child.selectSingleNode("properties/account") == null ? "" : child.selectSingleNode("properties/account").getText();
					String password = child.selectSingleNode("properties/password") == null ? "" : child.selectSingleNode("properties/password").getText();
					String protocol = child.selectSingleNode("properties/protocol") == null ? "" : child.selectSingleNode("properties/protocol").getText();
					MountProperties mp = new MountProperties();
					mp.setHostname(hostname);
					mp.setPath(path);
					mp.setAccount(account);
					mp.setPassword(password);
					mp.setProtocol(protocol);
					mounts.put(name, mp);
				}
			}
		} catch (DocumentException e) {
			log.info("LazyHomer: "+e.getMessage());
		}
	}
	
}
