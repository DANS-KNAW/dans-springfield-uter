/* 
* FSList.java
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

package org.springfield.uter.fs;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FSList {
	private static final Logger log = Logger.getLogger(FSList.class);

	private String path;
	private String id;
	private List<FsNode> nodes;
	private Map<String, String> properties = new HashMap<String, String>();
	

	public FSList(String uri) {
		path = uri;
	}
	
	public FSList(String uri,List<FsNode> list) {
		path = uri;
		nodes = list;
	}
	
	public void setPath(String p) {
		path = p;
	}
	
	public String getPath() {
		return path;
	}
	
	public void setId(String i) {
		id = i;
	}
	
	public String getId() {
		return id;
	}
	
	public int size() {
		if (nodes!=null) {
			return nodes.size();
		} else {
			return -1;
		}
	}
	
	public List<FsNode> getNodes() {
		return nodes;
	}
	
	public List<FsNode> getNodesByName(String name) {
		// create a sublist based on input
		List<FsNode> result = new ArrayList<FsNode>();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {
			FsNode n = (FsNode)iter.next();	
			if (n.getName().equals(name)) {
				result.add(n);
			}
		}
		return result;
	}
	
	// give a list of a type but filter on searchkey
	public List<FsNode> getNodesByName(String name,String searchlabel,String searchkey) {
		// create a sublist based on input
		List<FsNode> result = new ArrayList<FsNode>();
		for(Iterator<FsNode> iter = nodes.iterator() ; iter.hasNext(); ) {
			FsNode n = (FsNode)iter.next();	
			log.debug("NAME="+name);
			if (n.getName().equals(name)) {
				String field = n.getProperty(searchlabel);
				log.debug("F="+field+" K="+searchkey);
				if (field.indexOf(searchkey)!=-1) {
					result.add(n);
				}
			}
		}
		return result;
	}
		

}
