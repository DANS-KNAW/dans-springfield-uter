/* 
* ActionFS.java
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

import java.util.LinkedList;
import org.springfield.uter.dans.xml.FSActionPair;

public class ActionFS {
    private LinkedList<FSActionPair> actions = new LinkedList();
    private static ActionFS instance;

    public static ActionFS instance() {
        if (instance == null) {
            instance = new ActionFS();
        }
        return instance;
    }

    public void addToFS(String uri) {
        this.actions.add(new FSActionPair("add", uri));
    }

    public void deleteFromFS(String uri) {
        this.actions.add(new FSActionPair("delete", uri));
    }

    public boolean isAddedToFS(String uri) {
        boolean added = false;
        for (FSActionPair action : this.actions) {
            if (action.contains("add", uri)) {
                added = true;
            }
            if (!action.contains("delete", uri)) continue;
            added = false;
        }
        return added;
    }

    public boolean isDeletedFromFS(String uri) {
        boolean removed = false;
        for (FSActionPair action : this.actions) {
            if (action.contains("delete", uri)) {
                removed = true;
            }
            if (!action.contains("add", uri)) continue;
            removed = false;
        }
        return removed;
    }

    public void clear() {
        this.actions = new LinkedList();
    }
}

