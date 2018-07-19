/* 
* Collection.java
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
import org.springfield.uter.dans.xml.Presentation;
import org.springfield.uter.dans.xml.UriParser;
import org.springfield.uter.homer.LazyHomer;

public class Collection {
    private static Logger LOG = Logger.getLogger(Collection.class);
    private Node collection;
    private String target;
    private String method;
    private String name;
    private String title;
    private String description;
    private Presentation[] presentations;
    private boolean valid = false;

    public Collection(Node collection, String target, String method) {
        this.collection = collection;
        this.target = target;
        this.method = method;
    }

    public void validate() {
        Document doc;
        String response;
        String domain = UriParser.getDomainIdFromUri(this.target);
        String user = UriParser.getUserIdFromUri(this.target);
        if (domain == null) {
            LOG.error("Target for collection not as expected, domain missing - "+this.target);
            this.valid = false;
            return;
        }
        if (user == null) {
            LOG.error("Target for collection not as expected, user missing - "+this.target);
            this.valid = false;
            return;
        }
        if (this.method.equals("add")) {
            this.name = this.collection.selectSingleNode("@name") == null ? null : this.collection.selectSingleNode("@name").getText();
            this.title = this.collection.selectSingleNode("title") == null ? "" : this.collection.selectSingleNode("title").getText();
            String string = this.description = this.collection.selectSingleNode("description") == null ? "" : this.collection.selectSingleNode("description").getText();
            if (this.name == null) {
                LOG.error("No name defined for collection - "+this.target);
                this.valid = false;
                return;
            }
        }
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create collection as no parent user was created in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request");
                this.valid = false;
                return;
            }
            this.target = this.target + "/collection/" + this.name;
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create collection that's already in FS - "+this.target+"/collection/"+this.name);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request - "+this.target);
                this.valid = false;
                return;
            }
            ActionFS.instance().addToFS(this.target);
        } else if (this.method.equals("delete")) {
        	//TODO: check if we need also to add a name for the collection?
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot delete collection that is not in FS - "+this.target);
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request - "+this.target);
                this.valid = false;
                return;
            }
            ActionFS.instance().deleteFromFS(this.target);
        }
        if (this.method.equals("add")) {
            List<Node> childs = this.collection.selectNodes("*");
            for (Node child : childs) {
                if (child.getName().equals("presentation")) {
                    Presentation presentation = new Presentation(child, this.target, this.method);
                    presentation.validate();
                    if (presentation.isValid()) continue;
                    LOG.error("Presentation not valid");
                    this.valid = false;
                    return;
                }
                if (child.getName().equals("title") || child.getName().equals("description")) continue;
                LOG.error("Found child " + child.getName() + " in collection node, this is not allowed");
                this.valid = false;
                return;
            }
        }
        this.valid = true;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void process() {
        if (this.method.equals("add")) {
            String fsxml = "<fsxml><properties><title></title>" + this.title + "<description>" + this.description + "</description></properties></fsxml>";
            String uri = this.target + "/properties";
            LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            List<Node> childs = this.collection.selectNodes("*");
            for (Node child : childs) {
                if (!child.getName().equals("presentation")) continue;
                Presentation presentation = new Presentation(child, this.target, this.method);
                presentation.validate();
                presentation.process();
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
}

