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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.*;
import org.dom4j.*;

import com.noterik.springfield.tools.HttpHelper;


public class LazyHomer implements MargeObserver {
	
	private static Logger LOG = Logger.getLogger(LazyHomer.class);

	/** Noterik package root */
	public static final String PACKAGE_ROOT = "com.noterik";
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
		initConfig();
		initLogger();
		
		try{
			InetAddress mip=InetAddress.getLocalHost();
			myip = ""+mip.getHostAddress();
		}catch (Exception e){
			System.out.println("Exception ="+e.getMessage());
		}
		LOG.info("Uter init service name = uter on ipnumber = "+myip);
		System.out.println("Uter init service name = uter on ipnumber = "+myip+" on marge port "+port);
		marge = new LazyMarge();
		
		// lets watch for changes in the service nodes in smithers
		marge.addObserver("/domain/internal/service/uter/nodes/"+myip, ins);
		marge.addTimedObserver("/smithers/downcheck",6,this);
		new DiscoveryThread();	
	}

	public static void addSmithers(String ipnumber,String port,String mport,String role) {
		int oldsize = smithers.size();
		if (!(""+LazyHomer.getPort()).equals(mport)) {
			System.out.println("UTER EXTREEM WARNING CLUSTER COLLISION ("+LazyHomer.getPort()+") "+ipnumber+":"+port+":"+mport);
			return;
		}
		
		if (!role.equals(getRole())) {
			System.out.println("uter : Ignored this smithers ("+ipnumber+") its "+role+" and not "+getRole()+" like us");
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
			LOG.info("uter found smithers at = "+ipnumber+" port="+port+" multicast="+mport);
			System.out.println("uter found smithers at = "+ipnumber+" port="+port+" multicast="+mport);
		} else {
			if (!sp.isAlive()) {
				sp.setAlive(true); // since talking its alive again !
				LOG.info("uter recovered smithers at = "+ipnumber);
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
				LOG.info("This uter will be started (on startup)");
			}
		} else {
			if (running) {
				running = false;
			} else {
				LOG.info("This lou is not turned on, use smithers todo this for ip "+myip);
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
		//System.out.println("NODES="+nodes);
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
							System.out.println("This uter is not verified change its name, use smithers todo this for ip "+myip);
						} else {
							// so we have a name (verified) return true
							iamok = true;
						}
					}
				}	
			}
			if (!foundmynode) {
				LOG.info("LazyHomer : Creating my processing node "+LazyHomer.getSmithersUrl()  + "/domain/internal/service/uter/properties");
				String os = "unknown"; // we assume windows ?
				try{
					  os = System.getProperty("os.name");
				} catch (Exception e){
					System.out.println("LazyHomer : "+e.getMessage());
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
			LOG.info("LazyHomer exception doc");
			e.printStackTrace();
		}
		return iamok;
	}
	
	public static void setLastSeen() {
		Long value = new Date().getTime();
		LazyHomer.sendRequest("PUT", "/domain/internal/service/uter/nodes/"+myip+"/properties/lastseen", ""+value, "text/xml");
	}
	

	
	public static void send(String method, String uri) {
		try {
			MulticastSocket s = new MulticastSocket();
			String msg = myip+" "+method+" "+uri;
			byte[] buf = msg.getBytes();
			//System.out.println("UTER SEND="+msg);
			DatagramPacket pack = new DatagramPacket(buf, buf.length,InetAddress.getByName(group), port);
			s.send(pack,(byte)ttl);
			s.close();
		} catch(Exception e) {
			System.out.println("LazyHomer error "+e.getMessage());
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
					LOG.info("One or more smithers down, try to recover it");
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
					LOG.info("This edna will be started");
					serv.init();
				}
				setLogLevel(mp.getDefaultLogLevel());
			} else {
				if (serv.isRunning()) {
					LOG.info("This edna will be turned off");
					serv.destroy();
				} else {
					LOG.info("This edna is not turned on, use smithers todo this for ip "+myip);
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
		System.out.println("Uter: initializing configuration.");
		
		// properties
		Properties props = new Properties();
		
		// new loader to load from disk instead of war file
		String configfilename = "/springfield/homer/config.xml";
		if (isWindows()) {
			configfilename = "/springfield/homer/config.xml";
		}
		
		// load from file
		try {
			System.out.println("INFO: Loading config file from load : "+configfilename);
			File file = new File(configfilename);

			if (file.exists()) {
				props.loadFromXML(new BufferedInputStream(new FileInputStream(file)));
			} else { 
				System.out.println("FATAL: Could not load config "+configfilename);
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
		System.out.println("SERVER ROLE="+role);
	}

	
	public synchronized static String sendRequest(String method,String url,String body,String contentType) {
		String fullurl = getSmithersUrl()+url;
		String result = null;
		boolean validresult = true;
		
		//System.out.println("M="+method+" "+fullurl+" "+url);
		// first try 
		try {
			result = HttpHelper.sendRequest(method, fullurl, body, contentType);
			if (result.indexOf("<?xml")==-1) {
				LOG.error("FAIL TYPE ONE ("+fullurl+")");
				LOG.error("XML="+result);
				validresult = false;
			}
		} catch(Exception e) {
			LOG.error("FAIL TYPE TWO ("+fullurl+")");
			LOG.error("XML="+result);
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
					LOG.error("FAIL TYPE THREE ("+fullurl+")");
					LOG.error("XML="+result);
					validresult = false;
				}
			} catch(Exception e) {
				validresult = false;
				LOG.error("FAIL TYPE FOUR ("+fullurl+")");
				LOG.error("XML="+result);
			}
		}
		
		LOG.debug("VALID REQUEST RESULT ("+fullurl+") ");
		
		return result;
	}
	
	private static void getDifferentSmithers() {
		LOG.debug("Request for new smithers");
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
				LOG.info("All smithers seem down waiting for one to recover");
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
				LOG.info("changed to "+winner.getIpNumber()+" prefered="+pref);
			} else {
				LOG.info("changed from "+selectedsmithers.getIpNumber()+" to "+winner.getIpNumber()+" prefered="+pref);
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
		Level oldlevel = LOG.getLogger(PACKAGE_ROOT).getLevel();
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
			LOG.getLogger(PACKAGE_ROOT).setLevel(logLevel);
			LOG.info("logging level: " + logLevel);
		}
	}
	
 
	/**
	 * Initializes logger
	 */
    private void initLogger() {    	 
    	System.out.println("Initializing logging.");
    	
    	// get logging path
    	String logPath = LazyHomer.getRootPath().substring(0,LazyHomer.getRootPath().indexOf("webapps"));
		logPath += "logs/uter/uter.log";	
		

		
		try {
			// default layout
			Layout layout = new PatternLayout("%-5p: %d{yyyy-MM-dd HH:mm:ss} %c %x - %m%n");
			
			// rolling file appender
			DailyRollingFileAppender appender1 = new DailyRollingFileAppender(layout,logPath,"'.'yyyy-MM-dd");
			BasicConfigurator.configure(appender1);
			
			// console appender 
			ConsoleAppender appender2 = new ConsoleAppender(layout);
			BasicConfigurator.configure(appender2);
		}
		catch(IOException e) {
			System.out.println("UterServer got an exception while initializing the logger.");
			e.printStackTrace();
		}
		
		Level logLevel = Level.INFO;
		LOG.getRootLogger().setLevel(Level.OFF);
		LOG.getLogger(PACKAGE_ROOT).setLevel(logLevel);
		LOG.info("logging level: " + logLevel);
		
		LOG.info("Initializing logging done.");
    }
    
	public synchronized static String sendRequestBart(String method,String url,String body,String contentType) {
		String fullurl = getBartUrl()+url;
		String result = null;
		boolean validresult = true;
		
		// first try 
		try {
			//System.out.println("BEFORE LOU LAZY FULLURL="+fullurl+" method="+method+" ct="+contentType);
			result = HttpHelper.sendRequest(method, fullurl, body, contentType);
			//System.out.println("AFTER LOU LAZY FULLURL="+fullurl+" result="+result);
			if (result.indexOf("<?xml")==-1) {
				LOG.error("FAIL TYPE ONE ("+fullurl+")");
				LOG.error("XML="+result);
				String b = null;
				b.toString();
				validresult = false;
			}
		} catch(Exception e) {
			LOG.error("FAIL TYPE TWO ("+fullurl+")");
			LOG.error("XML="+result);
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
					LOG.error("FAIL TYPE THREE ("+fullurl+")");
					LOG.error("XML="+result);
					validresult = false;
				}
			} catch(Exception e) {
				validresult = false;
				LOG.error("FAIL TYPE FOUR ("+fullurl+")");
				LOG.error("XML="+result);
			}
		}
		
		LOG.debug("VALID REQUEST RESULT ("+fullurl+") ");
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
	      start();
	    }

	    public void run() {
	     int counter = 0;
	      while (LazyHomer.noreply || counter<10) {
	    	if (counter>4 && LazyHomer.noreply) LOG.info("Still looking for smithers on multicast port "+port+" ("+LazyHomer.noreply+")");
	    	LazyHomer.send("INFO","/domain/internal/service/getname");
	        try {
	          sleep(500+(counter*100));
	          counter++;
	        } catch (InterruptedException e) {
	          throw new RuntimeException(e);
	        }
	      }
	      LOG.info("Stopped looking for new smithers");
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
			LOG.info("LazyHomer: "+e.getMessage());
		}
	}
	
}
