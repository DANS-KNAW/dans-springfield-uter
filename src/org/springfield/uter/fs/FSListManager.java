/* 
* FSListManager.java
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springfield.uter.homer.*;

import com.noterik.springfield.tools.HttpHelper;

public class FSListManager {
	private static Map<String, FSList> lists = new HashMap<String, FSList>();
	
	public static FSList get(String uri) {
		// see if we already have it loaded
		FSList list = lists.get(uri);
		if (list==null) {
			//
			List<FsNode> l=getNodes(uri,1);
			list = new FSList(uri,l);
			lists.put(uri, list);
		}
		return list;
	}
	
	
	public static List<FsNode> getNodes(String path,int depth) {
		List<FsNode> result = new ArrayList<FsNode>();
		String xml = "<fsxml><properties><depth>"+depth+"</depth></properties></fsxml>";
		
		String nodes = "";
		if (path.indexOf("http://")==-1) {
			nodes = LazyHomer.sendRequestBart("GET",path,xml,"text/xml");
		
			
		} else {
			nodes = HttpHelper.sendRequest("GET", path, "text/xml", "text/xml");
			path = path.substring(path.indexOf("/domain/"));
		}
 		try { 
			Document doc = DocumentHelper.parseText(nodes);
			if (isMainNode(path)) {
				for(Iterator<Node> iter = doc.getRootElement().nodeIterator(); iter.hasNext(); ) {
					Element node = (Element)iter.next();
					FsNode nn = new FsNode();
					if (!node.getName().equals("properties")) {
						nn.setName(node.getName());
						nn.setId(node.attribute("id").getText());
						nn.setPath(path+"/"+nn.getName()+"/"+nn.getId());
						if (node.attribute("referid")!=null) {
							nn.setReferid(node.attribute("referid").getText());
						}
						result.add(nn);
					}
				}
			} else {
				for(Iterator<Node> iter = doc.getRootElement().nodeIterator(); iter.hasNext(); ) {
					Element node = (Element)iter.next();
					for(Iterator<Node> iter2 = node.nodeIterator(); iter2.hasNext(); ) {
						Element node2 = (Element)iter2.next();
						FsNode nn = new FsNode();
						if (!node2.getName().equals("properties")) {
							//System.out.println("NAME2="+node2.getName());
							nn.setName(node2.getName());
							nn.setId(node2.attribute("id").getText());
							nn.setPath(path+"/"+nn.getName()+"/"+nn.getId());
							if (node.attribute("referid")!=null) {
								nn.setReferid(node.attribute("referid").getText());
							}
							result.add(nn);
							for(Iterator<Node> iter3 = node2.nodeIterator(); iter3.hasNext(); ) {
								Element p2 = (Element)iter3.next();
								if (p2.getName().equals("properties")) {
									for(Iterator<Node> iter4 = p2.nodeIterator(); iter4.hasNext(); ) {
										Object o  = iter4.next();
										if (o instanceof Element) {
											Element p3 = (Element)o;
											String pname = p3.getName();
											String pvalue = p3.getText();
											nn.setProperty(pname, pvalue);
										}
									}
								}
							}

						}
					}
				}
			}
 		} catch(Exception e) {
 			e.printStackTrace();
 		}
		return result;
	}
	
	public static boolean isMainNode(String path) {
		int r = path.split("/").length;
		if  ( r % 2 == 0 ) return true;
		return false;
	}
	
}
