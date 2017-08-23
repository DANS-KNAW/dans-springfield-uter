/* 
* VideoPlaylist.java
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

import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.springfield.uter.dans.xml.ActionFS;
import org.springfield.uter.dans.xml.UriParser;
import org.springfield.uter.dans.xml.Video;
import org.springfield.uter.homer.LazyHomer;

public class VideoPlaylist {
    private static Logger LOG = Logger.getLogger(VideoPlaylist.class);
    
    enum PlayoutMode {continuous, menu};
    
    private Node videoplaylist;
    private String target;
    private String method;
    private boolean valid = false;
    private boolean requireTicket = true;
    private PlayoutMode playMode = PlayoutMode.continuous;

    public VideoPlaylist(Node videoplaylist, String target, String method) {
        LOG.setLevel(Level.DEBUG);
        this.videoplaylist = videoplaylist;
        this.target = target;
        this.method = method;
    }

    public void validate() {
        Document doc;
        String domain = UriParser.getDomainIdFromUri(this.target);
        String user = UriParser.getUserIdFromUri(this.target);
        String collection = UriParser.getCollectionIdFromUri(this.target);
        String presentation = UriParser.getPresentationIdFromUri(this.target);
        if (domain == null) {
            LOG.error("Target for video playlist not as expected, domain missing");
            this.valid = false;
            return;
        }
        if (user == null) {
            LOG.error("Target for video playlist not as expected, user missing");
            this.valid = false;
            return;
        }
        if (collection == null) {
            LOG.error("Target for video playlist not as expected, collection missing");
            this.valid = false;
            return;
        }
        if (presentation == null) {
            LOG.error("Target for video playlist not as expected, presentation missing");
            this.valid = false;
            return;
        }
       
        this.requireTicket = this.videoplaylist.selectSingleNode("@require-ticket") == null ? true : Boolean.parseBoolean(this.videoplaylist.selectSingleNode("@require-ticket").getText());
        String pMode = this.videoplaylist.selectSingleNode("@play-mode") == null ? null : this.videoplaylist.selectSingleNode("@play-mode").getText();
        if (pMode != null && pMode.toLowerCase().equals("menu")) {
            this.playMode = PlayoutMode.menu;
        }
        
        String cpUri = "/domain/" + domain + "/user/" + user + "/collection/" + collection + "/presentation/" + presentation;
        String response = LazyHomer.sendRequest("GET", cpUri, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
        try {
            doc = DocumentHelper.parseText((String)response);
            if (doc.selectSingleNode("@referid") != null) {
                this.target = doc.selectSingleNode("@referid").getText();
            }
        }
        catch (DocumentException e) {
            LOG.error("Could not parse response for FS request");
            this.valid = false;
            return;
        }
        this.target = this.target + "/videoplaylist/1";
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create video playlist that's already in FS");
                    LOG.info("is added ? "+ActionFS.instance().isAddedToFS(this.target));
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request");
                this.valid = false;
                return;
            }
            ActionFS.instance().addToFS(this.target);
        } else if (this.method.equals("delete")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot delete video playlist that is not in FS");
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request");
                this.valid = false;
                return;
            }
            ActionFS.instance().deleteFromFS(this.target);
        }
        List<Node> childs = this.videoplaylist.selectNodes("*");
        for (Node child : childs) {
            if (child.getName().equals("video")) {
                Video video = new Video(child, this.target, this.method, this.playMode);
                video.setRequireTicket(this.requireTicket);
                video.validate();
                if (video.isValid()) continue;
                LOG.error("Video not valid");
                this.valid = false;
                return;
            } else if (child.getName().equals("audio")) {
        	Audio audio = new Audio(child, this.target, this.method, this.playMode);
                audio.setRequireTicket(this.requireTicket);
                audio.validate();
                if (audio.isValid()) continue;
                LOG.error("Audio not valid");
                this.valid = false;
                return;
            }
            LOG.error("Found child " + child.getName() + " in video playlist node, this is not allowed");
            this.valid = false;
            return;
        }
        this.valid = true;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void process() {
        if (this.method.equals("add")) {
            String mode = playMode == PlayoutMode.menu ? "menu" : "continuous";
            
            String fsxml = "<fsxml><properties><play-mode>"+mode+"</play-mode></properties></fsxml>";
            String domain = UriParser.getDomainIdFromUri(this.target);
            String user = UriParser.getUserIdFromUri(this.target);
            String collection = UriParser.getCollectionIdFromUri(this.target);
            String presentation = UriParser.getPresentationIdFromUri(this.target);
            if (collection != null) {
                String cpUri = "/domain/" + domain + "/user/" + user + "/collection/" + collection + "/presentation/" + presentation;
                String response = LazyHomer.sendRequest("GET", cpUri, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
                try {
                    Document doc = DocumentHelper.parseText((String)response);
                    this.target = doc.selectSingleNode("//@referid") == null ? this.target : doc.selectSingleNode("//@referid").getText();
                    this.target = this.target + "/videoplaylist/1";
                }
                catch (DocumentException e) {
                    LOG.error("Could not parse response for FS request");
                    this.valid = false;
                    return;
                }
            }
            String uri = this.target + "/properties";
            LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            List<Node> childs = this.videoplaylist.selectNodes("*");
            for (Node child : childs) {
        	if (child.getName().equals("video")) {
        	    Video video = new Video(child, this.target, this.method, this.playMode);
                    video.setRequireTicket(this.requireTicket);
                    video.validate();
                    video.process();
        	} else if (child.getName().equals("audio")) {
        	    Audio audio = new Audio(child, this.target, this.method, this.playMode);
                    audio.setRequireTicket(this.requireTicket);
                    audio.validate();
                    audio.process();
        	} else {
        	    continue;
        	}   
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
}

