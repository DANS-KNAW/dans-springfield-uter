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
import java.io.Reader;
import java.io.Writer;

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
    private String subtitles;
    private boolean requireTicket = true;
    private int starttime;
    private int duration = -1;
    
    enum State { Number, TimeStamp, Text };

    public Video(Node video, String target, String method) {
        LOG.setLevel(Level.DEBUG);
        this.video = video;
        this.target = target;
        this.method = method;
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
        this.subtitles = this.video.selectSingleNode("@subtitles") == null ? this.subtitles : this.video.selectSingleNode("@subtitles").getText();

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
        
        if (this.subtitles != null) {
            File subtitles = new File("/springfield/inbox/"+this.subtitles);
            if (!subtitles.exists()) {
        	LOG.info("Source for subtitle does not yet exist - "+this.subtitles);
                this.valid = false;
                return;
            }
        }
        
        if (this.videoTarget == null) {
            LOG.error("No target defined for video - "+this.videoTarget);
            this.valid = false;
            return;
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
        this.subtitles = subtitles;
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
            
            if (this.subtitles != null) {        	
            	 File subs = new File("/springfield/inbox/" + this.subtitles);
            	 if (subs.exists()) {
            	     fsxml += "<srt>"+subs.getName()+"</srt>";
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
                LOG.debug("source file exists :-)");
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
            
            if (this.subtitles != null) {
        	 File subtitles = new File("/springfield/inbox/" + this.subtitles);
                 if (subtitles.exists()) {
                     LOG.debug("subtitles file exists :-)");
                     String checkDir = path + referid;
                     File folderPath = new File(checkDir);
                     LOG.debug("About to move file to " + folderPath.getAbsolutePath());
                     if (folderPath.exists() || folderPath.mkdirs()) {
                	 LOG.debug("Converting subtitles to VTT ");
                         File dest = new File(folderPath + "/" + FilenameUtils.getBaseName(subtitles.getName())+".vtt");
                         try {
                             Srt2Vtt(subtitles, dest);   
                         }
                         catch (IOException e) {
                             LOG.error("Could not convert subtitles from " + subtitles.getAbsolutePath() + " to " + dest.getAbsolutePath());
                             LOG.error(e.toString());
                         }
                         
                         LOG.debug("moving subtitles to " + folderPath.getAbsolutePath() + "/"+ subtitles.getName());
                         File dest1 = new File(folderPath + "/" + subtitles.getName());
                         try {
                            FileUtils.moveFile(subtitles, dest1);
                         }
                         catch (IOException e) {
                             LOG.error("Could not move subtitles from " + subtitles.getAbsolutePath() + " to " + dest1.getAbsolutePath());
                             LOG.error(e.toString());
                         }
                         
                         if (dest.exists() && dest1.exists()) {
                             LOG.info("Sucessfully converted subtitles to vtt "+dest.getAbsolutePath());
                             LOG.info("Successfully moved subtitles to " + dest1.getAbsolutePath());
                         } else {
                             LOG.error("Failed video processing");
                         }
                     }
                 }  else {
                     LOG.error("Subtitles file does not exist: " + subtitles.getAbsolutePath());
                 }
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

