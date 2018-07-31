/* 
* User.java
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
import org.springfield.uter.dans.xml.Collection;
import org.springfield.uter.dans.xml.UriParser;
import org.springfield.uter.homer.LazyHomer;

public class User {
    private static Logger LOG = Logger.getLogger(User.class);
    private Node user;
    private String target = "";
    private String method = "";
    private String name = "";
    private boolean valid = false;

    public User(Node user, String target, String method) {
        this.user = user;
        this.target = target;
        this.method = method;
    }

    public void validate() {
        String response;
        Document doc;
        String domain = UriParser.getDomainIdFromUri(this.target);
        if (domain == null) {
            LOG.error("Target for user not as expected");
            this.valid = false;
            return;
        }
        if (this.method.equals("add")) {
            String string = this.name = this.user.selectSingleNode("@name") == null ? null : this.user.selectSingleNode("@name").getText();
            if (this.name == null) {
                LOG.error("No name defined for user");
                this.valid = false;
                return;
            }
        } else if (this.method.equals("delete")) {
            this.name = UriParser.getUserIdFromUri(this.target);
        }
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") != null && !ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create user as no parent domain was created in FS");
                    this.valid = false;
                    return;
                }
            }
            catch (DocumentException e) {
                LOG.error("Could not parse response for FS request");
                this.valid = false;
                return;
            }
            this.target = this.target + "/user/" + this.name;
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create user that's already in FS");
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
                    LOG.error("Cannot delete user that is not in FS");
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
        if (this.method.equals("add")) {
            List<Node> childs = this.user.selectNodes("*");
            for (Node child : childs) {
                if (child.getName().equals("collection")) {
                    Collection collection = new Collection(child, this.target, this.method);
                    collection.validate();
                    if (collection.isValid()) continue;
                    LOG.error("Collection not valid");
                    this.valid = false;
                    return;
                }
                LOG.error("Found child " + child.getName() + " in user node, this is not allowed");
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
            String fsxml = "<fsxml><properties/></fsxml>";
            String uri = this.target + "/properties";
            LazyHomer.sendRequest("PUT", uri, fsxml, "text/xml");
            List<Node> childs = this.user.selectNodes("*");
            for (Node child : childs) {
                if (!child.getName().equals("collection")) continue;
                Collection collection = new Collection(child, this.target, this.method);
                collection.validate();
                collection.process();
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
}

