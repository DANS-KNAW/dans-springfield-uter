/* 
* GlobalConfig.java
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;


/**
 * Singleton configuration class
 *
 */
public class GlobalConfig {
	/** Singleton instance */
	private static GlobalConfig instance = new GlobalConfig();
	
	/** Default name of configuration file */
	private static String CONFIG_FILE = "config.xml";
	
	/** package root */
	public static final String PACKAGE_ROOT = "org.springfield.uter";
	
	/** service name */
	public static final String SERVICE_NAME = "uter";
	
	/** image path */
	public File IMAGE_PATH;
	
	/** running directory */
	private String baseDir;
	
	/**
	 * Sole constructor
	 */
	private GlobalConfig() {}
	
	/**
	 * Returns singleton instance
	 * 
	 * @return singleton instance
	 */
	public static GlobalConfig getInstance() {
		return instance;
	}
	
	/**
	 * Should be called upon startup
	 */
	public void initialize(String baseDir) {
		this.baseDir = baseDir;
		initConfig();
		initLogging();
	}

	/**
	 * Should be called upon teardown
	 */
	public void destroy() {
		
	}
	
	/**
	 * Initializes configuration
	 */
	private void initConfig() {
		System.out.println(SERVICE_NAME + ": initializing configuration.");
		
		// properties
		Properties props = new Properties();
		
		// load from file
		try {
			
			File file = new File(baseDir + "/conf/" + CONFIG_FILE);
			System.out.println(SERVICE_NAME + ": config path "+file);
			if (file.exists()) {
				props.loadFromXML(new BufferedInputStream(new FileInputStream(file)));
				IMAGE_PATH = new File(props.getProperty("image-path"));
				System.out.println(SERVICE_NAME + ": image path "+IMAGE_PATH);
			} else { 
				System.out.println(SERVICE_NAME + ": Could not load config");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println(SERVICE_NAME + ": initializing configuration done.");
	}
	
	
	/**
	 * Initializes logging
	 */
	private void initLogging() {
		System.out.println(SERVICE_NAME + ": initializing logging.");
		
		// enable appenders
		String logPath = "";
		int index = baseDir.indexOf("webapps");
    	if(index!=-1) {
    		logPath += baseDir.substring(0,index);
    	}
		logPath += "logs/"+SERVICE_NAME+"/"+SERVICE_NAME+".log";	
		
		try {
			// default layout
			Layout layout = new PatternLayout("%-5p: %d{yyyy-MM-dd HH:mm:ss,SSS} %c %x - %m%n");
			
			// rolling file appender
			DailyRollingFileAppender appender1 = new DailyRollingFileAppender(layout,logPath,"'.'yyyy-MM-dd");
			BasicConfigurator.configure(appender1);
			
			// console appender 
			ConsoleAppender appender2 = new ConsoleAppender(layout);
			BasicConfigurator.configure(appender2);
		}
		catch(IOException e) {
			System.out.println(SERVICE_NAME + ": GlobalConfig got an exception while initializing the logging configuration");
			e.printStackTrace();
		}
		
		/*
		 *  turn off all logging, and enable ERROR logging for root package
		 *  use restlet.LoggingResource to enable specific logging
		 */
		Logger.getRootLogger().setLevel(Level.OFF);
		Logger.getLogger(PACKAGE_ROOT).setLevel(Level.ERROR);
		
		System.out.println(SERVICE_NAME + ": initializing logging done.");
	}
	
	
	
}