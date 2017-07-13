/* 
* Video.java
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

package org.springfield.uter.dans.xml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.springfield.uter.dans.xml.ActionFS;
import org.springfield.uter.dans.xml.UriParser;
import org.springfield.uter.dans.xml.VideoPlaylist.PlayoutMode;
import org.springfield.uter.homer.LazyHomer;
import org.springfield.uter.homer.MountProperties;

public class Video {
    private static Logger LOG = Logger.getLogger(Video.class);
    private Node video;
    private String target;
    private String refertarget;
    private String method;
    private boolean valid = false;
    private String uri;
    private String src;
    private String videoTarget;
    private String subtitle;
    private boolean requireTicket = true;
    private int starttime;
    private int duration = -1;
    private PlayoutMode playMode;
    private String title;
    private Subtitle[] subtitles;
    
    enum State { Number, TimeStamp, Text };
    enum SubtitleType { SRT, WEBVTT };

    public Video(Node video, String target, String method, PlayoutMode playMode) {
        LOG.setLevel(Level.DEBUG);
        this.video = video;
        this.target = target;
        this.method = method;
        this.playMode = playMode;
    }

    public void validate() {
        String response;
        String domain = UriParser.getDomainIdFromUri(this.target);
        String user = UriParser.getUserIdFromUri(this.target);
        String presentation = UriParser.getPresentationIdFromUri(this.target);
        if (domain == null) {
            LOG.error((Object)"Target for video not as expected, domain missing - "+this.target);
            this.valid = false;
            return;
        }
        if (user == null) {
            LOG.error((Object)"Target for video not as expected, user missing - "+this.target);
            this.valid = false;
            return;
        }
        if (presentation == null) {
            LOG.error("Target for video not as expected, presentation missing - "+this.target);
            this.valid = false;
            return;
        }
        this.src = this.video.selectSingleNode("@src") == null ? null : this.video.selectSingleNode("@src").getText();
        this.videoTarget = this.video.selectSingleNode("@target") == null ? null : this.video.selectSingleNode("@target").getText();
        this.starttime = this.video.selectSingleNode("@start-time") == null ? 0 : Integer.parseInt(this.video.selectSingleNode("@start-time").getText());
        int endtime = this.video.selectSingleNode("@end-time") == null ? -1 : Integer.parseInt(this.video.selectSingleNode("@end-time").getText());
        this.subtitle = this.video.selectSingleNode("@subtitles") == null ? this.subtitle : this.video.selectSingleNode("@subtitles").getText();
        this.title = this.video.selectSingleNode("@title") == null ? null : this.video.selectSingleNode("@title").getText();
        
        List<Node> childs = this.video.selectNodes("*");
        this.subtitles = new Subtitle[childs.size()];
        int i = 0;
         
        for (Node child : childs) {
            String language = child.selectSingleNode("@xml:lang") == null ? null : child.selectSingleNode("@xml:lang").getText();
            String src =  child.selectSingleNode("@src") == null ? null : child.selectSingleNode("@src").getText();
            
            if (language == null || src == null) {
        	LOG.error("Subtitle node found without required language or source");
        	this.valid = false;
        	return;
            }
            
            File subtitles = new File("/springfield/inbox/"+src);
            if (!subtitles.exists()) {
        	LOG.info("Source for subtitle does not yet exist - "+src);
                this.valid = false;
                return;
            }
            
            Subtitle sub = new Subtitle(language, src);
            this.subtitles[i] = sub;
            i++;
        }
        
        if (this.src == null) {
            LOG.error("No src defined for video");
            this.valid = false;
            return;
        }
        File source = new File("/springfield/inbox/" + this.src);
        if (!source.exists()) {
            LOG.info("Source for video does not yet exist - "+this.src);
            this.valid = false;
            return;
        }
        
        if (this.subtitle != null) {
            File subtitles = new File("/springfield/inbox/"+this.subtitle);
            if (!subtitles.exists()) {
        	LOG.info("Source for subtitle does not yet exist - "+this.subtitle);
                this.valid = false;
                return;
            }
        }
        
        if (this.videoTarget == null) {
            LOG.error("No target defined for video - "+this.videoTarget);
            this.valid = false;
            return;
        }
        
        if (this.playMode == PlayoutMode.menu) {
            if (title == null || title.length() <= 0) {
        	LOG.error("Title mandatory for video in playlist mode menu");
        	this.valid = false;
                return;
            }
        }
        
        if (endtime != -1) {
            this.duration = endtime - this.starttime;
        }
        
        this.target = "/domain/" + domain + "/user/" + user + "/presentation/" + presentation + "/videoplaylist/1/video/" + this.videoTarget;
        this.refertarget = "/domain/" + domain + "/user/" + user + "/video";
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                Document doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error((Object)"Cannot create video that's already in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error((Object)"Could not parse response for FS request - "+this.target);
                this.valid = false;
                return;
            }
            ActionFS.instance().addToFS(this.target);
        } else if (this.method.equals("delete")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                Document doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error((Object)"Cannot delete presentation that is not in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error((Object)"Could not parse response for FS request - "+this.target);
                this.valid = false;
                return;
            }
            ActionFS.instance().deleteFromFS(this.target);
        }
        File videosrc = this.src.indexOf("/") != 0 ? new File("/springfield/inbox/" + this.src) : new File(this.src);
        if (!videosrc.isFile()) {
            LOG.error((Object)("Video " + videosrc.getPath() + " is not a file"));
            this.valid = false;
            return;
        }
        this.valid = true;
    }

    public String getId() {
        return this.videoTarget;
    }

    public String getIdentifier() {
        return this.uri;
    }

    public int getStartTime() {
        return this.starttime;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setRequireTicket(boolean requireTicket) {
        this.requireTicket = requireTicket;
    }

    public void setSubtitles(String subtitles) {
        this.subtitle = subtitles;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void process() {
	if (this.method.equals("add")) {
            MountProperties mp = LazyHomer.getMountProperties("dans");
            String path = mp.getPath();
            String extension = this.src.substring(this.src.lastIndexOf(".") + 1);
            String fsxml = "<fsxml><properties><private>"+String.valueOf(this.requireTicket)+"</private>";
            SubtitleType sType = SubtitleType.SRT;            
            
            if (this.subtitle != null) {        	
        	File subs = new File("/springfield/inbox/" + this.subtitle);
            	 
            	String fileExtension = FilenameUtils.getExtension(subs.getName());
            	 
            	if (fileExtension.toLowerCase().equals("srt")) {
            	    sType = SubtitleType.SRT;
            	} else if (fileExtension.toLowerCase().equals("vtt")) {
            	    sType = SubtitleType.WEBVTT;
            	}      	    
            	 
            	 if (subs.exists()) {
            	     if (sType == SubtitleType.SRT) {
            		 fsxml += "<srt>"+subs.getName()+"</srt>";
            	     } else if (sType == SubtitleType.WEBVTT) {
            		 fsxml += "<webvtt>"+subs.getName()+"</webvtt>";
            	     }
            	 }
            } else if (this.subtitles.length > 0) {
		for (Subtitle sub : this.subtitles) {
		    File subs = new File("/springfield/inbox/" + sub.getPath());

		    String fileExtension = FilenameUtils.getExtension(subs.getName());

		    if (fileExtension.toLowerCase().equals("srt")) {
			sType = SubtitleType.SRT;
		    } else if (fileExtension.toLowerCase().equals("vtt")) {
			sType = SubtitleType.WEBVTT;
		    }

		    if (subs.exists()) {
			if (sType == SubtitleType.SRT) {
			    fsxml += "<srt_"+sub.getLanguage()+">" + sub.getLanguage() +"_"+ subs.getName() + "</srt_"+sub.getLanguage()+">";
			} else if (sType == SubtitleType.WEBVTT) {
			    fsxml += "<webvtt_"+sub.getLanguage()+">"+ sub.getLanguage() +"_" + subs.getName() + "</webvtt_"+sub.getLanguage()+">";
			}
		    }
		}
            }
            
            fsxml += "</properties></fsxml>";
            String uri = this.refertarget;
            String response = LazyHomer.sendRequest("POST", uri, fsxml, "text/xml");
            String referid = "";
            try {
                Document doc = DocumentHelper.parseText((String)response);
                referid = doc.selectSingleNode("//properties/uri") == null ? "" : doc.selectSingleNode("//properties/uri").getText();
            }
            catch (DocumentException e) {
                LOG.error("Error with inserting video in FS");
                return;
            }
            File source = new File("/springfield/inbox/" + this.src);
            if (source.exists()) {
                LOG.debug("source file exists");
                String checkDir = path + referid + "/rawvideo/1";
                File folderPath = new File(checkDir);
                LOG.debug("About to move file to " + folderPath.getAbsolutePath());
                if (folderPath.exists() || folderPath.mkdirs()) {
                    LOG.debug("moving video to " + folderPath.getAbsolutePath() + "/"+ source.getName());
                    File dest = new File(folderPath + "/" + source.getName());
                    try {
                        FileUtils.moveFile(source, dest);
                    }
                    catch (IOException e) {
                        LOG.error("Could not move video from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
                        LOG.error(e.toString());
                    }
                    if (dest.exists()) {
                        LOG.info("Successfully moved video to " + dest.getAbsolutePath());
                    } else {
                        LOG.error("Failed video processing");
                    }
                }
            } else {
                LOG.error("Source file does not exist: " + source.getAbsolutePath());
            }
            
            if (this.subtitle != null) {
        	 File subtitles = new File("/springfield/inbox/" + this.subtitle);
                 if (subtitles.exists()) {
                     LOG.debug("subtitle file exists");
                     String checkDir = path + referid;
                     File folderPath = new File(checkDir);
                     LOG.debug("About to move file to " + folderPath.getAbsolutePath());
                     if (folderPath.exists() || folderPath.mkdirs()) {	                	 
                         File dest = new File(folderPath + "/" + FilenameUtils.getBaseName(subtitles.getName())+".vtt");
                         
                         if (sType == SubtitleType.SRT) {
                             LOG.debug("Converting subtitle to VTT ");
                             
                             try {
                                 Srt2Vtt(subtitles, dest);
                                 uri = this.refertarget;
                                 response = LazyHomer.sendRequest("PUT", referid+"/properties/webvtt", FilenameUtils.getBaseName(subtitles.getName())+".vtt", "text/xml");
                                 LOG.info("Adding webtvtt: "+response);
                             }
                             catch (IOException e) {
                                 LOG.error("Could not convert subtitle from " + subtitles.getAbsolutePath() + " to " + dest.getAbsolutePath());
                                 LOG.error(e.toString());
                             }
                         }
                         
                         LOG.debug("moving subtitle to " + folderPath.getAbsolutePath() + "/"+ subtitles.getName());
                         File dest1 = new File(folderPath + "/" + subtitles.getName());
                         try {
                            FileUtils.moveFile(subtitles, dest1);
                         }
                         catch (IOException e) {
                             LOG.error("Could not move subtitle from " + subtitles.getAbsolutePath() + " to " + dest1.getAbsolutePath());
                             LOG.error(e.toString());
                         }
                         
                         if (dest.exists() && dest1.exists()) {
                             if (sType == SubtitleType.SRT) {
                        	 LOG.info("Sucessfully converted subtitle to vtt "+dest.getAbsolutePath());
                             }
                             LOG.info("Successfully moved subtitle to " + dest1.getAbsolutePath());
                         } else {
                             LOG.error("Failed video processing");
                         }
                     }
                 }  else {
                     LOG.error("Subtitles file does not exist: " + subtitles.getAbsolutePath());
                 }
            } else if (this.subtitles.length > 0) {
        	for (Subtitle sub : this.subtitles) {
        	    File subtitles = new File("/springfield/inbox/" + sub.getPath());
                    if (subtitles.exists()) {
                        LOG.debug("subtitle file exists");
                        String checkDir = path + referid;
                        File folderPath = new File(checkDir);
                        LOG.debug("About to move file to " + folderPath.getAbsolutePath());
                        if (folderPath.exists() || folderPath.mkdirs()) {	                	 
                            File dest = new File(folderPath + "/" + sub.getLanguage() +"_"+ FilenameUtils.getBaseName(subtitles.getName())+".vtt");
                            
                            if (sType == SubtitleType.SRT) {
                                LOG.debug("Converting subtitle to VTT ");
                                
                                try {
                                    Srt2Vtt(subtitles, dest);
                                    uri = this.refertarget;
                                    response = LazyHomer.sendRequest("PUT", referid+"/properties/webvtt_"+sub.getLanguage(), sub.getLanguage() +"_"+ FilenameUtils.getBaseName(subtitles.getName())+".vtt", "text/xml");
                                    LOG.info("Adding webtvtt: "+response);
                                }
                                catch (IOException e) {
                                    LOG.error("Could not convert subtitle from " + subtitles.getAbsolutePath() + " to " + dest.getAbsolutePath());
                                    LOG.error(e.toString());
                                }
                            }
                            
                            LOG.debug("moving subtitle to " + folderPath.getAbsolutePath() +"/"+ sub.getLanguage() +"_"+ subtitles.getName());
                            File dest1 = new File(folderPath + "/"+ sub.getLanguage() +"_"+ subtitles.getName());
                            try {
                               FileUtils.moveFile(subtitles, dest1);
                            }
                            catch (IOException e) {
                                LOG.error("Could not move subtitle from " + subtitles.getAbsolutePath() + " to " + dest1.getAbsolutePath());
                                LOG.error(e.toString());
                            }
                            
                            if (dest.exists() && dest1.exists()) {
                                if (sType == SubtitleType.SRT) {
                           	 LOG.info("Sucessfully converted subtitle to vtt "+dest.getAbsolutePath());
                                }
                                LOG.info("Successfully moved subtitle to " + dest1.getAbsolutePath());
                            } else {
                                LOG.error("Failed video processing");
                            }
                        }
                    }  else {
                        LOG.error("Subtitles file does not exist: " + subtitles.getAbsolutePath());
                    }
        	}
            }
            
            if (this.playMode == PlayoutMode.menu) {
        	response = LazyHomer.sendRequest("PUT", referid+"/properties/title", this.title, "text/xml");
                LOG.info("Adding title to video: "+response);
            }
            
            fsxml = "<fsxml><properties><original>true</original><format>unknown</format><extension>" + extension + "</extension>" + 
            		"<mount>dans</mount>" + 
            		"<status>done</status>" + 
            		"<filename>"+source.getName()+"</filename>" +
            		"</properties></fsxml>";
            LazyHomer.sendRequest("PUT", referid + "/rawvideo/1/properties", fsxml, "text/xml");
            fsxml = "<fsxml><attributes><referid>" + referid + "</referid></attributes></fsxml>";
            uri = this.target + "/attributes";
            response = LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            StringBuffer properties = new StringBuffer();
            if (this.starttime != 0) {
                properties.append("<start>" + this.starttime + "</start>");
            }
            if (this.duration != -1) {
                properties.append("<duration>" + this.duration + "</duration>");
            }
            if (properties.length() != 0) {
                fsxml = "<fsxml><properties>" + properties.toString() + "</properties></fsxml>";
                uri = this.target + "/properties";
                LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
    
    //taken from code example at http://codegists.com/snippet/java/srt2vttjava_rhulha_java
    public void Srt2Vtt(File srt, File vttFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(srt));
        PrintWriter vtt = new PrintWriter(vttFile);
        String line;
        vtt.write("WEBVTT\n\n");
        State state=State.Number;
         
        while((line = br.readLine())!=null){
            switch(state) {
                case Number:
                    state=State.TimeStamp;
                    break;
                case TimeStamp:
                    vtt.write(line.replace(',', '.')+"\n");
                    state=State.Text;
                    break;
                case Text:
                    vtt.write(line+"\n");
                    if( line.length()==0)
                        state=State.Number;
                    break;
            }
        }
        vtt.close();
        br.close();
    }
}

