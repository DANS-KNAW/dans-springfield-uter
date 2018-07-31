/* 
* Domain.java
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
import org.springfield.uter.dans.xml.User;
import org.springfield.uter.homer.LazyHomer;

public class Domain {
    private static Logger LOG = Logger.getLogger(Domain.class);
    private Node domain;
    private String target = "";
    private String method = "";
    private String name = "";
    private boolean valid = false;

    public Domain(Node domain, String target, String method) {
        this.domain = domain;
        this.target = target;
        this.method = method;
    }

    public void validate() {
        String response;
        Document doc;
        if (!this.target.equals("")) {
            LOG.error("Target for domain should be empty");
            this.valid = false;
            return;
        }
        if (this.method.equals("add")) {
            String string = this.name = this.domain.selectSingleNode("@name") == null ? null : this.domain.selectSingleNode("@name").getText();
            if (this.name == null) {
                LOG.error((Object)"No name defined for domain");
                this.valid = false;
                return;
            }
            this.target = "/domain/" + this.name;
        }
        if (this.method.equals("add")) {
            response = LazyHomer.sendRequest("GET", this.target, "<fsxml><properties><depth>0</depth></properties></fsxml>", "");
            try {
                doc = DocumentHelper.parseText((String)response);
                if (doc.selectSingleNode("//error") == null || ActionFS.instance().isAddedToFS(this.target)) {
                    LOG.error("Cannot create domain that's already in FS");
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
                    LOG.error("Cannot delete domain that is not in FS");
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
            List<Node> childs = this.domain.selectNodes("*");
            for (Node child : childs) {
                if (child.getName().equals("user")) {
                    User user = new User(child, this.target, this.method);
                    user.validate();
                    if (user.isValid()) continue;
                    LOG.error("User not valid");
                    this.valid = false;
                    return;
                }
                LOG.error("Found child " + child.getName() + " in domain node, this is not allowed");
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
            List<Node> childs = this.domain.selectNodes("*");
            for (Node child : childs) {
                if (!child.getName().equals("user")) continue;
                User user = new User(child, this.target, this.method);
                user.validate();
                user.process();
            }
        }
        if (this.method.equals("delete")) {
            LazyHomer.sendRequest("DELETE", this.target, "", "");
        }
    }
}

