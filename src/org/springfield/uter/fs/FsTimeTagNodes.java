/* 
* FsTimeTageNodes.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FsTimeTagNodes {
	
	private Map<String, ArrayList<FsNode>> types = new HashMap<String, ArrayList<FsNode>>();
	private ArrayList<FsNode> all = new ArrayList<FsNode>();
	
	public void addNode(FsNode node) {
		ArrayList<FsNode> list = types.get(node.getName());
		//log.debug("LIST="+list+" N="+node.getName());
		if (list==null) {
			 list = new ArrayList<FsNode>();
			 types.put(node.getName(), list);
		}
		list.add(node);
		all.add(node);
	}
	
	public int size() {
		return all.size();
	}
	
	public ArrayList<FsNode> getAllNodes() {
		return all;
	}
	
	public int numberOfTypes() {
		return types.size();
	}
}
