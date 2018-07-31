/* 
* FtpIngester.java
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPListParseEngine;
import org.springfield.uter.fs.*;
import org.springfield.uter.homer.*;

import com.noterik.springfield.tools.HttpHelper;
import com.noterik.springfield.tools.ftp.FtpHelper;

public class FtpIngester extends Thread {
	
	public static void checkProviderVideo(String provider) {
		System.out.println("UTER: FTPIngester:  check provider "+provider);
		String uri = "/domain/euscreenxl/user/"+provider+"/video";
		String seriesuri = "/domain/euscreenxl/user/"+provider+"/series";
		ArrayList<String> videodir = null;
		
		FSList fsseries = FSListManager.get(seriesuri);
		List<FsNode> series = fsseries.getNodes();
		Properties seriesMap = new Properties();
		for(Iterator<FsNode> iter = series.iterator() ; iter.hasNext(); ) {
			FsNode n = (FsNode)iter.next();	
			String path = n.getPath();
			path = path.replace("/series/series","/series"); // bug in FsNode in lou needs fixing
			
			FsNode snode = Fs.getNode(path);
			String seriesTitle =  snode.getProperty("TitleSet_TitleSetInOriginalLanguage_seriesOrCollectionTitle");
			if(seriesTitle!=null) {
				seriesMap.put(snode.getId(), seriesTitle);
			}
		}
		
		// allways 'loads' the full result;
		FSList fslist = FSListManager.get(uri);
		System.out.println("UTER: FTPIngester: ingest list size = "+fslist.size());
		
		// now we can query the resultset;
		List<FsNode> nodes = fslist.getNodes();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {

			FsNode n = (FsNode)iter.next();	
			String path = n.getPath();
			path = path.replace("/video/video","/video"); // bug in FsNode in lou needs fixing
			System.out.println("UTER: FTPIngester: get video node = "+path);
			FsNode vnode = Fs.getNode(path);
			String filename = vnode.getProperty("filename"); // bug props should already be in the n node.
			if(filename==null) {
				continue;
			}
			if (provider.equals("eu_tvc") && filename!=null) {
				filename = filename.replace(" ", "_");
			}
			
			//Check if for some reason the video node materialType is not video
			String curType = vnode.getProperty("TechnicalInformation_materialType");
			if(!curType.equalsIgnoreCase("video")) {
				LazyHomer.sendRequest("DELETE", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId(), null, null);
				continue;
			}
			
			System.out.println("UTER: FTPIngester: check for filename = "+filename);
			// ok for each of these is there also a raw node one already ?
			FsNode rawvideonode = Fs.getNode(path+"/rawvideo/1");
			System.out.println("UTER: FTPIngester: check FS: "+path+"/rawvideo/1");
			if (rawvideonode==null) {
				// we don't have a raw node
				//System.out.println("PROVIDER="+provider+" RAWNODE="+rawvideonode+" FILENAME="+filename);
				// do we have this in our ftp list ?
				if (videodir==null) videodir = getFtpList(provider,"videos");
				if (videodir!=null) {
					if (videodir.contains(filename)) {
						//System.out.println("WHOO WE HAVE THE FILE ON DISK");
						if (getFileToTemp(provider,"videos",filename)) {
							System.out.println("UTER: GOT LOCAL COPY = "+filename+" TO "+vnode.getId());
							getFileToStream(provider,"video",vnode.getId(),filename);
							
							// create the rawentry
					    	String newbody ="<fsxml><properties>";	
					    	// enum the properties

							newbody+="<format>MP4</format>\n";
							newbody+="<extension>mp4</extension>\n";
							newbody+="<mount>stream18</mount>\n";
							newbody+="<original>true</original>\n";
							newbody+="<status>done</status>\n";
					    	newbody+="</properties></fsxml>";
					    	
					    	System.out.println("UTER: NEWBODY="+newbody);
							String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/rawvideo/1/properties",newbody,"text/xml");
							System.out.println("UTER: RESULTD="+result);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/datasource", "euscreenxl", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
						} else {
							System.out.println("UTER: FTP FAIL="+filename);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/ingestreport", "FTP download fail.", "text/xml");
						}
					} else {
						// file is not on the ftp server
						System.out.println("UTER: FILE NOT ON FTP SERVER ID="+vnode.getId()+" FILENAME="+filename);
						Boolean res = ClusterIngester.checkOldCluster(provider, "video", vnode);
						if(!res) {
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/ingestreport", "file not found on ftp", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/hasRaws", "false", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/datasource", "euscreenxl", "text/xml");
						} else {
							System.out.println("UTER: FILE INGESTED FROM OLD CLUSTER! ID="+vnode.getId()+" FILENAME="+filename);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/datasource", "euscreen", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
						}
					}
				} else {
					System.out.println("UTER: FTPIngester: videodir is EMPTY!");
				}
				
			} else {

				String mount = rawvideonode.getProperty("mount");
				String datasource = "euscreenxl";
				if(mount!=null) {
					if(mount.contains("http://stream") || mount.contains("http://images1") || mount.contains("rtmp://") || mount.contains("rtmpe://") || mount.contains("rtp://") || mount.contains("bgdrm://") || mount.contains("drm://")) {
						datasource = "euscreen";
					}
					if(mount.equals("stream6") || mount.equals("stream18")) {
						datasource = "euscreenxl";
					}
				}
				
				String screenshot = null;
				if(datasource.equals("euscreen")) {
					screenshot = ClusterIngester.getScreenshot(filename, provider);
				} else {					
					LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/rawvideo/1/properties/extension", "mp4", "text/xml");
					screenshot = vnode.getProperty("screenshot");
					
					if(screenshot==null) {
						FsNode screensnode = Fs.getNode(path+"/screens/1");
						if(screensnode==null) {
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/rawvideo/1/properties/status", "done", "text/xml");
						} else {
							screenshot = screensnode.getProperty("uri");
							if(screenshot==null) {
								LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/screens/1/properties/redo", "true", "text/xml");
							} else {
								if(screenshot.contains("http://images")) {
									screenshot += "/h/0/m/0/sec5.jpg"; //Take the 5th second.
								} else {
									screenshot = null;
								}
							}
						}
					}
				}
				System.out.println("UTER: ALREADY GOT ID = "+vnode.getId());
				System.out.println("UTER: MOUNT = "+mount);
				if(screenshot!=null) {
					if(!screenshot.contains("/edna")) {
						screenshot = screenshot.replace("noterik.com", "noterik.com/edna");
					}
					LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/screenshot", screenshot, "text/xml");
				}
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/datasource", datasource, "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
			}
			
			//Handle series
			String seriesTitle = vnode.getProperty("TitleSet_TitleSetInOriginalLanguage_seriesOrCollectionTitle");
			if(seriesTitle!=null) {
				System.out.println("UTER:" + vnode.getId() + " is episode of series: " + seriesTitle);
				if(seriesMap.containsValue(seriesTitle)) {
					for(Iterator<FsNode> siter = series.iterator() ; siter.hasNext(); ) {
						FsNode s = (FsNode)siter.next();	
						String spath = s.getPath();
						spath = spath.replace("/series/series","/series"); // bug in FsNode in lou needs fixing
						
						FsNode snode = Fs.getNode(spath);
						String sTitle =  snode.getProperty("TitleSet_TitleSetInOriginalLanguage_seriesOrCollectionTitle");
						if(sTitle==null) continue;
						if(sTitle.equals(seriesTitle)) {
							System.out.println("UTER:" + vnode.getId() + " series found: " + seriesTitle);
							String body = "<fsxml><attributes>";
							body += "<referid>/domain/euscreenxl/user/"+provider+"/video/"+vnode.getId()+"</referid>";
							body += "</attributes></fsxml>";
							String response = LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/series/"+snode.getId()+"/video/"+vnode.getId()+"/attributes", body, "text/xml");
							System.out.println("UTER: Episode created: " + vnode.getId());
						}
					}
				}
			}
		} 
	}
	
	public static void checkProviderDoc(String provider) {
		System.out.println("UTER: FTPIngester:  check provider doc "+provider);
		String uri = "/domain/euscreenxl/user/"+provider+"/doc";
		ArrayList<String> docdir = null;
		// allways 'loads' the full result;
		FSList fslist = FSListManager.get(uri);
		System.out.println("UTER: FTPIngester: ingest list size = "+fslist.size());
		
		// now we can query the resultset;
		List<FsNode> nodes = fslist.getNodes();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {
			
			FsNode n = (FsNode)iter.next();	
			String path = n.getPath();
			path = path.replace("/doc/doc","/doc"); // bug in FsNode in lou needs fixing
			
			FsNode pnode = Fs.getNode(path);
			String filename = pnode.getProperty("filename"); // bug props should already be in the n node.
			if(filename==null) {
				continue;
			}
			
			System.out.println("UTER: FTPIngester: check for filename = "+filename);
			FsNode rawdocnode = Fs.getNode(path+"/rawdoc/1");
			System.out.println("UTER: FTPIngester: check FS: "+path+"/rawdoc/1");
			if (rawdocnode==null) {
				// we don't have a raw node
				//System.out.println("PROVIDER="+provider+" RAWNODE="+rawvideonode+" FILENAME="+filename);
				// do we have this in our ftp list ?
				if (docdir==null) docdir = getFtpList(provider,"docs");
				if (docdir!=null) {
					if (docdir.contains(filename)) {
						//System.out.println("WHOO WE HAVE THE FILE ON DISK");
						if (getFileToTemp(provider,"docs",filename)) {
							System.out.println("UTER: GOT LOCAL COPY = "+filename+" TO "+pnode.getId());
							getFileToDataStream(provider,"doc",pnode.getId(),filename);
							String ext = FilenameUtils.getExtension(filename);
							// create the rawentry
							String newbody ="<fsxml><properties>";	
							// enum the properties
							
							newbody+="<format>"+ext.toUpperCase()+"</format>\n";
							newbody+="<extension>"+ext.toLowerCase()+"</extension>\n";
							newbody+="<mount>data1</mount>\n";
							newbody+="<original>true</original>\n";
							newbody+="</properties></fsxml>";
							
							System.out.println("UTER: NEWBODY="+newbody);
							String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/rawdoc/1/properties",newbody,"text/xml");
							System.out.println("UTER: RESULTD="+result);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/datasource", "euscreenxl", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
						} else {
							System.out.println("UTER: FTP FAIL="+filename);
						}
					} else {
						// file is not on the ftp server
						System.out.println("UTER: FILE NOT ON FTP SERVER ID="+pnode.getId()+" FILENAME="+filename);
						Boolean res = ClusterIngester.checkOldCluster(provider, "pdf", pnode);
						if(!res) {
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/ingestreport", "file not found on ftp", "text/xml");
						} else {
							System.out.println("UTER: FILE INGESTED FROM OLD CLUSTER! ID="+pnode.getId()+" FILENAME="+filename);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/datasource", "euscreen", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
						}
					}
				} else {
					System.out.println("UTER: FTPIngester: docdir is EMPTY!");
				}
				
			} else {
				System.out.println("UTER: ALREADY GOT ID = "+pnode.getId());
				String mount = rawdocnode.getProperty("mount");
				System.out.println("UTER: MOUNT = "+mount);
				String datasource = "euscreenxl";
				if(mount!=null) {
					if(mount.contains("http://images1")) {
						datasource = "euscreen";
					}
					if(mount.equals("data1")) {
						datasource = "euscreenxl";
					}
				}
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/datasource", datasource, "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/doc/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
			}
		} 
		
	}
	
	public static void checkProviderPicture(String provider) {
		System.out.println("UTER: FTPIngester:  check provider picture "+provider);
		String uri = "/domain/euscreenxl/user/"+provider+"/picture";
		ArrayList<String> picturedir = null;
		// allways 'loads' the full result;
		FSList fslist = FSListManager.get(uri);
		System.out.println("UTER: FTPIngester: ingest list size = "+fslist.size());
		
		// now we can query the resultset;
		List<FsNode> nodes = fslist.getNodes();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {
			
			FsNode n = (FsNode)iter.next();	
			String path = n.getPath();
			path = path.replace("/picture/picture","/picture"); // bug in FsNode in lou needs fixing
			
			FsNode pnode = Fs.getNode(path);
			String filename = pnode.getProperty("filename"); // bug props should already be in the n node.
			if(filename==null) {
				continue;
			}
			
			System.out.println("UTER: FTPIngester: check for filename = "+filename);
			FsNode rawpicturenode = Fs.getNode(path+"/rawpicture/1");
			System.out.println("UTER: FTPIngester: check FS: "+path+"/rawpicture/1");
			if (rawpicturenode==null) {
				// we don't have a raw node
				//System.out.println("PROVIDER="+provider+" RAWNODE="+rawvideonode+" FILENAME="+filename);
				// do we have this in our ftp list ?
				if (picturedir==null) picturedir = getFtpList(provider,"pictures");
				if (picturedir!=null) {
					if (picturedir.contains(filename)) {
						//System.out.println("WHOO WE HAVE THE FILE ON DISK");
						if (getFileToTemp(provider,"pictures",filename)) {
							System.out.println("UTER: GOT LOCAL COPY = "+filename+" TO "+pnode.getId());
							getFileToPictureStream(provider,"picture",pnode.getId(),filename);
							String ext = FilenameUtils.getExtension(filename);
							// create the rawentry
							String newbody ="<fsxml><properties>";	
							// enum the properties
							
							newbody+="<format>"+ext.toUpperCase()+"</format>\n";
							newbody+="<extension>"+ext.toLowerCase()+"</extension>\n";
							newbody+="<mount>images3</mount>\n";
							newbody+="<original>true</original>\n";
							newbody+="</properties></fsxml>";
							
							System.out.println("UTER: NEWBODY="+newbody);
							String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/rawpicture/1/properties",newbody,"text/xml");
							System.out.println("UTER: RESULTD="+result);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/datasource", "euscreenxl", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
						} else {
							System.out.println("UTER: FTP FAIL="+filename);
						}
					} else {
						// file is not on the ftp server
						System.out.println("UTER: FILE NOT ON FTP SERVER ID="+pnode.getId()+" FILENAME="+filename);
						Boolean res = ClusterIngester.checkOldCluster(provider, "picture", pnode);
						if(!res) {
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/ingestreport", "file not found on ftp", "text/xml");
						} else {
							System.out.println("UTER: FILE INGESTED FROM OLD CLUSTER! ID="+pnode.getId()+" FILENAME="+filename);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/datasource", "euscreen", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
						}
					}
				} else {
					System.out.println("UTER: FTPIngester: picturedir is EMPTY!");
				}
				
			} else {
				
				System.out.println("UTER: ALREADY GOT ID = "+pnode.getId());
				String mount = rawpicturenode.getProperty("mount");
				System.out.println("UTER: MOUNT = "+mount);
				String datasource = "euscreenxl";
				if(mount!=null) {
					if(mount.contains("http://images1")) {
						datasource = "euscreen";
					}
					if(mount.equals("images3")) {
						datasource = "euscreenxl";
					}
				}
				
				String screenshot = null;
				if(datasource.equals("euscreen")) {
					screenshot = ClusterIngester.getPictureScreenshot(filename, provider);
				} else {
					screenshot = pnode.getProperty("screenshot");
					if(screenshot==null) {
						String ext = rawpicturenode.getProperty("extension");
						screenshot = "http://images3.noterik.com/domain/euscreenxl/user/"+provider+"/picture/" + pnode.getId() + "/raw." + ext;
					}
				}
				if(screenshot!=null) {
					if(!screenshot.contains("/edna")) {
						screenshot = screenshot.replace("noterik.com", "noterik.com/edna");
					}
					LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/screenshot", screenshot, "text/xml");
				}
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/hasRaws", "true", "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/datasource", datasource, "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/picture/"+pnode.getId()+"/properties/ingestreport", "done", "text/xml");
			}
		} 
		
	}
	
	public static void checkProviderAudio(String provider) {
		System.out.println("UTER: FTPIngester:  check provider audio "+provider);
		String uri = "/domain/euscreenxl/user/"+provider+"/audio";
		ArrayList<String> audiodir = null;
		// allways 'loads' the full result;
		FSList fslist = FSListManager.get(uri);
		System.out.println("UTER: FTPIngester: ingest list size = "+fslist.size());
		
		// now we can query the resultset;
		List<FsNode> nodes = fslist.getNodes();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {

			FsNode n = (FsNode)iter.next();	
			String path = n.getPath();
			path = path.replace("/audio/audio","/audio"); // bug in FsNode in lou needs fixing
			
			FsNode vnode = Fs.getNode(path);
			String filename = vnode.getProperty("filename"); // bug props should already be in the n node.
			if(filename==null) {
				continue;
			}
			
			//Check if for some reason the video node materialType is not video
			String curType = vnode.getProperty("TechnicalInformation_materialType");
			if(!curType.equalsIgnoreCase("sound")) {
				LazyHomer.sendRequest("DELETE", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId(), null, null);
				continue;
			}
		
			System.out.println("FTPIngester: check for filename = "+filename);
			FsNode rawaudionode = Fs.getNode(path+"/rawaudio/1");
			System.out.println("FTPIngester: check FS: "+path+"/rawaudio/1");
			if (rawaudionode==null) {
				// we don't have a raw node
				//System.out.println("PROVIDER="+provider+" RAWNODE="+rawvideonode+" FILENAME="+filename);
				// do we have this in our ftp list ?
				if (audiodir==null) audiodir = getFtpList(provider,"sounds");
				if (audiodir!=null) {
					if (audiodir.contains(filename)) {
						//System.out.println("WHOO WE HAVE THE FILE ON DISK");
						if (getFileToTemp(provider,"sounds",filename)) {
							System.out.println("GOT LOCAL COPY = "+filename+" TO "+vnode.getId());
							getFileToAudioStream(provider,"audio",vnode.getId(),filename);
							String ext = FilenameUtils.getExtension(filename);
							// create the rawentry
					    	String newbody ="<fsxml><properties>";	
					    	// enum the properties

							newbody+="<format>"+ext.toUpperCase()+"</format>\n";
							newbody+="<extension>"+ext.toLowerCase()+"</extension>\n";
							newbody+="<mount>audio1</mount>\n";
							newbody+="<original>true</original>\n";
					    	newbody+="</properties></fsxml>";
					    	
					    	System.out.println("NEWBODY="+newbody);
							String result = LazyHomer.sendRequest("PUT","/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/rawaudio/1/properties",newbody,"text/xml");
							System.out.println("RESULTD="+result);
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/datasource", "euscreenxl", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
						} else {
							System.out.println("FTP FAIL="+filename);
						}
					} else {
						// file is not on the ftp server
						System.out.println("FILE NOT ON FTP SERVER ID="+vnode.getId()+" FILENAME="+filename);						
						Boolean res = ClusterIngester.checkOldCluster(provider, "audio", vnode);
						if(!res) {
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/ingestreport", "file not found on ftp", "text/xml");
						} else {
							System.out.println("FILE INGESTED FROM OLD CLUSTER! ID="+vnode.getId()+" FILENAME="+filename);	
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/datasource", "euscreen", "text/xml");
							LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
						}
					}
				} else {
					System.out.println("FTPIngester: audiodir is EMPTY!");
				}
				
			} else {
				System.out.println("ALREADY GOT ID = "+vnode.getId());
				String mount = rawaudionode.getProperty("mount");
				System.out.println("MOUNT = "+mount);
				String datasource = "euscreenxl";
				if(mount!=null) {
					if(mount.contains("http://images1")) {
						datasource = "euscreen";
					}
					if(mount.equals("audio1")) {
						datasource = "euscreenxl";
					}
				}
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/hasRaws", "true", "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/datasource", datasource, "text/xml");
				LazyHomer.sendRequest("PUT", "/domain/euscreenxl/user/"+provider+"/audio/"+vnode.getId()+"/properties/ingestreport", "done", "text/xml");
			}
		} 
		
	}
	
	private static Boolean getFileToStream(String provider,String type,String id,String filename) {
		boolean ok = FtpHelper.commonsSendFile("stream18.noterik.com", "stream18", "stream18", "domain/euscreenxl/user/"+provider+"/"+type+"/"+id+"/rawvideo/1","/springfield/uter/temp/", "raw.mp4", filename, true);
		
		if(ok) { //Try to delete the TMP if it was uploaded successfully
			String path = "/springfield/uter/temp";
			File lfile = new File(path+File.separator+filename);
			if(lfile.exists()) {
				lfile.delete();
			}
		}
		return ok;
	}
	
	private static Boolean getFileToAudioStream(String provider,String type,String id,String filename) {
		String ext = FilenameUtils.getExtension(filename);
		String rfile = "raw." + ext;
		boolean ok = FtpHelper.commonsSendFile("audio1.noterik.com", "audio1", "audio1", "audio1/domain/euscreenxl/user/"+provider+"/"+type+"/"+id+"/rawaudio/1","/springfield/uter/temp/", rfile, filename, true);
		
		if(ok) { //Try to delete the TMP if it was uploaded successfully
			String path = "/springfield/uter/temp";
			File lfile = new File(path+File.separator+filename);
			if(lfile.exists()) {
				lfile.delete();
			}
		}
		return ok;
	}
	
	private static Boolean getFileToPictureStream(String provider,String type,String id,String filename) {
			String ext = FilenameUtils.getExtension(filename);
			String rfile = "raw." + ext;
			boolean ok = FtpHelper.commonsSendFile("images3.noterik.com", "images3", "images3", "images3/domain/euscreenxl/user/"+provider+"/"+type+"/"+id+"/rawpicture/1","/springfield/uter/temp/", rfile, filename, true);
			
			if(ok) { //Try to delete the TMP if it was uploaded successfully
				String path = "/springfield/uter/temp";
				File lfile = new File(path+File.separator+filename);
				if(lfile.exists()) {
					lfile.delete();
				}
			}
			return ok;
	}
	
	private static Boolean getFileToDataStream(String provider,String type,String id,String filename) {
		String ext = FilenameUtils.getExtension(filename);
		String rfile = "raw." + ext;
		boolean ok = FtpHelper.commonsSendFile("data1.noterik.com", "data1", "data1", "data1/domain/euscreenxl/user/"+provider+"/"+type+"/"+id+"/rawdoc/1","/springfield/uter/temp/", rfile, filename, true);
		
		if(ok) { //Try to delete the TMP if it was uploaded successfully
			String path = "/springfield/uter/temp";
			File lfile = new File(path+File.separator+filename);
			if(lfile.exists()) {
				lfile.delete();
			}
		}
		return ok;
	}
	
	
	private static Boolean getFileToTemp(String provider,String type,String filename) {
		String ftppath = provider+"/"+type+"/";
		String path = "/springfield/uter/temp/";
		File tempDir = new File(path);
		if(!tempDir.exists()) {
			tempDir.mkdirs();
		}
		boolean success = FtpHelper.commonsGetFile("ftp.noterik.com", "euscreenfetcher","euscreenfetcher", ftppath, path, filename, filename+".downloading");
		if (success) {
			File lfile = new File(path+File.separator+filename+".downloading");
			if (lfile.exists()) {
				lfile.renameTo(new File(path+File.separator+filename));
				return true;
			}
		} 
		return false;
	}
	
	
	
	private static ArrayList<String> getFtpList(String provider,String path) {
		ArrayList results = new ArrayList<String>();
		try {
			FTPClient client = new FTPClient();
			client.connect("ftp.noterik.com");
			boolean loggedin = client.login("euscreenfetcher","euscreenfetcher");
			if (!loggedin) { 
				System.out.println("FtpIngester: can not login ftp "); 
				return null;
			}
			client.enterLocalPassiveMode();
			String ftppath = provider+"/"+path;
			if (ftppath!=null) client.changeWorkingDirectory(ftppath);
			// loop through remote folder
			FTPListParseEngine engine = client.initiateListParsing();
			// loop through files
			boolean done = false;
			while(engine.hasNext() && !done) {
				FTPFile[] files = engine.getNext(10);
				for(FTPFile file : files) {
					if(!file.isDirectory()) {
						String filename = file.getName();
						results.add(filename);
						//System.out.println("FILENAME="+filename);
					}
				}
			}
					  
		} catch(Exception e) {
			
		}
		return results;
	}
}