/* 
* Subtitle.java
* 
* Copyright (c) 2017 Noterik B.V.
* 
* This file is part of uter-dans, related to the Noterik Springfield project.
*
* uter-dans is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* uter-dans is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with uter-dans.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.springfield.uter.dans.xml;

/**
 * Subtitle.java
 *
 * @author Pieter van Leeuwen
 * @copyright Copyright: Noterik B.V. 2017
 * @package org.springfield.uter.dans.xml
 * 
 */
public class Subtitle {
    private String language;
    private String path;
    
    public Subtitle(String language, String path) {
	this.language = language;
	this.path = path;
    }
    
    public String getLanguage() {
	return language;
    }
    
    public String getPath() {
	return path;
    }
}
