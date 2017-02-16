/* 
* MountProperties.java
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
 
package org.springfield.uter.homer;

public class MountProperties {
    private String hostname;
    private String path;
    private String account;
    private String password;
    private String protocol;

    public void setHostname(String h) {
        this.hostname = h;
    }

    public void setPath(String p) {
        this.path = p;
    }

    public void setAccount(String a) {
        this.account = a;
    }

    public void setPassword(String p) {
        this.password = p;
    }

    public void setProtocol(String p) {
        this.protocol = p;
    }

    public String getHostname() {
        return this.hostname;
    }

    public String getPath() {
        return this.path;
    }

    public String getAccount() {
        return this.account;
    }

    public String getPassword() {
        return this.password;
    }

    public String getProtocol() {
        return this.protocol;
    }
}

