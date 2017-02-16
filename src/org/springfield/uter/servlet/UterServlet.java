/* 
* UterServlet.java
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

package org.springfield.uter.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Document;
import org.dom4j.Node;
import org.springfield.uter.fs.Fs;
import org.springfield.uter.fs.FsNode;
import org.springfield.uter.homer.LazyHomer;
import org.xml.sax.InputSource;



/**
 * Servlet implementation class ServletResource
 * 
 * @author Daniel Ockeloen
 * @copyright Copyright: Noterik B.V. 2012
 * @package org.springfield.lou.servlet
 */
@WebServlet("/UterServlet")
public class UterServlet extends HttpServlet {
	
	private static final Logger logger = Logger.getLogger(UterServlet.class);
	private static final long serialVersionUID = 42L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public UterServlet() {
        super();
        System.out.println("Uter servlet object created");
        // TODO Auto-generated constructor stub
    }
    
  
    
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");  
		response.addHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
		response.addHeader("Access-Control-Allow-Headers", "Content-Type");
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");  
		response.addHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
		response.addHeader("Access-Control-Allow-Headers", "Content-Type");
		String body = request.getRequestURI();
		System.out.println("INCOMMING!! REQUEST IS="+body);
		if(request.getParameter("method")!=null) {
			if(request.getParameter("method").equals("post")){
				System.out.println("going for post");
				doPost(request, response);
				return;
			}
		}
		
		//String body = request.getRequestURI();
		//System.out.println("INCOMMING REQUEST IS="+body);
		
		
		response.setContentType("text/xml; charset=UTF-8");
		OutputStream out = response.getOutputStream();
		
		out.close();
		return;
	}
	
	/**
	 * Post request handles mainly external requests
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");  
		response.addHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
		response.addHeader("Access-Control-Allow-Headers", "Content-Type");
		
		String uri = request.getRequestURI();
		System.out.println("UTER INCOMMING REQUEST IS="+uri);
		//read post data
		StringBuffer jb = new StringBuffer();
		String line = null;
		try {
			BufferedReader reader = request.getReader();
			while ((line = reader.readLine()) != null)
				jb.append(line);
			reader.close();
		} catch (Exception e) { /*report an error*/ }
		
		String xml = jb.toString();
		
		System.out.println("PARAMS="+jb.toString());
		Document doc = null;
		try {
			doc = DocumentHelper.parseText(xml);
		} catch(DocumentException e) {
			System.out.println("XML parsing error: " + e);
		}
		
		String springfieldid=null,springfieldpath=null,language=null,title=null,content=null;
		if(doc!=null) {
			Node springfieldidNode = doc.selectSingleNode("//springfieldid");
			if(springfieldidNode!=null) {
				springfieldid = springfieldidNode.getText();
			}
			Node springfieldpathNode = doc.selectSingleNode("//springfieldpath");
			if(springfieldpathNode!=null) {
				springfieldpath = springfieldpathNode.getText();
			}
			Node languageNode = doc.selectSingleNode("//language");
			if(languageNode!=null) {
				language = languageNode.getText().toLowerCase();
			}
			Node titleNode = doc.selectSingleNode("//title");
			if(titleNode!=null) {
				title = titleNode.getText();
			}
			Node contentNode = doc.selectSingleNode("//content");
			if(contentNode!=null) {
				content = contentNode.getText();
			}
		}
		
		if(springfieldid==null || springfieldpath==null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing paramaters");
			return;
		}
		
		String langPrefix = "";
		if(language!=null) {
			langPrefix = language+"_";
		}
		
		String contentProperty = langPrefix + "content";
		if(springfieldid.indexOf("/")!=-1) {
			String[] tmp = springfieldid.split("/");
			contentProperty = langPrefix + tmp[1];
			springfieldid = tmp[0];
		}
		
		FsNode pnode = Fs.getNode(springfieldpath + "/" + springfieldid);
		pnode.setProperty(langPrefix + "title", title);
		pnode.setProperty(contentProperty, content);
		
		
		
		
		String body = "<fsxml><properties>";
	
		for(Iterator<String> iter = pnode.getKeys(); iter.hasNext(); ) {
			String key = iter.next();
			String value = pnode.getProperty(key);
			if (value.contains("&") || value.contains("<")) {
				body+="<"+key+"><![CDATA["+value+"]]></"+key+">\n";
			} else {
				body+="<"+key+">"+value+"</"+key+">\n";
			}
		}
		
		/*
		body += "<" + langPrefix + "title><![CDATA[" + title + "]]></" + langPrefix + "title>";
		body += "<" + contentProperty + "><![CDATA[" + content + "]]></" + contentProperty + ">";
		*/
		body += "</properties></fsxml>";
		
		System.out.println("Saving page: " + springfieldpath + "/" + springfieldid);
		System.out.println("PRoperties: " + body);
		String res = LazyHomer.sendRequest("PUT",springfieldpath + "/" + springfieldid + "/properties",body,"text/xml");
		PrintWriter out = response.getWriter();
		out.print(res);
		out.flush();
		out.close();
		
		return;
	}
}
