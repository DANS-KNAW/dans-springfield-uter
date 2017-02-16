/* 
* Action.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Node;
import org.springfield.uter.dans.xml.Collection;
import org.springfield.uter.dans.xml.Domain;
import org.springfield.uter.dans.xml.Presentation;
import org.springfield.uter.dans.xml.UriParser;
import org.springfield.uter.dans.xml.User;

public class Action {
    private static Logger LOG = Logger.getLogger(Action.class);
    private String type = "";
    private String target = "";
    private ArrayList<Object> actionList;

    public Action(Node action) {
        LOG.setLevel(Level.DEBUG);
        this.actionList = new ArrayList();
        this.type = action.getName();
        String string = this.target = action.selectSingleNode("@target") == null ? "" : action.selectSingleNode("@target").getText();
        if (this.target != "" && !this.target.startsWith("/")) {
            this.target = "/" + this.target;
        }
        if (this.type.toLowerCase().equals("add")) {
            this.handleAdd(action);
        } else if (this.type.toLowerCase().equals("remove")) {
            this.handleRemove(action);
        }
    }

    private void handleAdd(Node action) {
        LOG.debug("Add action - target "+this.target);
        List<Node> childs = action.selectNodes("*");
        for (Node child : childs) {
            String name = child.getName();
            LOG.debug("node name = " + name);
            if (name.toLowerCase().equals("domain")) {
                this.actionList.add(new Domain(child, this.target, "add"));
                continue;
            }
            if (name.toLowerCase().equals("user")) {
                this.actionList.add(new User(child, this.target, "add"));
                continue;
            }
            if (name.toLowerCase().equals("collection")) {
                this.actionList.add(new Collection(child, this.target, "add"));
                continue;
            }
            if (!name.toLowerCase().equals("presentation")) continue;
            this.actionList.add(new Presentation(child, this.target, "add"));
        }
    }

    private void handleRemove(Node action) {
        LOG.debug("Remove action - target "+this.target);
        String domain = UriParser.getDomainIdFromUri(this.target);
        String user = UriParser.getUserIdFromUri(this.target);
        String collection = UriParser.getCollectionIdFromUri(this.target);
        String presentation = UriParser.getPresentationIdFromUri(this.target);
        if (user == null) {
            this.actionList.add(new Domain(null, this.target, "delete"));
        } else if (collection == null) {
            this.actionList.add(new User(null, this.target, "delete"));
        } else if (presentation == null) {
            this.actionList.add(new Collection(null, this.target, "delete"));
        } else {
            this.actionList.add(new Presentation(null, this.target, "delete"));
        }
    }

    public boolean isValid() {
        for (Object o : this.actionList) {
            boolean valid;
            if (o instanceof Domain) {
                Domain d = (Domain)o;
                d.validate();
                valid = d.isValid();
                LOG.debug("Domain is valid = " + valid);
                if (valid) continue;
                return false;
            }
            if (o instanceof User) {
                User u = (User)o;
                u.validate();
                valid = u.isValid();
                LOG.debug("User is valid = " + valid);
                if (valid) continue;
                return false;
            }
            if (o instanceof Collection) {
                Collection c = (Collection)o;
                c.validate();
                valid = c.isValid();
                LOG.debug("Collection is valid = " + valid);
                if (valid) continue;
                return false;
            }
            if (!(o instanceof Presentation)) continue;
            Presentation p = (Presentation)o;
            p.validate();
            valid = p.isValid();
            LOG.debug("Presentation is valid = " + valid);
            if (valid) continue;
            return false;
        }
        return true;
    }

    public void process() {
        for (Object o : this.actionList) {
            if (o instanceof Domain) {
                Domain d = (Domain)o;
                d.process();
                continue;
            }
            if (o instanceof User) {
                User u = (User)o;
                u.process();
                continue;
            }
            if (o instanceof Collection) {
                Collection c = (Collection)o;
                c.process();
                continue;
            }
            if (!(o instanceof Presentation)) continue;
            Presentation p = (Presentation)o;
            p.process();
        }
    }

    public HashMap<String, LinkedList<String>> getFSUris() {
        LinkedList addList = new LinkedList();
        LinkedList deleteList = new LinkedList();
        HashMap<String, LinkedList<String>> queues = new HashMap<String, LinkedList<String>>();
        queues.put("add", addList);
        queues.put("delete", deleteList);
        return queues;
    }
}

