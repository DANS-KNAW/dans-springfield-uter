/* 
* Audio.java
* 
* Copyright (c) 2017 Noterik B.V.
* 
* This file is part of uter, related to the Noterik Springfield project.
*
* uter is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* uter is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with uter.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.springfield.uter.dans.xml;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.springfield.uter.dans.xml.VideoPlaylist.PlayoutMode;
import org.springfield.uter.homer.LazyHomer;
import org.springfield.uter.homer.MountProperties;

/**
 * Audio.java
 *
 * @author Pieter van Leeuwen
 * @copyright Copyright: Noterik B.V. 2017
 * @package org.springfield.uter.dans.xml
 * 
 */
public class Audio {
    private static Logger LOG = Logger.getLogger(Audio.class);
    private Node audio;
    private String target;
    private String refertarget;
    private String method;
    private boolean valid = false;
    private String uri;
    private String src;
    private String audioTarget;
    private boolean requireTicket = true;
    private int starttime;
    private int duration = -1;
    private PlayoutMode playMode;
    private String title;
    
    public Audio(Node audio, String target, String method, PlayoutMode playMode) {
        this.audio = audio;
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
            LOG.error((Object)"Target for audio not as expected, domain missing - "+this.target);
            this.valid = false;
            return;
        }
        if (user == null) {
            LOG.error((Object)"Target for audio not as expected, user missing - "+this.target);
            this.valid = false;
            return;
        }
        if (presentation == null) {
            LOG.error("Target for audio not as expected, presentation missing - "+this.target);
            this.valid = false;
            return;
        }
        this.src = this.audio.selectSingleNode("@src") == null ? null : this.audio.selectSingleNode("@src").getText();
        this.audioTarget = this.audio.selectSingleNode("@target") == null ? null : this.audio.selectSingleNode("@target").getText();
        this.starttime = this.audio.selectSingleNode("@start-time") == null ? 0 : Integer.parseInt(this.audio.selectSingleNode("@start-time").getText());
        int endtime = this.audio.selectSingleNode("@end-time") == null ? -1 : Integer.parseInt(this.audio.selectSingleNode("@end-time").getText());
        this.title = this.audio.selectSingleNode("@title") == null ? null : this.audio.selectSingleNode("@title").getText();
        
        if (this.src == null) {
            LOG.error("No src defined for audio");
            this.valid = false;
            return;
        }
        File source = new File("/springfield/inbox/" + this.src);
        if (!source.exists()) {
            LOG.info("Source for audio does not yet exist - "+this.src);
            this.valid = false;
            return;
        }
        
        if (this.audioTarget == null) {
            LOG.error("No target defined for audio - "+this.audioTarget);
            this.valid = false;
            return;
        }
        
        if (this.playMode == PlayoutMode.menu) {
            if (title == null || title.length() <= 0) {
        	LOG.error("Title mandatory for audio in playlist mode menu");
        	this.valid = false;
                return;
            }
        }
        
        if (endtime != -1) {
            this.duration = endtime - this.starttime;
        }
        
        this.target = "/domain/" + domain + "/user/" + user + "/presentation/" + presentation + "/videoplaylist/1/audio/" + this.audioTarget;
        this.refertarget = "/domain/" + domain + "/user/" + user + "/audio";
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                Document doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error((Object)"Cannot create audio that's already in FS - "+this.target);
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
        File audiosrc = this.src.indexOf("/") != 0 ? new File("/springfield/inbox/" + this.src) : new File(this.src);
        if (!audiosrc.isFile()) {
            LOG.error((Object)("Audio " + audiosrc.getPath() + " is not a file"));
            this.valid = false;
            return;
        }
        this.valid = true;
    }

    public String getId() {
        return this.audioTarget;
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

    public boolean isValid() {
        return this.valid;
    }

    public void process() {
        if (this.method.equals("add")) {
            MountProperties mp = LazyHomer.getMountProperties("dans");
            String path = mp.getPath();
            String extension = this.src.substring(this.src.lastIndexOf(".") + 1);
            String fsxml = "<fsxml><properties><private>"+String.valueOf(this.requireTicket)+"</private>";
            
            fsxml += "</properties></fsxml>";
            String uri = this.refertarget;
            String response = LazyHomer.sendRequest("POST", uri, fsxml, "text/xml");
            String referid = "";
            try {
                Document doc = DocumentHelper.parseText((String)response);
                referid = doc.selectSingleNode("//properties/uri") == null ? "" : doc.selectSingleNode("//properties/uri").getText();
            }
            catch (DocumentException e) {
                LOG.error("Error with inserting audio in FS");
                return;
            }
            File source = new File("/springfield/inbox/" + this.src);
            if (source.exists()) {
                LOG.debug("source file exists :-)");
                String checkDir = path + referid + "/rawaudio/1";
                File folderPath = new File(checkDir);
                LOG.debug("About to move file to " + folderPath.getAbsolutePath());
                if (folderPath.exists() || folderPath.mkdirs()) {
                    LOG.debug("moving audio to " + folderPath.getAbsolutePath() + "/"+ source.getName());
                    File dest = new File(folderPath + "/" + source.getName());
                    try {
                        FileUtils.moveFile(source, dest);
                    }
                    catch (IOException e) {
                        LOG.error("Could not move audio from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
                        LOG.error(e.toString());
                    }
                    if (dest.exists()) {
                        LOG.info("Successfully moved audio to " + dest.getAbsolutePath());
                    } else {
                        LOG.error("Failed audio processing");
                    }
                }
            } else {
                LOG.error("Source file does not exist: " + source.getAbsolutePath());
            }           
            
            if (this.playMode == PlayoutMode.menu) {
        	response = LazyHomer.sendRequest("PUT", referid+"/properties/title", this.title, "text/xml");
                LOG.info("Adding title to audio: "+response);
            }
            
            fsxml = "<fsxml><properties><original>true</original><format>unknown</format><extension>" + extension + "</extension>" + 
            		"<mount>dans</mount>" + 
            		"<status>done</status>" + 
            		"<filename>"+source.getName()+"</filename>" +
            		"</properties></fsxml>";
            LazyHomer.sendRequest("PUT", referid + "/rawaudio/1/properties", fsxml, "text/xml");
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
}
