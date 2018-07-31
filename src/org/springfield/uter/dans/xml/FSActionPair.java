/* 
* FsActionPair.java
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

public class FSActionPair {
    private String method;
    private String uri;

    public FSActionPair(String method, String uri) {
        this.method = method;
        this.uri = uri;
    }

    public boolean contains(String method, String uri) {
        if (this.method.equals(method) && this.uri.equals(uri)) {
            return true;
        }
        return false;
    }

    @Override public String toString() {
        return "FSActionPair[method = " + method + ", uri = " + uri + "]";
    }
}

