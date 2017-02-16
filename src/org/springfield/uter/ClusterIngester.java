/* 
* ClusterIngester.java
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springfield.uter.fs.FsNode;
import org.springfield.uter.homer.LazyHomer;

import com.noterik.springfield.tools.HttpHelper;

public class ClusterIngester {
	private static String[] frinkServers = new String[] {"frink1.noterik.com","frink2.noterik.com","frink5.noterik.com"};
	private static String[] bartServers = new String[] {"bart1.noterik.com","bart2.noterik.com","bart5.noterik.com"};
	private static String screenshotProperty = "";
	
	public static Boolean checkOldCluster(String provider, String nType, FsNode node) {
		
		String frinkUrl = "";
		String filename = node.getProperty("filename");
		String ext = FilenameUtils.getExtension(filename);
		String nid = "euscreen-"+provider;
		if(provider.equals("eu_nisv")) {
			nid = "euscreen-eu_bg";
			String uri = node.getProperty("landingPageURL");
			if(uri==null) return false;
			if(uri.contains("//www.surfmedia.nl/app/video/")) {
				uri = uri.replace("http://www.surfmedia.nl/app/video/", "");
				uri = uri.replace("&mode=object", "").replace("&mode=url", "");
				filename = encodeASCII8(uri);
			}
		}
		String nss = filename;
		screenshotProperty = "";
		try {
			nss = URLEncoder.encode(filename, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.out.println("could not urlencode: " + filename);
		}
		frinkUrl += "?nid=" + nid + "&nss=" + nss;
		String response = sendFrinkRequest(frinkUrl);
		if(response==null) {
			response = sendFrinkRequest(frinkUrl);
		}
		
		if(response==null) return false;
		String itemUri = "";
		
		System.out.println("response = "+response);
		
		try {
			Document doc = DocumentHelper.parseText(response);
			List<Node> urns = doc.selectNodes("//urn");
			if(urns.isEmpty()) {
				System.out.println("Frink collection does not exist: " + frinkUrl);
				return false;
			}
			for (Iterator<Node> cIter = urns.iterator(); cIter.hasNext();) {
				Element urn = (Element) cIter.next();
				String frinknid = urn.element("nid").getTextTrim();
				if(frinknid.equals("springfield")) {
					itemUri = urn.element("nss").getTextTrim();
					break;
				}
			}
			if(itemUri.equals("")) {
				System.out.println("Incomplete FRINK data: " + frinkUrl);
				return false;
			}
		} catch (DocumentException e) {
			System.out.println("could not get presentationuri from frink"+response);
		}
		
		String bartUrl = itemUri;
		if(nType.equals("video")) {
			bartUrl += "/videoplaylist/1/video";
			String newbody = processVideo(bartUrl);
			if(newbody==null) return false;			
			
			String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/video/"+node.getId()+"/rawvideo/1/properties",newbody,"text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+node.getId()+"/properties/hasRaws", "true", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+node.getId()+"/properties/datasource", "euscreen", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+node.getId()+"/properties/ingestreport", "done", "text/xml");
			if(screenshotProperty.length()>0) {
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+node.getId()+"/properties/screenshot", screenshotProperty, "text/xml");
			}
			return true;
			
		}
		
		if(nType.equals("audio")) {
			String newbody = processAudio(bartUrl);
			if(newbody==null) return false;
			
			String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/audio/"+node.getId()+"/rawaudio/1/properties",newbody,"text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+node.getId()+"/properties/hasRaws", "true", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+node.getId()+"/properties/datasource", "euscreen", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+node.getId()+"/properties/ingestreport", "done", "text/xml");
			return true;
		}
		
		if(nType.equals("picture")) {
			String newbody = processPicture(bartUrl);
			if(newbody==null) return false;
			
			String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/picture/"+node.getId()+"/rawpicture/1/properties",newbody,"text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+node.getId()+"/properties/hasRaws", "true", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+node.getId()+"/properties/datasource", "euscreen", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+node.getId()+"/properties/ingestreport", "done", "text/xml");
			if(screenshotProperty.length()>0) {
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+node.getId()+"/properties/screenshot", screenshotProperty, "text/xml");
			}
			return true;
		}
		
		if(nType.equals("pdf")) {
			String newbody = processDocument(bartUrl);
			if(newbody==null) return false;
			
			String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/doc/"+node.getId()+"/rawdoc/1/properties",newbody,"text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+node.getId()+"/properties/hasRaws", "true", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+node.getId()+"/properties/datasource", "euscreen", "text/xml");
			LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+node.getId()+"/properties/ingestreport", "done", "text/xml");
			return true;
		}
		
		return false;
	}
	
	private static String processDocument(String url) {
		
		String mount = "http://images1.noterik.com"+url+"/raw.pdf";
		String newbody ="<fsxml><properties>";	
		// enum the properties
		
		newbody+="<format>PDF</format>\n";
		newbody+="<extension>pdf</extension>\n";
		newbody+="<mount>"+mount+"</mount>\n";
		newbody+="<original>true</original>\n";
		newbody+="</properties></fsxml>";
		
		return newbody;
	}
	
	private static String processAudio(String url) {
		
		String mount = "http://images1.noterik.com"+url+"/raw.mp3";
		String newbody ="<fsxml><properties>";	
		// enum the properties
		
		newbody+="<format>MP3</format>\n";
		newbody+="<extension>mp3</extension>\n";
		newbody+="<mount>"+mount+"</mount>\n";
		newbody+="<original>true</original>\n";
		newbody+="</properties></fsxml>";
		
		return newbody;
	}
	
	private static String processPicture(String url) {
		
		String mount = "http://images1.noterik.com"+url+"/raw.png";
		String newbody ="<fsxml><properties>";	
    	// enum the properties

		newbody+="<format>PNG</format>\n";
		newbody+="<extension>png</extension>\n";
		newbody+="<mount>"+mount+"</mount>\n";
		newbody+="<original>true</original>\n";
    	newbody+="</properties></fsxml>";
    	
    	screenshotProperty = mount;
    	return newbody;
	}
	
	public static String processVideo(String url) {
		
		String newbody = null;
		String response = sendBartRequest(url, 2);
		if(response==null) return null;
 		try { 
			Document doc = DocumentHelper.parseText(response);
			 
			Element presentationVideo = (Element) doc.selectSingleNode("//video");
			if(presentationVideo==null) {
				System.out.println("Error getting video");
				return null; // node not found
			}
			
			
			String videoUri = presentationVideo.attributeValue("referid");
			if(videoUri==null) return null;
			System.out.println("Video uri: " + videoUri);
			response = sendBartRequest(videoUri, 1);
			
			if(response==null) return null;
			doc = DocumentHelper.parseText(response);
			
			Node mountNode = doc.selectSingleNode("//rawvideo/properties/mount");
			if(mountNode==null) {
				
				response = sendBartRequest(videoUri, 1);
				if(response==null) return null;
				doc = DocumentHelper.parseText(response);
				mountNode = doc.selectSingleNode("//rawvideo/properties/mount");
				if(mountNode==null) {
					System.out.println("Error getting rawvideo");
					return null;
				}
				//return false; // node not found
			}
			
			String rawVideoId = doc.selectSingleNode("//rawvideo/@id").getText();
			String mount = mountNode.getText();
			
			Node screensNode = doc.selectSingleNode("//screens/properties/uri");
			String screenshot = null;
			if(screensNode!=null) {
				screenshot = screensNode.getText();
				screenshot += "/h/0/m/0/sec5.jpg"; //Take the 5th second.
				screenshotProperty = screenshot;
			}
			
			if(!mount.contains("://")) { //Internal mount
				String[] mounts = mount.split(",");
				String[] newMounts = new String[mounts.length];
				for(int idx=0; idx<mounts.length; idx++) {
					String m = mounts[idx];
					String newM = "";
					m = m.trim();
					if(m.contains("stream")) {
						newMounts[idx] = "http://" + m + ".noterik.com/progressive/"	+ m +videoUri + "/rawvideo/" + rawVideoId + "/raw.mp4";
					}
				}
				
				mount = StringUtils.join(newMounts, ",");
			}
	    	
	    	
    		// create the rawentry
	    	newbody ="<fsxml><properties>";	
	    	// enum the properties
			newbody+="<format>MP4</format>\n";
			newbody+="<extension>mp4</extension>\n";
			newbody+="<mount>"+mount+"</mount>\n";
			newbody+="<original>true</original>\n";
			newbody+="<status>done</status>\n";
	    	newbody+="</properties></fsxml>";
			
 		} catch(Exception e) {
 			e.printStackTrace();
 		}
 		
 		return newbody;
	}
	
	private static String getFrinkServer() {
		int num = (int)Math.floor(Math.random()*frinkServers.length);
		return frinkServers[num];
	}
	
	private static String getBartServer() {
		int num = (int)Math.floor(Math.random()*bartServers.length);
		return bartServers[num];
	}
	
	private static String sendFrinkRequest(String url) {
		boolean validresult = true;
		String result = null;
		String fullurl = "http://" + getFrinkServer() + "/frink/collection" + url;
		//System.out.println("M="+method+" "+fullurl+" "+url);
		// first try 
		try {
			result = HttpHelper.sendRequest("GET",fullurl, null, null);
			if (result.indexOf("<?xml")==-1) {
				System.out.println("FRINK FAIL TYPE ONE ("+fullurl+")");
				System.out.println("XML="+result);
				validresult = false;
				result=null;
			}
		} catch(Exception e) {
			System.out.println("FRINK FAIL TYPE TWO ("+fullurl+")");
			System.out.println("XML="+result);
			validresult = false;
			result=null;
		}
		
		return result;
	}

	public static String sendBartRequest(String url, int depth) {
		boolean validresult = true;
		String result = null;
		String fullurl = "http://" + getBartServer() + "/bart" + url;
		String xml = "<fsxml><properties><depth>"+depth+"</depth></properties></fsxml>";
		// first try 
		try {
			result = HttpHelper.sendRequest("GET",fullurl, xml, "text/xml");
			if (result.indexOf("<?xml")==-1) {
				System.out.println("FRINK FAIL TYPE ONE ("+fullurl+")");
				System.out.println("XML="+result);
				validresult = false;
				return null;
			}
		} catch(Exception e) {
			System.out.println("FRINK FAIL TYPE TWO ("+fullurl+")");
			System.out.println("XML="+result);
			validresult = false;
			return null;
		}
		
		return result;
	}
	
	public static String getPictureScreenshot(String filename, String provider) {
		String frinkUrl = "";
		String ext = FilenameUtils.getExtension(filename);
		if(provider.equals("eu_nisv")) provider = "eu_bg";
		String nid = "euscreen-"+provider;
		String nss = filename;
		try {
			nss = URLEncoder.encode(filename, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.out.println("could not urlencode: " + filename);
		}
		frinkUrl += "?nid=" + nid + "&nss=" + nss;
		String response = sendFrinkRequest(frinkUrl);
		if(response==null) {
			response = sendFrinkRequest(frinkUrl);
		}
		
		if(response==null) return null;
		String itemUri = "";
		
		System.out.println("response = "+response);
		
		try {
			Document doc = DocumentHelper.parseText(response);
			List<Node> urns = doc.selectNodes("//urn");
			if(urns.isEmpty()) {
				System.out.println("Frink collection does not exist: " + frinkUrl);
				return null;
			}
			for (Iterator<Node> cIter = urns.iterator(); cIter.hasNext();) {
				Element urn = (Element) cIter.next();
				String frinknid = urn.element("nid").getTextTrim();
				if(frinknid.equals("springfield")) {
					itemUri = urn.element("nss").getTextTrim();
					break;
				}
			}
			if(itemUri.equals("")) {
				System.out.println("Incomplete FRINK data: " + frinkUrl);
				return null;
			}
		} catch (DocumentException e) {
			System.out.println("could not get presentationuri from frink"+response);
		}
		
		String screenshot = "http://images1.noterik.com" + itemUri + "/raw.png";
		return screenshot;
	}
	
	public static String getScreenshot(String filename, String provider) {
		String frinkUrl = "";
		String ext = FilenameUtils.getExtension(filename);
		if(provider.equals("eu_nisv")) provider = "eu_bg";
		String nid = "euscreen-"+provider;
		String nss = filename;
		try {
			nss = URLEncoder.encode(filename, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.out.println("could not urlencode: " + filename);
		}
		frinkUrl += "?nid=" + nid + "&nss=" + nss;
		String response = sendFrinkRequest(frinkUrl);
		if(response==null) {
			response = sendFrinkRequest(frinkUrl);
		}
		
		if(response==null) return null;
		String itemUri = "";
		
		System.out.println("response = "+response);
		
		try {
			Document doc = DocumentHelper.parseText(response);
			List<Node> urns = doc.selectNodes("//urn");
			if(urns.isEmpty()) {
				System.out.println("Frink collection does not exist: " + frinkUrl);
				return null;
			}
			for (Iterator<Node> cIter = urns.iterator(); cIter.hasNext();) {
				Element urn = (Element) cIter.next();
				String frinknid = urn.element("nid").getTextTrim();
				if(frinknid.equals("springfield")) {
					itemUri = urn.element("nss").getTextTrim();
					break;
				}
			}
			if(itemUri.equals("")) {
				System.out.println("Incomplete FRINK data: " + frinkUrl);
				return null;
			}
		} catch (DocumentException e) {
			System.out.println("could not get presentationuri from frink"+response);
		}
		
		String screenshot = null, customscreenshot = null;
		//First check if custom screenshot node is set
		String bartUrl = itemUri + "/videoplaylist/1/screenshot";
		response = sendBartRequest(bartUrl, 1);
		try { 
			Document doc = DocumentHelper.parseText(response);
			 
			Element screenshotnode = (Element) doc.selectSingleNode("//screenshot");
			if(screenshotnode!=null) {
				
				Node screenshotSTNode = doc.selectSingleNode("//screenshot/properties/starttime");
				if(screenshotSTNode!=null) {
					float startfl = Float.parseFloat(screenshotSTNode.getText());
					int starttime = (int) startfl;
					int seconds = (int) Math.round(starttime/1000)+1;
					int m = seconds / 60;
					int h = 0;
					if(m>=60) {
						h = m / 60;
						m = m - h*60;
					}
					
					int sec = seconds - (h*3600 + m*60);
					customscreenshot = "/h/"+h+"/m/"+m+"/sec"+sec+".jpg"; //Take the 5th second.
				}
			}
 		} catch(Exception e) {
 			e.printStackTrace();
 		}
		
		
		
		bartUrl = itemUri + "/videoplaylist/1/video";
		response = sendBartRequest(bartUrl, 2);
		if(response==null) return null;
		
		
 		try { 
			Document doc = DocumentHelper.parseText(response);
			 
			Element presentationVideo = (Element) doc.selectSingleNode("//video");
			if(presentationVideo==null) {
				System.out.println("Error getting video");
				return null; // node not found
			}
			
			
			String videoUri = presentationVideo.attributeValue("referid");
			if(videoUri==null) return null;
			System.out.println("Video uri: " + videoUri);
			response = sendBartRequest(videoUri, 1);
			
			if(response==null) return null;
			doc = DocumentHelper.parseText(response);
			
			Node screensNode = doc.selectSingleNode("//screens/properties/uri");
			
			if(screensNode!=null) {
				
				screenshot = screensNode.getText();
				if(customscreenshot==null) {
					screenshot += "/h/0/m/0/sec5.jpg"; //Take the 5th second.
				} else {
					screenshot += customscreenshot; //Use the custom screenshot
				}
			}
 		} catch(Exception e) {
 			e.printStackTrace();
 		}
		
 		return screenshot;
	}
	
	public static String encodeASCII8(String input) {
		String output="";
		for (int i=0;i<input.length();i++) {
			int code = input.codePointAt(i);
			if (code>127) {
				output+="\\"+code;
			} else if (code==13) {
				output+="\\013";
			} else {
				if (code==37) {
						output+="\\037";
				} else if (code==35) {
						output+="\\035";
				} else if (code==61) {
					output+="\\061";
				} else if (code==92) {
						output+="\\092";
				}  
				else {
						output+=input.charAt(i);	
				}
			}
		}
		return output;
	}
}
