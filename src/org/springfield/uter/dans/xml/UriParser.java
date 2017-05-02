/* 
* UriParser.java
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

public class UriParser {
    private static String getTypeIdFromUri(String uri, String type) {
        String value = null;
        String typeUriPart = "/" + type + "/";
        int index1 = uri.indexOf(typeUriPart);
        if (index1 != -1) {
            int index2 = uri.indexOf("/", index1 += typeUriPart.length());
            value = index2 != -1 ? uri.substring(index1, index2) : uri.substring(index1);
        }
        return value;
    }

    public static String getDomainIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "domain");
    }

    public static String getUserIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "user");
    }

    public static String getPresentationIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "presentation");
    }

    public static String getCollectionIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "collection");
    }

    public static String getVideoIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "video");
    }
    
    public static String getAudioIdFromUri(String uri) {
        return UriParser.getTypeIdFromUri(uri, "audio");
    }

    public static String getParentUri(String uri) {
        String parentUri = "";
        uri = UriParser.removeFirstSlash(uri);
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 2; ++i) {
            parentUri = parentUri + "/" + parts[i];
        }
        return parentUri;
    }

    public static String removeFirstSlash(String uri) {
        if (uri.indexOf("/") == 0) {
            return uri.substring(1);
        }
        return uri;
    }

    public static String removeLastSlash(String uri) {
        if (uri.lastIndexOf("/") == uri.length() - 1) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }
}

