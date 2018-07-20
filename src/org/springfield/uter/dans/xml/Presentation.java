/* 
* Presentation.java
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
import org.springfield.uter.dans.xml.VideoPlaylist;
import org.springfield.uter.homer.LazyHomer;

public class Presentation {
    private static Logger LOG = Logger.getLogger(Presentation.class);
    private Node presentation;
    private String target;
    private String refertarget;
    private String method;
    private boolean valid = false;
    private String name;
    private String title;
    private String description;

    public Presentation(Node presentation, String target, String method) {
        this.presentation = presentation;
        this.target = target;
        this.method = method;
    }

    public void validate() {
        LOG.debug("Calling Presentation.validate()");
        String response;
        Document doc;
        String domain = UriParser.getDomainIdFromUri(this.target);
        String user = UriParser.getUserIdFromUri(this.target);
        String collection = UriParser.getCollectionIdFromUri(this.target);

        LOG.debug("domain = " + domain + ", user = " + user + ", collection = " + collection);
        if (domain == null) {
            LOG.error("Target for presentation not as expected, domain missing");
            this.valid = false;
            return;
        }
        if (user == null) {
            LOG.error("Target for presentation not as expected, user missing");
            this.valid = false;
            return;
        }
        if (collection == null) {
            LOG.error("Target for presentation not as expected, collection missing");
            this.valid = false;
            return;
        }
        this.name = this.presentation.selectSingleNode("@name") == null ? null : this.presentation.selectSingleNode("@name").getText();
        LOG.debug("name = " + name);
        this.title = this.presentation.selectSingleNode("title") == null ? "" : this.presentation.selectSingleNode("title").getText();
        LOG.debug("title = " + title);
        this.description = this.presentation.selectSingleNode("description") == null ? "" : this.presentation.selectSingleNode("description").getText();
        LOG.debug("description = " + description);
        if (this.name == null) {
            LOG.error("No name defined for presentation");
            this.valid = false;
            return;
        }
        this.refertarget = "/domain/" + domain + "/user/" + user + "/presentation";
        LOG.debug("refertarget = " + refertarget);
        LOG.debug("method = " + method);
        if (this.method.equals("add")) {
            LOG.debug("Requesting properties for " + this.target);
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            LOG.debug("Received response: START>>>" + response + "<<<END");
            try {
                doc = DocumentHelper.parseText(response);
                // If there is an error it probably means the presentation is not yet in the FS, which is good, unless we ar
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create presentation as no parent collection was created in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request - "+this.target);
                this.valid = false;
                return;
            }
            this.target = this.target + "/presentation/" + this.name;
            LOG.debug("Requesting properties for " + this.target);
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            LOG.debug("Received response: START>>>" + response + "<<<END");
            try {
                doc = DocumentHelper.parseText(response);
                // If there is no error it means that the presentation is already present
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create presentation that's already in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request - "+this.target+"/presentation/"+this.name);
                this.valid = false;
                return;
            }
            ActionFS.instance().addToFS(this.target);
        } else if (this.method.equals("delete")) {
            //TODO: deletes collection rather then presentation?        	
        	response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot delete presentation that is not in FS - "+this.target);
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
        List<Node> childs = this.presentation.selectNodes("*");
        for (Node child : childs) {
            if (child.getName().equals("video-playlist")) {
                VideoPlaylist playlist = new VideoPlaylist(child, this.target, this.method);
                playlist.validate();
                if (playlist.isValid()) continue;
                LOG.error("Video playlist not valid");
                this.valid = false;
                return;
            }
            if (child.getName().equals("title") || child.getName().equals("description")) continue;
            LOG.error("Found child " + child.getName() + " in presentation node, this is not allowed");
            this.valid = false;
            return;
        }
        this.valid = true;
    }

    public String getId() {
        return this.name;
    }

    public String getReferId() {
        return this.refertarget;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void process() {
        if (this.method.equals("add")) {
            String fsxml = "<fsxml><properties><title></title>" + this.title + "<description>" + this.description + "</description></properties></fsxml>";
            String uri = this.refertarget;
            String response = LazyHomer.sendRequest("POST", uri, fsxml, "text/xml");
            String referid = "";
            try {
                Document doc = DocumentHelper.parseText((String)response);
                referid = doc.selectSingleNode("//properties/uri") == null ? "" : doc.selectSingleNode("//properties/uri").getText();
            }
            catch (DocumentException e) {
                LOG.error("Error with inserting presentation in FS");
                return;
            }
            fsxml = "<fsxml><attributes><referid>" + referid + "</referid></attributes></fsxml>";
            uri = this.target + "/attributes";
            LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            List<Node> childs = this.presentation.selectNodes("*");
            for (Node child : childs) {
                if (!child.getName().equals("video-playlist")) continue;
                VideoPlaylist videoplaylist = new VideoPlaylist(child, this.target, this.method);
                videoplaylist.validate();
                videoplaylist.process();
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
}

